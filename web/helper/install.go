package main

// Installing is the helper copying itself into its folder and telling each browser where it is:
// a small JSON (the "manifest") that names the program and the one extension allowed to start it.
// Chromium browsers on Windows find it through a key in the listener's own part of the registry,
// on a Mac and Linux in a folder of each browser's; Firefox has its own of both.

import (
	"encoding/json"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
)

func manifest(exe string, firefox bool) []byte {
	m := map[string]any{
		"name":        hostName(),
		"description": "YouCloud Helper: YouTube through yt-dlp and the status in Discord",
		"path":        exe,
		"type":        "stdio",
	}
	if firefox {
		m["allowed_extensions"] = []string{geckoID}
	} else {
		m["allowed_origins"] = []string{"chrome-extension://" + extensionID + "/"}
	}
	data, _ := json.MarshalIndent(m, "", "  ")
	return append(data, '\n')
}

// install puts the helper in its folder (the copy running now, when it runs from elsewhere) and
// registers it with the browsers. Run again, it repairs: the same files, the same keys.
func install() (string, error) {
	dir := home()
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return "", err
	}
	target := filepath.Join(dir, exeName())
	cleanOld(target)
	self, err := os.Executable()
	if err != nil {
		return "", err
	}
	if s, err := filepath.EvalSymlinks(self); err == nil {
		self = s
	}
	if !samePath(self, target) {
		if err := copySelf(self, target); err != nil {
			return "", err
		}
	}
	chrome := filepath.Join(dir, hostName()+".json")
	firefox := filepath.Join(dir, hostName()+".firefox.json")
	if err := os.WriteFile(chrome, manifest(target, false), 0o644); err != nil {
		return "", err
	}
	if err := os.WriteFile(firefox, manifest(target, true), 0o644); err != nil {
		return "", err
	}
	return target, register(target, chrome, firefox)
}

func samePath(a, b string) bool {
	a, b = filepath.Clean(a), filepath.Clean(b)
	if strings.EqualFold(a, b) {
		return true
	}
	sa, errA := os.Stat(a)
	sb, errB := os.Stat(b)
	return errA == nil && errB == nil && os.SameFile(sa, sb)
}

// copySelf writes the running helper over [target]. A helper the browser is running can't be
// overwritten on Windows, but it can be renamed away: the next start clears it up.
func copySelf(self, target string) error {
	data, err := os.ReadFile(self)
	if err != nil {
		return err
	}
	part := target + ".part"
	if err := os.WriteFile(part, data, 0o755); err != nil {
		return err
	}
	if err := os.Rename(part, target); err == nil {
		return nil
	}
	old, err := os.CreateTemp(filepath.Dir(target), filepath.Base(target)+".old-*")
	if err != nil {
		return err
	}
	old.Close()
	_ = os.Remove(old.Name())
	if err := os.Rename(target, old.Name()); err != nil {
		_ = os.Remove(part)
		return err
	}
	return os.Rename(part, target)
}

func cleanOld(target string) {
	old, _ := filepath.Glob(target + ".old-*")
	for _, f := range old {
		_ = os.Remove(f)
	}
}

// installedVersion asks the installed helper its version; "" when there is none.
func installedVersion(exe string) string {
	if !exists(exe) {
		return ""
	}
	cmd := exec.Command(exe, "--version")
	hideWindow(cmd)
	out, err := cmd.Output()
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(out))
}

// uninstall: the browsers forget the helper, and its folder goes with all it fetched.
func uninstall() error {
	unregister()
	return removeHome(home())
}
