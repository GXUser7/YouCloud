package main

import (
	"fmt"
	"net"
	"os/exec"
	"syscall"
	"time"

	"github.com/Microsoft/go-winio"
	"golang.org/x/sys/windows"
)

func ipcPaths() []string {
	paths := make([]string, 10)
	for i := range paths {
		paths[i] = fmt.Sprintf(`\\.\pipe\discord-ipc-%d`, i)
	}
	return paths
}

// dialIPC opens Discord's pipe for overlapped I/O: one goroutine waits on a read while another
// writes. A pipe opened the plain way serializes them, and a waiting read would hold every write.
func dialIPC(path string) (net.Conn, error) {
	timeout := 2 * time.Second
	return winio.DialPipe(path, &timeout)
}

// hideWindow: the browser starts the helper without a console, and yt-dlp, a console program,
// would otherwise open a window of its own.
func hideWindow(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{HideWindow: true, CreationFlags: windows.CREATE_NO_WINDOW}
}

// Russian-reading listeners get the helper's words in Russian: the language Windows speaks.
func systemRussian() bool {
	lang, _, _ := windows.NewLazySystemDLL("kernel32.dll").NewProc("GetUserDefaultUILanguage").Call()
	return russianPrimary[uint16(lang)&0x3ff]
}

// Primary language ids of the languages whose readers the site greets in Russian.
var russianPrimary = map[uint16]bool{0x19: true, 0x22: true, 0x23: true, 0x3f: true, 0x40: true, 0x43: true, 0x28: true, 0x2b: true, 0x2c: true, 0x37: true}
