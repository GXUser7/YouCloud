package main

import (
	"fmt"
	"io"
	"net/http"
	"time"
)

// fetch downloads [url] whole: GitHub's release files, the largest being Deno's tens of megabytes.
func fetch(url string, timeout time.Duration) ([]byte, error) {
	client := &http.Client{Timeout: timeout}
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "YouCloud-Helper/"+Version)
	res, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GitHub ответил %d", res.StatusCode)
	}
	return io.ReadAll(res.Body)
}
