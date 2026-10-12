//go:build !windows

package main

import (
	"os"
	"path/filepath"
	"runtime"
)

// browserDirs: each browser's folder for host manifests. The first of each list is written even
// when its browser isn't there yet (Chrome, Firefox); the rest only when the browser is.
func browserDirs() (chromium, firefox []string) {
	h, _ := os.UserHomeDir()
	if runtime.GOOS == "darwin" {
		base := filepath.Join(h, "Library", "Application Support")
		for _, d := range []string{"Google/Chrome", "Google/Chrome Beta", "Google/Chrome Canary", "Chromium", "Microsoft Edge", "BraveSoftware/Brave-Browser", "Vivaldi", "Yandex/YandexBrowser", "com.operasoftware.Opera"} {
			chromium = append(chromium, filepath.Join(base, filepath.FromSlash(d)))
		}
		return chromium, []string{filepath.Join(base, "Mozilla")}
	}
	base := filepath.Join(h, ".config")
	for _, d := range []string{"google-chrome", "chromium", "microsoft-edge", "BraveSoftware/Brave-Browser", "vivaldi", "yandex-browser", "opera"} {
		chromium = append(chromium, filepath.Join(base, filepath.FromSlash(d)))
	}
	return chromium, []string{filepath.Join(h, ".mozilla")}
}

func hostsDir(browser string, firefox bool) string {
	if firefox && runtime.GOOS != "darwin" {
		return filepath.Join(browser, "native-messaging-hosts")
	}
	return filepath.Join(browser, "NativeMessagingHosts")
}

func register(exe, _, _ string) error {
	chromium, firefox := browserDirs()
	write := func(browser string, ff, always bool) error {
		if !always && !exists(browser) {
			return nil
		}
		dir := hostsDir(browser, ff)
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
		return os.WriteFile(filepath.Join(dir, hostName()+".json"), manifest(exe, ff), 0o644)
	}
	for i, b := range chromium {
		if err := write(b, false, i == 0); err != nil {
			return err
		}
	}
	return write(firefox[0], true, true)
}

func unregister() {
	chromium, firefox := browserDirs()
	for _, b := range chromium {
		_ = os.Remove(filepath.Join(hostsDir(b, false), hostName()+".json"))
	}
	_ = os.Remove(filepath.Join(hostsDir(firefox[0], true), hostName()+".json"))
}

func removeHome(dir string) error { return os.RemoveAll(dir) }
