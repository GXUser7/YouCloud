package main

import (
	"bufio"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"sync"
)

// What the extension asks. id 0 is a message that wants no answer (the status in Discord).
type request struct {
	ID      int64    `json:"id"`
	Type    string   `json:"type"`
	VideoID string   `json:"videoId"`
	Fresh   bool     `json:"fresh"`
	Live    bool     `json:"live"`
	Cookies []cookie `json:"cookies"`
	Track   *Track   `json:"track"`
}

type answer struct {
	ID    int64  `json:"id"`
	Value any    `json:"value,omitempty"`
	Error string `json:"error,omitempty"`
}

var (
	outMu sync.Mutex
	out   io.Writer = os.Stdout
)

// send writes one message the way the browser reads it: its length, in the machine's own byte
// order (little-endian on every machine the helper is built for), then the JSON.
func send(a answer) {
	data, err := json.Marshal(a)
	if err != nil {
		return
	}
	outMu.Lock()
	defer outMu.Unlock()
	_ = binary.Write(out, binary.LittleEndian, uint32(len(data)))
	_, _ = out.Write(data)
}

// serve answers the extension until the browser lets the helper go (stdin closes): then the
// status in Discord goes with it, as a closed browser plays nothing.
func serve() {
	startLog()
	log.Printf("started %s for %v", Version, os.Args[1:])
	in := bufio.NewReader(os.Stdin)
	for {
		var n uint32
		if err := binary.Read(in, binary.LittleEndian, &n); err != nil {
			break
		}
		if n > 64<<20 {
			log.Printf("message of %d bytes: too big", n)
			break
		}
		body := make([]byte, n)
		if _, err := io.ReadFull(in, body); err != nil {
			break
		}
		var req request
		if err := json.Unmarshal(body, &req); err != nil {
			log.Printf("unreadable message: %v", err)
			continue
		}
		go handle(req)
	}
	clearPresence()
	log.Printf("browser let go")
}

func handle(req request) {
	reply := func(value any, err error) {
		if req.ID == 0 {
			return
		}
		if err != nil {
			send(answer{ID: req.ID, Error: err.Error()})
		} else {
			send(answer{ID: req.ID, Value: value})
		}
	}
	switch req.Type {
	case "hello":
		// The site is open: yt-dlp (and a JavaScript runtime for it) get ready now, so the first
		// YouTube track doesn't wait for the download.
		go prepare()
		reply(map[string]any{"version": Version, "os": runtime.GOOS}, nil)
	case "yt-stream":
		found, err := resolve(req.VideoID, req.Fresh, req.Live, req.Cookies)
		if err != nil {
			log.Printf("yt-stream %s: %v", req.VideoID, err)
			reply(nil, err)
			return
		}
		reply(found, nil)
	case "presence":
		setPresence(req.Track)
		reply(true, nil)
	default:
		reply(nil, fmt.Errorf("unknown request: %q", req.Type))
	}
}

// startLog keeps a short log next to the helper, for when something has to be found out: what
// failed and why — never cookies or the links themselves.
func startLog() {
	dir := home()
	if err := os.MkdirAll(dir, 0o755); err != nil {
		log.SetOutput(io.Discard)
		return
	}
	path := filepath.Join(dir, "helper.log")
	if st, err := os.Stat(path); err == nil && st.Size() > 512<<10 {
		_ = os.Rename(path, path+".1")
	}
	f, err := os.OpenFile(path, os.O_CREATE|os.O_APPEND|os.O_WRONLY, 0o644)
	if err != nil {
		log.SetOutput(io.Discard)
		return
	}
	log.SetOutput(f)
}

var errNoID = errors.New("неверный id ролика")
