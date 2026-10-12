package main

// yt-dlp needs a JavaScript runtime for YouTube: the player's challenges are code that has to run.
// Masaki lends it its own Electron as Node; the helper has none of its own, so it takes Deno or
// Node the listener already has (new enough for yt-dlp), and otherwise fetches Deno once — from
// Deno's own releases, checked against the checksum published beside it.

import (
	"archive/zip"
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
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

const denoRelease = "https://github.com/denoland/deno/releases/latest/download/"

var (
	jsMu    sync.Mutex
	jsFound string
)

func denoDir() string { return filepath.Join(home(), "deno") }

func denoExe() string {
	if runtime.GOOS == "windows" {
		return "deno.exe"
	}
	return "deno"
}

// jsRuntime is what goes after --js-runtimes: "deno:<path>" or "node:<path>".
func jsRuntime() (string, error) {
	if r := os.Getenv("YOUCLOUD_JS_RUNTIME"); r != "" {
		return r, nil
	}
	jsMu.Lock()
	defer jsMu.Unlock()
	if jsFound != "" {
		return jsFound, nil
	}
	own := filepath.Join(denoDir(), denoExe())
	switch {
	case exists(own):
		jsFound = "deno:" + own
	case lookup("deno", `^deno (\d+)\.`, 2) != "":
		jsFound = "deno:" + lookup("deno", `^deno (\d+)\.`, 2)
	case lookup("node", `^v(\d+)\.`, 20) != "":
		jsFound = "node:" + lookup("node", `^v(\d+)\.`, 20)
	default:
		if err := fetchDeno(); err != nil {
			return "", fmt.Errorf("не удалось скачать Deno для yt-dlp: %w", err)
		}
		jsFound = "deno:" + own
	}
	return jsFound, nil
}

// lookup finds [name] on PATH whose --version has at least [min] as its major version.
func lookup(name, pattern string, min int) string {
	path, err := exec.LookPath(name)
	if err != nil {
		return ""
	}
	cmd := exec.Command(path, "--version")
	hideWindow(cmd)
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	m := regexp.MustCompile(pattern).FindStringSubmatch(strings.TrimSpace(string(out)))
	if m == nil {
		return ""
	}
	if major, _ := strconv.Atoi(m[1]); major < min {
		return ""
	}
	return path
}

func denoAsset() string {
	arch := "x86_64"
	if runtime.GOARCH == "arm64" {
		arch = "aarch64"
	}
	switch runtime.GOOS {
	case "darwin":
		return "deno-" + arch + "-apple-darwin.zip"
	case "linux":
		return "deno-" + arch + "-unknown-linux-gnu.zip"
	default:
		// Windows on ARM runs the x86-64 build.
		return "deno-x86_64-pc-windows-msvc.zip"
	}
}

var hex64 = regexp.MustCompile(`(?i)\b[0-9a-f]{64}\b`)

func fetchDeno() error {
	asset := denoAsset()
	sum, err := fetch(denoRelease+asset+".sha256sum", 30*time.Second)
	if err != nil {
		return err
	}
	want := strings.ToLower(hex64.FindString(string(sum)))
	if want == "" {
		return fmt.Errorf("нет контрольной суммы %s", asset)
	}
	body, err := fetch(denoRelease+asset, 10*time.Minute)
	if err != nil {
		return err
	}
	if got := sha256.Sum256(body); hex.EncodeToString(got[:]) != want {
		return fmt.Errorf("%s не совпал с контрольной суммой", asset)
	}
	archive, err := zip.NewReader(bytes.NewReader(body), int64(len(body)))
	if err != nil {
		return err
	}
	for _, f := range archive.File {
		if f.Name != denoExe() {
			continue
		}
		r, err := f.Open()
		if err != nil {
			return err
		}
		defer r.Close()
		if err := os.MkdirAll(denoDir(), 0o755); err != nil {
			return err
		}
		target := filepath.Join(denoDir(), denoExe())
		part := target + ".part"
		w, err := os.OpenFile(part, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o755)
		if err != nil {
			return err
		}
		if _, err := io.Copy(w, r); err != nil {
			w.Close()
			return err
		}
		if err := w.Close(); err != nil {
			return err
		}
		return os.Rename(part, target)
	}
	return fmt.Errorf("в %s нет %s", asset, denoExe())
}
