package main

import (
	"os"
	"os/exec"
	"syscall"

	"golang.org/x/sys/windows"
	"golang.org/x/sys/windows/registry"
)

// Where each browser looks, under HKEY_CURRENT_USER. Opera and Vivaldi read Chrome's key as well.
var chromiumKeys = []string{
	`Software\Google\Chrome`,
	`Software\Chromium`,
	`Software\Microsoft\Edge`,
	`Software\BraveSoftware\Brave-Browser`,
	`Software\Yandex\YandexBrowser`,
	`Software\Vivaldi`,
}

const firefoxKey = `Software\Mozilla`

func hostKey(browser string) string { return browser + `\NativeMessagingHosts\` + hostName() }

func register(_, chrome, firefox string) error {
	set := func(key, manifest string) error {
		k, _, err := registry.CreateKey(registry.CURRENT_USER, key, registry.SET_VALUE)
		if err != nil {
			return err
		}
		defer k.Close()
		return k.SetStringValue("", manifest)
	}
	for _, b := range chromiumKeys {
		if err := set(hostKey(b), chrome); err != nil {
			return err
		}
	}
	return set(hostKey(firefoxKey), firefox)
}

func unregister() {
	for _, b := range append(chromiumKeys, firefoxKey) {
		_ = registry.DeleteKey(registry.CURRENT_USER, hostKey(b))
	}
}

// removeHome deletes the folder; what Windows won't let go of yet (the helper running this, or one
// a browser still holds) goes a moment after, by a hidden cmd that outlives it.
func removeHome(dir string) error {
	if os.RemoveAll(dir) == nil {
		return nil
	}
	cmd := exec.Command("cmd.exe")
	cmd.SysProcAttr = &syscall.SysProcAttr{
		HideWindow:    true,
		CreationFlags: windows.CREATE_NO_WINDOW | windows.DETACHED_PROCESS,
		CmdLine:       `cmd.exe /d /c "ping -n 4 127.0.0.1 >nul & rmdir /s /q "` + dir + `""`,
	}
	return cmd.Start()
}
