package main

// The status in Discord, as Spotify's: "Listening to YouCloud" — track, artist, cover and the time
// bar. The helper talks to the Discord app running on this computer over its local channel
// (discord-ipc-N) with the Rich Presence protocol; nothing goes to the internet from here, Discord
// publishes the status itself. The name "YouCloud" and its icon come from the application on
// discord.com/developers — its id below is public, as any program's with a status is. Masaki
// (src/main/discord.ts there) does the same for its own listeners.

import (
	"crypto/rand"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"io"
	"log"
	"math"
	"net"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"
	"unicode/utf8"
)

const (
	site = "https://youcloud.inkerror67.workers.dev/"
	// YouCloud's icon as PNG (the site has only SVG, and Discord takes PNG or JPG over https).
	icon = "https://gxuser7.github.io/Masaki/youcloud.png"
)

func discordAppID() string {
	if id := os.Getenv("YOUCLOUD_DISCORD_APP_ID"); id != "" {
		return id
	}
	return "1558936902051307630"
}

// Track is what plays, as the site tells it. Times in seconds; position as of when it was told.
type Track struct {
	Title    string   `json:"title"`
	Artist   string   `json:"artist"`
	Album    string   `json:"album"`
	Artwork  string   `json:"artwork"`
	Playing  bool     `json:"playing"`
	Duration *float64 `json:"duration"`
	Position *float64 `json:"position"`
}

var (
	dMu        sync.Mutex
	track      *Track
	conn       net.Conn
	ready      bool
	connecting bool
	retry      *time.Timer
	debounce   *time.Timer
	// What Discord shows now, so the same isn't sent again (it lets through 5 updates in 20 s).
	shown string
)

func clip(s string, n int) string {
	s = strings.TrimSpace(s)
	if utf8.RuneCountInString(s) <= n {
		return s
	}
	return string([]rune(s)[:n])
}

func finite(p *float64) *float64 {
	if p == nil || math.IsNaN(*p) || math.IsInf(*p, 0) || *p < 0 {
		return nil
	}
	return p
}

// setPresence takes what plays (nil: nothing). Updates are smoothed: a change of track comes as
// a burst of them.
func setPresence(t *Track) {
	if t != nil {
		t = &Track{
			Title: clip(t.Title, 300), Artist: clip(t.Artist, 300), Album: clip(t.Album, 300),
			Artwork: clip(t.Artwork, 4000), Playing: t.Playing,
			Duration: finite(t.Duration), Position: finite(t.Position),
		}
		if t.Title == "" {
			t = nil
		}
	}
	dMu.Lock()
	defer dMu.Unlock()
	track = t
	if debounce != nil {
		debounce.Stop()
	}
	debounce = time.AfterFunc(1500*time.Millisecond, func() {
		dMu.Lock()
		playing := track != nil && track.Playing
		dMu.Unlock()
		if playing {
			go connect()
		}
		push()
	})
}

// clearPresence: the browser let the helper go — the status goes too, and Discord forgets it.
func clearPresence() {
	dMu.Lock()
	track = nil
	c := conn
	dMu.Unlock()
	push()
	if c != nil {
		time.Sleep(200 * time.Millisecond)
		c.Close()
	}
}

func discordPipes() []string {
	if p := os.Getenv("YOUCLOUD_DISCORD_PIPE"); p != "" {
		return []string{p}
	}
	return ipcPaths()
}

func frame(op uint32, data any) []byte {
	body, _ := json.Marshal(data)
	buf := make([]byte, 8, 8+len(body))
	binary.LittleEndian.PutUint32(buf[0:], op)
	binary.LittleEndian.PutUint32(buf[4:], uint32(len(body)))
	return append(buf, body...)
}

func connect() {
	dMu.Lock()
	if conn != nil || connecting {
		dMu.Unlock()
		return
	}
	connecting = true
	dMu.Unlock()
	defer func() {
		dMu.Lock()
		connecting = false
		dMu.Unlock()
	}()
	for _, p := range discordPipes() {
		c, err := dialIPC(p)
		if err != nil {
			continue
		}
		dMu.Lock()
		conn = c
		dMu.Unlock()
		go readLoop(c)
		if _, err := c.Write(frame(0, map[string]any{"v": 1, "client_id": discordAppID()})); err != nil {
			c.Close()
		}
		return
	}
	later()
}

func readLoop(c net.Conn) {
	head := make([]byte, 8)
	for {
		if _, err := io.ReadFull(c, head); err != nil {
			break
		}
		op := binary.LittleEndian.Uint32(head[0:])
		n := binary.LittleEndian.Uint32(head[4:])
		if n > 1<<20 {
			break
		}
		body := make([]byte, n)
		if _, err := io.ReadFull(c, body); err != nil {
			break
		}
		var msg struct {
			Evt  string `json:"evt"`
			Cmd  string `json:"cmd"`
			Data any    `json:"data"`
		}
		_ = json.Unmarshal(body, &msg)
		switch {
		case op == 3: // ping → pong
			dMu.Lock()
			_, _ = c.Write(frame(4, json.RawMessage(body)))
			dMu.Unlock()
		case op == 1 && msg.Evt == "READY":
			dMu.Lock()
			ready = true
			shown = ""
			dMu.Unlock()
			push()
		case op == 1 && msg.Evt == "ERROR":
			log.Printf("discord: %s", body)
		case op == 2: // Discord closes: a wrong application id, for one
			log.Printf("discord closed: %s", body)
		}
	}
	c.Close()
	dMu.Lock()
	if conn == c {
		conn = nil
		ready = false
		shown = ""
	}
	dMu.Unlock()
	later()
}

// later: Discord isn't running — try again every 20 s while there is something to show.
func later() {
	dMu.Lock()
	defer dMu.Unlock()
	if retry != nil {
		retry.Stop()
	}
	if track != nil && track.Playing {
		retry = time.AfterFunc(20*time.Second, connect)
	}
}

// fit: Discord wants 2–128 characters in each line of the status.
func fit(s string, max int) string {
	s = clip(s, max)
	for utf8.RuneCountInString(s) < 2 {
		s += " "
	}
	return s
}

var https = regexp.MustCompile(`(?i)^https://`)

func activity(t *Track) map[string]any {
	cover := ""
	if https.MatchString(t.Artwork) && len(t.Artwork) <= 300 {
		cover = t.Artwork
	}
	state := t.Artist
	if state == "" {
		state = t.Album
	}
	if state == "" {
		state = "YouCloud"
	}
	largeText := t.Album
	if largeText == "" {
		largeText = t.Title
	}
	assets := map[string]any{"large_image": icon, "large_text": fit(largeText, 128)}
	if cover != "" {
		assets["large_image"] = cover
		assets["small_image"] = icon
		assets["small_text"] = "YouCloud"
	}
	a := map[string]any{
		"type":    2, // "Listening to"
		"details": fit(t.Title, 128),
		"state":   fit(state, 128),
		"assets":  assets,
		"buttons": []map[string]string{{"label": "Открыть YouCloud", "url": site}},
	}
	if t.Duration != nil && *t.Duration > 0 && t.Position != nil {
		// The time bar: Discord counts it itself from start to end.
		start := time.Now().UnixMilli() - int64(*t.Position*1000)
		a["timestamps"] = map[string]int64{"start": start, "end": start + int64(*t.Duration*1000)}
	}
	return a
}

func push() {
	dMu.Lock()
	defer dMu.Unlock()
	if !ready || conn == nil {
		return
	}
	var a map[string]any
	key := ""
	if track != nil && track.Playing {
		a = activity(track)
		// A bar a couple of seconds off is no reason for a new status; a jump within the track is.
		start := int64(0)
		if ts, ok := a["timestamps"].(map[string]int64); ok {
			start = ts["start"]
		}
		rest := map[string]any{}
		for k, v := range a {
			if k != "timestamps" {
				rest[k] = v
			}
		}
		body, _ := json.Marshal(rest)
		key = string(body) + "|" + strconv.FormatInt(int64(math.Round(float64(start)/5000)), 10)
	}
	if key == shown {
		return
	}
	shown = key
	args := map[string]any{"pid": os.Getpid()}
	if a != nil {
		args["activity"] = a
	}
	nonce := make([]byte, 16)
	_, _ = rand.Read(nonce)
	_, _ = conn.Write(frame(1, map[string]any{"cmd": "SET_ACTIVITY", "args": args, "nonce": hex.EncodeToString(nonce)}))
}
