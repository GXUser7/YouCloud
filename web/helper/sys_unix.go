//go:build !windows

package main

import (
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"strconv"
	"strings"
	"time"
)

// Discord's socket lies in the temporary folder it was given, which the browser (and so the
// helper) was given too.
func ipcPaths() []string {
	var dirs []string
	seen := map[string]bool{}
	for _, d := range []string{os.Getenv("XDG_RUNTIME_DIR"), os.Getenv("TMPDIR"), os.Getenv("TMP"), os.Getenv("TEMP"), os.TempDir(), "/tmp"} {
		if d != "" && !seen[d] {
			seen[d] = true
			dirs = append(dirs, d)
		}
	}
	var paths []string
	for _, d := range dirs {
		for i := 0; i < 10; i++ {
			paths = append(paths, filepath.Join(d, "discord-ipc-"+strconv.Itoa(i)))
		}
	}
	return paths
}

func dialIPC(path string) (net.Conn, error) {
	return net.DialTimeout("unix", path, 2*time.Second)
}

func hideWindow(*exec.Cmd) {}

var russianLangs = []string{"ru", "uk", "be", "kk", "ky", "uz", "tg", "hy", "az", "ka"}

func systemRussian() bool {
	for _, v := range []string{os.Getenv("LC_ALL"), os.Getenv("LC_MESSAGES"), os.Getenv("LANG")} {
		if v == "" {
			continue
		}
		for _, l := range russianLangs {
			if strings.HasPrefix(v, l) {
				return true
			}
		}
		return false
	}
	// A Finder double-click may give no LANG: the Mac's own language then.
	if out, err := exec.Command("defaults", "read", "-g", "AppleLocale").Output(); err == nil {
		for _, l := range russianLangs {
			if strings.HasPrefix(strings.TrimSpace(string(out)), l) {
				return true
			}
		}
	}
	return false
}
