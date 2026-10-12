// YouCloud Helper: what the YouCloud extension can't do from inside the browser. YouTube's sound
// through yt-dlp — as the Android app and Masaki find it, past the "confirm you're not a bot" the
// site's own clients run into — and the listener's Discord status, "Listening to YouCloud".
//
// The browser starts it (Native Messaging) when the extension first asks, and it lives as long as
// the extension holds it: messages come in on stdin and go out on stdout, each a JSON with its
// length before it. Started by hand, it installs itself: copies itself into the listener's own
// folder and tells the browsers where it is — no administrator needed.
package main

import (
	_ "embed"
	"encoding/json"
	"fmt"
	"os"
	"strings"
)

//go:embed version.json
var versionJSON []byte

// Version is the helper's own, the one the site compares with what it hands out (version.json).
var Version = func() string {
	var v struct{ Version string }
	_ = json.Unmarshal(versionJSON, &v)
	return v.Version
}()

func main() {
	args := os.Args[1:]
	switch {
	case fromBrowser(args):
		serve()
	case has(args, "--version"):
		fmt.Println(Version)
	case has(args, "--install"):
		if _, err := install(); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	case has(args, "--uninstall"):
		if err := uninstall(); err != nil {
			fmt.Fprintln(os.Stderr, err)
			os.Exit(1)
		}
	default:
		interactive()
	}
}

// fromBrowser: Chromium passes the extension's origin (and, on Windows, --parent-window); Firefox
// the path of the manifest and the extension's id.
func fromBrowser(args []string) bool {
	for _, a := range args {
		if strings.HasPrefix(a, "chrome-extension://") || a == geckoID || strings.HasSuffix(strings.ToLower(a), ".json") {
			return true
		}
	}
	return false
}

func has(args []string, flag string) bool {
	for _, a := range args {
		if a == flag {
			return true
		}
	}
	return false
}
