package main

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"

	"com.omplayer/ytdlbridge/internal/bridge"
	youtube "github.com/lvcoi/ytdl-lib/v2"
)

type response struct {
	Type          string         `json:"type"`
	Tracks        []bridge.Track `json:"tracks,omitempty"`
	URL           string         `json:"url,omitempty"`
	ContentLength *int64         `json:"content_length,omitempty"`
	ConsentCookie string         `json:"consent_cookie,omitempty"`
	MIMEType      string         `json:"mime_type,omitempty"`
	ItagNo        int            `json:"itag,omitempty"`
	Error         string         `json:"error,omitempty"`
	Progress      float64        `json:"progress,omitempty"`
	Path          string         `json:"path,omitempty"`
}

func main() {
	if len(os.Args) < 2 {
		fail(errors.New("missing command"))
	}
	switch os.Args[1] {
	case "search":
		ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
		defer cancel()
		if len(os.Args) < 3 {
			fail(errors.New("missing search query"))
		}
		tracks, err := bridge.Search(ctx, strings.Join(os.Args[2:], " "), 30)
		if err != nil {
			fail(err)
		}
		emit(response{Type: "search", Tracks: tracks})
	case "resolve":
		ctx, cancel := context.WithTimeout(context.Background(), 25*time.Second)
		defer cancel()
		if len(os.Args) != 3 {
			fail(errors.New("usage: resolve <video-url>"))
		}
		stream, err := bridge.Resolve(ctx, os.Args[2])
		if err != nil {
			fail(err)
		}
		emit(response{
			Type:          "resolve",
			URL:           stream.URL,
			ContentLength: &stream.ContentLength,
			ConsentCookie: stream.ConsentCookie,
			MIMEType:      stream.MIMEType,
			ItagNo:        stream.ItagNo,
		})
	case "download-resolved":
		if len(os.Args) != 3 {
			fail(errors.New("usage: download-resolved <output-base-path>"))
		}
		var stream bridge.ResolvedStream
		if err := json.NewDecoder(io.LimitReader(os.Stdin, 64<<10)).Decode(&stream); err != nil {
			fail(errors.New("invalid resolved stream input"))
		}
		path, err := bridge.DownloadResolved(context.Background(), stream, os.Args[2], func(progress float64) {
			emit(response{Type: "progress", Progress: progress})
		})
		if err != nil {
			fail(err)
		}
		emit(response{Type: "done", Path: path})
	case "download":
		if len(os.Args) != 4 {
			fail(errors.New("usage: download <video-url> <output-base-path>"))
		}
		// The Android caller owns cancellation; a song download has no fixed duration limit.
		path, err := download(context.Background(), os.Args[2], os.Args[3])
		if err != nil {
			fail(err)
		}
		emit(response{Type: "done", Path: path})
	default:
		fail(errors.New("unknown command"))
	}
}

func download(ctx context.Context, page, basePath string) (string, error) {
	if !bridge.IsYouTubeURL(page) {
		return "", errors.New("unsupported video address")
	}
	if err := os.MkdirAll(filepath.Dir(basePath), 0700); err != nil {
		return "", err
	}
	// A deterministic temp folder lets the Kotlin caller remove an interrupted transfer.
	tempDir := basePath + ".tmp"
	if err := os.RemoveAll(tempDir); err != nil {
		return "", err
	}
	if err := os.Mkdir(tempDir, 0700); err != nil {
		return "", err
	}
	defer os.RemoveAll(tempDir)

	client := &youtube.Client{
		HTTPClient: &http.Client{Transport: &http.Transport{
			DialContext:           (&net.Dialer{Timeout: 15 * time.Second, KeepAlive: 30 * time.Second}).DialContext,
			TLSHandshakeTimeout:   10 * time.Second,
			ResponseHeaderTimeout: 20 * time.Second,
			IdleConnTimeout:       30 * time.Second,
		}},
		ClientType: &youtube.AndroidClient,
	}
	video, err := client.GetVideoContext(ctx, page)
	if err != nil {
		return "", err
	}
	format, err := bridge.BestAudioFormat(video.Formats)
	if err != nil {
		return "", err
	}
	stream, total, err := client.GetStreamContext(ctx, video, &format)
	if err != nil {
		return "", err
	}
	defer stream.Close()

	extension := ".m4a"
	mimeType := strings.ToLower(format.MimeType)
	if strings.Contains(mimeType, "webm") {
		extension = ".webm"
	}
	if strings.Contains(mimeType, "audio/ogg") {
		extension = ".ogg"
	}
	partPath := filepath.Join(tempDir, "audio.part")
	file, err := os.OpenFile(partPath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0600)
	if err != nil {
		return "", err
	}
	var written int64
	last := time.Now()
	buf := make([]byte, 64*1024)
	for {
		n, readErr := stream.Read(buf)
		if n > 0 {
			count, writeErr := file.Write(buf[:n])
			written += int64(count)
			if writeErr != nil {
				_ = file.Close()
				return "", writeErr
			}
			if total > 0 && time.Since(last) >= 400*time.Millisecond {
				emit(response{Type: "progress", Progress: float64(written) / float64(total)})
				last = time.Now()
			}
		}
		if readErr == io.EOF {
			break
		}
		if readErr != nil {
			_ = file.Close()
			return "", readErr
		}
		if err := ctx.Err(); err != nil {
			_ = file.Close()
			return "", err
		}
	}
	if err := file.Sync(); err != nil {
		_ = file.Close()
		return "", err
	}
	if err := file.Close(); err != nil {
		return "", err
	}
	finalPath := basePath + extension
	if err := os.Rename(partPath, finalPath); err != nil {
		return "", err
	}
	emit(response{Type: "progress", Progress: 1})
	return finalPath, nil
}

func emit(v response) { _ = json.NewEncoder(os.Stdout).Encode(v) }
func fail(err error) {
	emit(response{Type: "error", Error: err.Error()})
	_, _ = fmt.Fprintln(os.Stderr, err)
	os.Exit(1)
}
