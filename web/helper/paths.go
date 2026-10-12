package main

import (
	"os"
	"path/filepath"
	"runtime"
)

const (
	// The extension's id in Chromium, fixed by the key in its manifest; and its id in Firefox.
	extensionID = "jddbcipfppgdolkopjmaidoejnjoanch"
	geckoID     = "youcloud-bridge@gxuser7"
)

// hostName is how the extension asks for the helper. Tests install under a name of their own,
// so they never touch a helper the listener has.
func hostName() string {
	if n := os.Getenv("YOUCLOUD_HELPER_NAME"); n != "" {
		return n
	}
	return "com.youcloud.helper"
}

// home is where the helper lives with everything it fetches: the listener's own folder, so
// neither installing nor updating asks for an administrator.
func home() string {
	if d := os.Getenv("YOUCLOUD_HELPER_HOME"); d != "" {
		return d
	}
	switch runtime.GOOS {
	case "windows":
		d, _ := os.UserCacheDir() // %LOCALAPPDATA%
		return filepath.Join(d, "YouCloud Helper")
	case "darwin":
		d, _ := os.UserConfigDir() // ~/Library/Application Support
		return filepath.Join(d, "YouCloud Helper")
	default:
		d, _ := os.UserHomeDir()
		return filepath.Join(d, ".local", "share", "youcloud-helper")
	}
}

func exeName() string {
	if runtime.GOOS == "windows" {
		return "youcloud-helper.exe"
	}
	return "youcloud-helper"
}

func exists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}
