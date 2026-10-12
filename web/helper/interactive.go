package main

// Started by hand — downloaded from the site and opened — the helper installs itself and gets yt-dlp
// ready, saying what it does in a console window, in Russian or English as the system speaks.
// Opened again once installed, it checks the install and offers to remove itself.

import (
	"bufio"
	"fmt"
	"os"
	"path/filepath"
	"strings"
)

func interactive() {
	ru := systemRussian()
	say := func(rus, eng string, args ...any) {
		if ru {
			fmt.Printf(rus+"\n", args...)
		} else {
			fmt.Printf(eng+"\n", args...)
		}
	}
	in := bufio.NewReader(os.Stdin)
	wait := func() {
		say("\nНажми Enter, чтобы закрыть.", "\nPress Enter to close.")
		_, _ = in.ReadString('\n')
	}
	fmt.Printf("YouCloud Helper %s\n\n", Version)

	target := filepath.Join(home(), exeName())
	if installedVersion(target) == Version {
		say("Помощник уже стоит — проверяю, что браузеры о нём знают.", "The helper is already installed — checking that the browsers know of it.")
		if _, err := install(); err != nil {
			say("Не получилось: %v", "That failed: %v", err)
			wait()
			os.Exit(1)
		}
		say("Всё в порядке.\n\nЧтобы удалить помощник, напиши «удалить» и нажми Enter. Просто Enter — закрыть.",
			"All is well.\n\nTo remove the helper, type \"delete\" and press Enter. Just Enter closes this.")
		line, _ := in.ReadString('\n')
		switch strings.ToLower(strings.TrimSpace(line)) {
		case "удалить", "delete", "uninstall":
			if err := uninstall(); err != nil {
				say("Не получилось удалить: %v", "Couldn't remove it: %v", err)
				wait()
				os.Exit(1)
			}
			say("Помощник удалён: браузеры о нём забыли, его папка удаляется.", "The helper is removed: the browsers forgot it, its folder is going.")
			wait()
		}
		return
	}

	say("Ставлю помощник…", "Installing the helper…")
	if _, err := install(); err != nil {
		say("Не получилось: %v", "That failed: %v", err)
		wait()
		os.Exit(1)
	}
	say("  — лежит в %s", "  — it lives in %s", home())
	say("  — Chrome, Edge, Яндекс Браузер, Opera, Brave и Firefox знают, где он", "  — Chrome, Edge, Yandex Browser, Opera, Brave and Firefox know where it is")

	say("\nСкачиваю yt-dlp для звука YouTube…", "\nDownloading yt-dlp for YouTube's sound…")
	if _, err := ensureYtdlp(); err != nil {
		say("  — не вышло (%v): помощник попробует сам, когда понадобится", "  — that failed (%v): the helper will try again when it's needed", err)
	} else {
		say("  — готово", "  — done")
	}
	say("Ищу JavaScript для YouTube (Deno или Node)…", "Looking for JavaScript for YouTube (Deno or Node)…")
	if js, err := jsRuntime(); err != nil {
		say("  — не вышло (%v): помощник попробует сам, когда понадобится", "  — that failed (%v): the helper will try again when it's needed", err)
	} else {
		say("  — %s", "  — %s", strings.SplitN(js, ":", 2)[0])
	}

	say("\nГотово. Вернись на сайт YouCloud — помощник подключится сам.", "\nDone. Go back to YouCloud — the helper connects by itself.")
	wait()
}
