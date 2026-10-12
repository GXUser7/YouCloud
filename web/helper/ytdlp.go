package main

// YouTube's sound with yt-dlp, as the Android app and Masaki get it. The site in a browser repeats
// only part of yt-dlp's logic, and YouTube refuses it many tracks ("confirm you're not a bot", a
// stream without a PO token cut off at its first megabyte); yt-dlp asks YouTube the right way and
// catches up with its changes within days.
//
// yt-dlp is fetched when first needed, from the nightly builds (the ones yt-dlp itself advises
// for YouTube), checked against the SHA2-256SUMS of the same release, and checked for an update
// once a day and after a failure. It runs signed out first (fast, and as everyone does); refused,
// it runs again with the browser's youtube.com sign-in, from a file that lives only while it runs.

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"log"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"time"
)

const (
	ytdlpRelease = "https://github.com/yt-dlp/yt-dlp-nightly-builds/releases/latest/download/"
	day          = 24 * time.Hour
	runTimeout   = 60 * time.Second
)

// The build of yt-dlp for this system, as the release names it.
func ytdlpAsset() string {
	switch runtime.GOOS {
	case "darwin":
		return "yt-dlp_macos"
	case "linux":
		if runtime.GOARCH == "arm64" {
			return "yt-dlp_linux_aarch64"
		}
		return "yt-dlp_linux"
	default:
		return "yt-dlp.exe"
	}
}

func ytdlpDir() string { return filepath.Join(home(), "yt-dlp") }

func ytdlpPath() string {
	// Tests put a stand-in of their own here.
	if p := os.Getenv("YOUCLOUD_YTDLP"); p != "" {
		return p
	}
	if runtime.GOOS == "windows" {
		return filepath.Join(ytdlpDir(), "yt-dlp.exe")
	}
	return filepath.Join(ytdlpDir(), "yt-dlp")
}

type ytdlpState struct {
	CheckedAt int64  `json:"checkedAt"`
	SHA256    string `json:"sha256"`
}

func stateFile() string { return filepath.Join(ytdlpDir(), "state.json") }

func readState() ytdlpState {
	var s ytdlpState
	if data, err := os.ReadFile(stateFile()); err == nil {
		_ = json.Unmarshal(data, &s)
	}
	return s
}

func writeState(s ytdlpState) {
	data, _ := json.Marshal(s)
	_ = os.WriteFile(stateFile(), data, 0o644)
}

var sumLine = regexp.MustCompile(`^([0-9a-f]{64})\s+\*?(\S+)$`)

// updateYtdlp fetches the newest yt-dlp unless the one here already is it.
func updateYtdlp() error {
	if os.Getenv("YOUCLOUD_YTDLP") != "" {
		return nil
	}
	if err := os.MkdirAll(ytdlpDir(), 0o755); err != nil {
		return err
	}
	sums, err := fetch(ytdlpRelease+"SHA2-256SUMS", 30*time.Second)
	if err != nil {
		return err
	}
	asset := ytdlpAsset()
	want := ""
	for _, line := range strings.Split(string(sums), "\n") {
		if m := sumLine.FindStringSubmatch(strings.TrimSpace(line)); m != nil && m[2] == asset {
			want = m[1]
		}
	}
	if want == "" {
		return fmt.Errorf("в SHA2-256SUMS нет %s", asset)
	}
	have := readState()
	if have.SHA256 == want && exists(ytdlpPath()) {
		have.CheckedAt = time.Now().UnixMilli()
		writeState(have)
		return nil
	}
	body, err := fetch(ytdlpRelease+asset, 5*time.Minute)
	if err != nil {
		return err
	}
	if got := sha256.Sum256(body); hex.EncodeToString(got[:]) != want {
		return fmt.Errorf("%s не совпал с контрольной суммой", asset)
	}
	part := ytdlpPath() + ".part"
	if err := os.WriteFile(part, body, 0o755); err != nil {
		return err
	}
	// While the old yt-dlp runs, Windows won't let it be replaced: then next time.
	if err := os.Rename(part, ytdlpPath()); err != nil {
		_ = os.Remove(part)
		return err
	}
	writeState(ytdlpState{CheckedAt: time.Now().UnixMilli(), SHA256: want})
	log.Printf("yt-dlp updated: %s", want[:12])
	return nil
}

var (
	updateMu  sync.Mutex
	updateRun *flight
)

// flight is one run of something many may wait for at once.
type flight struct {
	done chan struct{}
	err  error
}

func updateOnce() error {
	updateMu.Lock()
	f := updateRun
	if f == nil {
		f = &flight{done: make(chan struct{})}
		updateRun = f
		go func() {
			f.err = updateYtdlp()
			updateMu.Lock()
			updateRun = nil
			updateMu.Unlock()
			close(f.done)
		}()
	}
	updateMu.Unlock()
	<-f.done
	return f.err
}

// ensureYtdlp: the first time it waits for the download; after that the update check runs aside.
func ensureYtdlp() (string, error) {
	if !exists(ytdlpPath()) {
		if err := updateOnce(); err != nil {
			return "", err
		}
	} else if time.Since(time.UnixMilli(readState().CheckedAt)) > day {
		go func() { _ = updateOnce() }()
	}
	return ytdlpPath(), nil
}

// prepare gets yt-dlp and a JavaScript runtime ready ahead of the first track.
func prepare() {
	if _, err := ensureYtdlp(); err != nil {
		log.Printf("yt-dlp: %v", err)
	}
	if _, err := jsRuntime(); err != nil {
		log.Printf("js runtime: %v", err)
	}
}

// A cookie of youtube.com, as the extension reads it from the browser.
type cookie struct {
	Domain         string  `json:"domain"`
	HostOnly       bool    `json:"hostOnly"`
	Path           string  `json:"path"`
	Secure         bool    `json:"secure"`
	HTTPOnly       bool    `json:"httpOnly"`
	ExpirationDate float64 `json:"expirationDate"`
	Name           string  `json:"name"`
	Value          string  `json:"value"`
}

var youtubeDomain = regexp.MustCompile(`(^|\.)youtube\.com$`)

// netscapeCookies: the browser's youtube.com sign-in in the format yt-dlp reads; "" when it has none.
func netscapeCookies(list []cookie) string {
	signedIn := false
	lines := []string{"# Netscape HTTP Cookie File"}
	for _, c := range list {
		domain := strings.TrimPrefix(c.Domain, ".")
		if !youtubeDomain.MatchString(domain) || strings.ContainsAny(c.Name+c.Value+c.Path, "\t\r\n") {
			continue
		}
		if c.Name == "SAPISID" || c.Name == "__Secure-3PAPISID" {
			signedIn = true
		}
		sub := "FALSE"
		if !c.HostOnly {
			domain, sub = "."+domain, "TRUE"
		}
		if c.HTTPOnly {
			domain = "#HttpOnly_" + domain
		}
		path := c.Path
		if path == "" {
			path = "/"
		}
		lines = append(lines, strings.Join([]string{domain, sub, path, upper(c.Secure), strconv.FormatInt(int64(c.ExpirationDate), 10), c.Name, c.Value}, "\t"))
	}
	if !signedIn {
		return ""
	}
	return strings.Join(lines, "\n") + "\n"
}

func upper(b bool) string {
	if b {
		return "TRUE"
	}
	return "FALSE"
}

// Found is the sound of a video: a link <audio> plays, good until expire (ms since the epoch).
type Found struct {
	URL    string `json:"url"`
	Expire int64  `json:"expire"`
}

var errorPrefix = regexp.MustCompile(`^ERROR:\s*(\[youtube\]\s*[\w-]+:\s*)?`)

func run(exe, videoID, cookies string) (Found, error) {
	js, err := jsRuntime()
	if err != nil {
		return Found{}, err
	}
	args := []string{
		"--ignore-config", "--no-playlist", "--dump-json", "--no-warnings",
		"--socket-timeout", "15",
		// Only files over HTTPS: <audio> plays them itself, HLS and DASH manifests aren't needed.
		"-f", "bestaudio[protocol=https]/bestaudio",
		"--extractor-args", "youtube:skip=hls,dash",
		"--js-runtimes", js,
		"--cache-dir", filepath.Join(ytdlpDir(), "cache"),
	}
	if cookies != "" {
		args = append(args, "--cookies", cookies)
	}
	args = append(args, "--", "https://www.youtube.com/watch?v="+videoID)

	ctx, cancel := context.WithTimeout(context.Background(), runTimeout)
	defer cancel()
	cmd := exec.CommandContext(ctx, exe, args...)
	hideWindow(cmd)
	var stdout, stderr strings.Builder
	cmd.Stdout, cmd.Stderr = &stdout, &stderr
	runErr := cmd.Run()

	var j struct {
		URL string `json:"url"`
	}
	if json.Unmarshal([]byte(stdout.String()), &j) == nil && j.URL != "" {
		expire := time.Now().Add(time.Hour).UnixMilli()
		if u, err := url.Parse(j.URL); err == nil {
			if s, err := strconv.ParseInt(u.Query().Get("expire"), 10, 64); err == nil && s > 0 {
				expire = s * 1000
			}
		}
		return Found{URL: j.URL, Expire: expire}, nil
	}
	// "ERROR: [youtube] id: Sign in to confirm…" — the reason is the last ERROR line.
	reason := ""
	lines := strings.Split(stderr.String(), "\n")
	for i := len(lines) - 1; i >= 0; i-- {
		if strings.HasPrefix(lines[i], "ERROR") {
			reason = errorPrefix.ReplaceAllString(strings.TrimSpace(lines[i]), "")
			break
		}
	}
	if reason == "" {
		switch {
		case ctx.Err() != nil:
			reason = "yt-dlp не ответил за минуту"
		case runErr != nil:
			reason = "yt-dlp: " + runErr.Error()
		default:
			reason = "yt-dlp ответил без ссылки"
		}
	}
	if len(reason) > 300 {
		reason = reason[:300]
	}
	return Found{}, errors.New(reason)
}

// runWith: signed out first; refused, again with the browser's sign-in, if it has one.
func runWith(exe, videoID string, cookies []cookie) (Found, error) {
	found, anonymous := run(exe, videoID, "")
	if anonymous == nil {
		return found, nil
	}
	text := netscapeCookies(cookies)
	if text == "" {
		return Found{}, anonymous
	}
	tmp := filepath.Join(ytdlpDir(), "tmp")
	if err := os.MkdirAll(tmp, 0o700); err != nil {
		return Found{}, anonymous
	}
	name := make([]byte, 8)
	_, _ = rand.Read(name)
	file := filepath.Join(tmp, "cookies-"+hex.EncodeToString(name)+".txt")
	if err := os.WriteFile(file, []byte(text), 0o600); err != nil {
		return Found{}, anonymous
	}
	defer os.Remove(file)
	return run(exe, videoID, file)
}

var (
	videoIDPattern = regexp.MustCompile(`^[\w-]{11}$`)
	foundMu        sync.Mutex
	foundLinks     = map[string]Found{}
	inflight       = map[string]*resolveFlight{}
	lastForced     time.Time
)

type resolveFlight struct {
	done  chan struct{}
	found Found
	err   error
}

// resolve: a link kept while it has time left; otherwise a fresh one from yt-dlp. fresh: the kept
// one wouldn't play, ask anew.
func resolve(videoID string, fresh bool, cookies []cookie) (Found, error) {
	if !videoIDPattern.MatchString(videoID) {
		return Found{}, errNoID
	}
	foundMu.Lock()
	if fresh {
		delete(foundLinks, videoID)
	}
	if kept, ok := foundLinks[videoID]; ok && time.Until(time.UnixMilli(kept.Expire)) > 10*time.Minute {
		foundMu.Unlock()
		return kept, nil
	}
	f := inflight[videoID]
	if f == nil {
		f = &resolveFlight{done: make(chan struct{})}
		inflight[videoID] = f
		go func() {
			f.found, f.err = resolveNow(videoID, cookies)
			foundMu.Lock()
			if f.err == nil {
				foundLinks[videoID] = f.found
			}
			delete(inflight, videoID)
			foundMu.Unlock()
			close(f.done)
		}()
	}
	foundMu.Unlock()
	<-f.done
	return f.found, f.err
}

func resolveNow(videoID string, cookies []cookie) (Found, error) {
	exe, err := ensureYtdlp()
	if err != nil {
		return Found{}, fmt.Errorf("не удалось скачать yt-dlp: %w", err)
	}
	found, err := runWith(exe, videoID, cookies)
	if err == nil {
		return found, nil
	}
	// YouTube may have changed: yt-dlp updates and tries once more (at most once an hour).
	foundMu.Lock()
	recent := time.Since(lastForced) < time.Hour
	if !recent {
		lastForced = time.Now()
	}
	foundMu.Unlock()
	if recent {
		return Found{}, err
	}
	before := readState().SHA256
	_ = updateOnce()
	if readState().SHA256 == before {
		return Found{}, err
	}
	return runWith(exe, videoID, cookies)
}
