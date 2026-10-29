package bridge

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math/big"
	"net"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	youtube "github.com/lvcoi/ytdl-lib/v2"
)

const maxSearchBytes = 12 << 20

var searchEndpoint = "https://www.youtube.com/results"

// Track is the stable JSON shape consumed by the Android Discover source.
type Track struct {
	ID           string `json:"id"`
	Title        string `json:"title"`
	Artist       string `json:"artist"`
	Duration     int64  `json:"duration_ms"`
	ArtworkURL   string `json:"artwork_url"`
	StreamURL    string `json:"stream_url"`
	PageURL      string `json:"page_url"`
	CanDownload  bool   `json:"can_download"`
	SourceID     string `json:"source_id"`
	NeedsResolve bool   `json:"needs_resolve"`
}

// Search reads public search results from YouTube's web results page.
func Search(ctx context.Context, query string, limit int) ([]Track, error) {
	query = strings.TrimSpace(query)
	if query == "" {
		return []Track{}, nil
	}
	if limit < 1 || limit > 30 {
		limit = 30
	}
	searchURL, err := url.Parse(searchEndpoint)
	if err != nil {
		return nil, err
	}
	searchURL.RawQuery = url.Values{"search_query": []string{query}}.Encode()
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, searchURL.String(), nil)
	if err != nil {
		return nil, err
	}
	req.Header.Set("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36")
	req.Header.Set("Accept-Language", "en-US,en;q=0.9")
	resp, err := (&http.Client{Timeout: 18 * time.Second}).Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("search returned HTTP %d", resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxSearchBytes+1))
	if err != nil {
		return nil, err
	}
	if len(body) > maxSearchBytes {
		return nil, errors.New("search response is too large")
	}
	payload := extractInitialData(body)
	if len(payload) == 0 {
		return nil, errors.New("search results could not be read")
	}
	tracks := make([]Track, 0, limit)
	collectVideoRenderers(payload, &tracks, limit)
	if len(tracks) == 0 {
		return nil, errors.New("no searchable video results were returned")
	}
	return tracks, nil
}

// extractInitialData finds the balanced object after ytInitialData. A regexp
// cannot safely delimit nested objects or braces inside quoted strings.
func extractInitialData(body []byte) []byte {
	const marker = "ytInitialData"
	for searchAt := 0; searchAt < len(body); {
		found := bytes.Index(body[searchAt:], []byte(marker))
		if found < 0 {
			return nil
		}
		i := searchAt + found + len(marker)
		searchAt = i
		for i < len(body) && (body[i] == ' ' || body[i] == '\t' || body[i] == '\r' || body[i] == '\n') {
			i++
		}
		if i >= len(body) || body[i] != '=' {
			continue
		}
		i++
		for i < len(body) && (body[i] == ' ' || body[i] == '\t' || body[i] == '\r' || body[i] == '\n') {
			i++
		}
		if i < len(body) && body[i] == '{' {
			if object := extractJSONObject(body, i); len(object) > 0 && json.Valid(object) {
				return object
			}
		}
	}
	return nil
}

func extractJSONObject(body []byte, start int) []byte {
	if start >= len(body) || body[start] != '{' {
		return nil
	}
	i, depth := start, 0
	inString, escaped := false, false
	for ; i < len(body); i++ {
		c := body[i]
		if inString {
			if escaped {
				escaped = false
			} else if c == '\\' {
				escaped = true
			} else if c == '"' {
				inString = false
			}
			continue
		}
		switch c {
		case '"':
			inString = true
		case '{':
			depth++
		case '}':
			depth--
			if depth == 0 {
				return body[start : i+1]
			}
		}
	}
	return nil
}

// Scan raw JSON to preserve result order; decoding a tree into Go maps would
// randomize siblings and can put recommendations ahead of search hits.
func collectVideoRenderers(payload []byte, out *[]Track, limit int) {
	seen := make(map[string]bool)
	for offset := 0; offset < len(payload) && len(*out) < limit; {
		legacyAt := bytes.Index(payload[offset:], []byte("\"videoRenderer\""))
		contextAt := bytes.Index(payload[offset:], []byte("\"videoWithContextRenderer\""))
		found, markerLength := legacyAt, len("\"videoRenderer\"")
		if contextAt >= 0 && (legacyAt < 0 || contextAt < legacyAt) {
			found, markerLength = contextAt, len("\"videoWithContextRenderer\"")
		}
		if found < 0 {
			return
		}
		i := offset + found + markerLength
		offset = i
		for i < len(payload) && (payload[i] == ' ' || payload[i] == '\t' || payload[i] == '\r' || payload[i] == '\n') {
			i++
		}
		if i >= len(payload) || payload[i] != ':' {
			continue
		}
		i++
		for i < len(payload) && (payload[i] == ' ' || payload[i] == '\t' || payload[i] == '\r' || payload[i] == '\n') {
			i++
		}
		object := extractJSONObject(payload, i)
		if len(object) == 0 {
			continue
		}
		offset = i + len(object)
		var renderer map[string]any
		if json.Unmarshal(object, &renderer) != nil {
			continue
		}
		track, ok := parseRenderer(renderer)
		if !ok || seen[track.ID] {
			continue
		}
		seen[track.ID] = true
		*out = append(*out, track)
	}
}

func parseRenderer(m map[string]any) (Track, bool) {
	id, _ := m["videoId"].(string)
	title := textValue(m["title"])
	if title == "" {
		title = textValue(m["headline"])
	}
	if id == "" || title == "" {
		return Track{}, false
	}
	author := textValue(m["ownerText"])
	if author == "" {
		author = textValue(m["longBylineText"])
	}
	if author == "" {
		author = textValue(m["shortBylineText"])
	}
	duration, _ := m["lengthText"].(map[string]any)
	page := "https://www.youtube.com/watch?v=" + url.QueryEscape(id)
	artwork := "https://i.ytimg.com/vi/" + url.PathEscape(id) + "/hqdefault.jpg"
	if thumbs, ok := m["thumbnail"].(map[string]any); ok {
		if list, ok := thumbs["thumbnails"].([]any); ok && len(list) > 0 {
			if last, ok := list[len(list)-1].(map[string]any); ok {
				if s, ok := last["url"].(string); ok && strings.HasPrefix(s, "https://") {
					artwork = s
				}
			}
		}
	}
	return Track{ID: id, Title: title, Artist: author, Duration: int64(parseDuration(textValue(duration))) * 1000,
		ArtworkURL: artwork, StreamURL: page, PageURL: page, CanDownload: true, SourceID: "ytdlp", NeedsResolve: true}, true
}

func textValue(v any) string {
	m, ok := v.(map[string]any)
	if !ok {
		return ""
	}
	if s, ok := m["simpleText"].(string); ok {
		return s
	}
	if runs, ok := m["runs"].([]any); ok {
		var b strings.Builder
		for _, r := range runs {
			if run, ok := r.(map[string]any); ok {
				if s, ok := run["text"].(string); ok {
					b.WriteString(s)
				}
			}
		}
		return strings.TrimSpace(b.String())
	}
	return ""
}

func parseDuration(s string) int {
	seconds := 0
	for _, p := range strings.Split(s, ":") {
		var n int
		if _, err := fmt.Sscanf(p, "%d", &n); err != nil {
			return 0
		}
		seconds = seconds*60 + n
	}
	return seconds
}

// ResolvedStream carries the exact selected format metadata needed to request
// bounded byte ranges from the same signed media URL.
type ResolvedStream struct {
	URL           string `json:"url"`
	ContentLength int64  `json:"content_length"`
	ConsentCookie string `json:"consent_cookie"`
	MIMEType      string `json:"mime_type"`
	ItagNo        int    `json:"itag"`
}

// Resolve uses the same Go client as ytdl-go to obtain an expiring audio URL.
func Resolve(ctx context.Context, pageURL string) (ResolvedStream, error) {
	if !validYouTubeURL(pageURL) {
		return ResolvedStream{}, errors.New("unsupported video address")
	}
	// Follow yt-dlp's current VISIONOS profile (yt-dlp/yt_dlp/extractor/youtube/_base.py).
	visionOSClient := youtube.ClientInfo{
		Name: "VISIONOS", Version: "1.02", Key: youtube.WebClient.Key,
		UserAgent:   "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15",
		DeviceModel: "RealityDevice17,1",
	}
	client := &youtube.Client{HTTPClient: &http.Client{Timeout: 18 * time.Second}, ClientType: &visionOSClient}
	video, err := client.GetVideoContext(ctx, pageURL)
	if err != nil {
		return ResolvedStream{}, err
	}
	format, err := BestAudioFormat(video.Formats)
	if err != nil {
		return ResolvedStream{}, err
	}
	streamURL, err := client.GetStreamURLContext(ctx, video, &format)
	if err != nil {
		return ResolvedStream{}, err
	}
	cookie, err := newConsentCookie()
	if err != nil {
		return ResolvedStream{}, fmt.Errorf("prepare stream request cookie: %w", err)
	}
	return ResolvedStream{
		URL: streamURL, ContentLength: format.ContentLength, ConsentCookie: cookie,
		MIMEType: format.MimeType, ItagNo: format.ItagNo,
	}, nil
}

// DownloadResolved transfers the exact stream selected during Resolve. The URL is
// short-lived, so the caller resolves immediately before handing it to this method.
func DownloadResolved(ctx context.Context, stream ResolvedStream, basePath string, progress func(float64)) (string, error) {
	client := &http.Client{Transport: &http.Transport{
		DialContext:           (&net.Dialer{Timeout: 15 * time.Second, KeepAlive: 30 * time.Second}).DialContext,
		TLSHandshakeTimeout:   10 * time.Second,
		ResponseHeaderTimeout: 20 * time.Second,
		IdleConnTimeout:       30 * time.Second,
	}}
	return downloadResolvedWithClient(ctx, stream, basePath, progress, client)
}

func downloadResolvedWithClient(ctx context.Context, stream ResolvedStream, basePath string, progress func(float64), client *http.Client) (string, error) {
	if stream.ContentLength <= 0 || stream.ItagNo <= 0 || stream.MIMEType == "" || !validGoogleVideoURL(stream.URL) || !validConsentCookie(stream.ConsentCookie) {
		return "", errors.New("invalid resolved audio stream")
	}
	extension, err := audioExtension(stream.MIMEType)
	if err != nil {
		return "", err
	}
	if strings.TrimSpace(basePath) == "" || client == nil {
		return "", errors.New("invalid download destination")
	}
	if err := os.MkdirAll(filepath.Dir(basePath), 0700); err != nil {
		return "", err
	}
	tempDir := basePath + ".tmp"
	if err := os.RemoveAll(tempDir); err != nil {
		return "", err
	}
	if err := os.Mkdir(tempDir, 0700); err != nil {
		return "", err
	}
	defer os.RemoveAll(tempDir)

	partPath := filepath.Join(tempDir, "audio.part")
	file, err := os.OpenFile(partPath, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0600)
	if err != nil {
		return "", err
	}
	closed := false
	defer func() {
		if !closed {
			_ = file.Close()
		}
	}()

	const chunkSize = int64(10 * 1024 * 1024)
	var written int64
	for written < stream.ContentLength {
		if err := ctx.Err(); err != nil {
			return "", err
		}
		chunkLength := min(chunkSize, stream.ContentLength-written)
		requestURL, err := googleVideoRangeURL(stream.URL, written, written+chunkLength-1)
		if err != nil {
			return "", err
		}
		request, err := http.NewRequestWithContext(ctx, http.MethodGet, requestURL, nil)
		if err != nil {
			return "", err
		}
		request.Header.Set("User-Agent", "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip")
		request.Header.Set("Origin", "https://youtube.com")
		request.Header.Set("Sec-Fetch-Mode", "navigate")
		request.Header.Set("Accept-Encoding", "identity")
		request.Header.Set("Cookie", stream.ConsentCookie)
		response, err := client.Do(request)
		if err != nil {
			return "", err
		}
		if response.StatusCode != http.StatusOK && response.StatusCode != http.StatusPartialContent {
			_ = response.Body.Close()
			return "", fmt.Errorf("media range request returned HTTP %d", response.StatusCode)
		}
		if response.ContentLength >= 0 && response.ContentLength != chunkLength {
			_ = response.Body.Close()
			return "", fmt.Errorf("media range returned %d bytes; expected %d", response.ContentLength, chunkLength)
		}
		if response.StatusCode == http.StatusPartialContent && !matchesContentRange(response.Header.Get("Content-Range"), written, written+chunkLength-1, stream.ContentLength) {
			_ = response.Body.Close()
			return "", errors.New("media server returned an unexpected content range")
		}
		count, copyErr := io.CopyN(file, response.Body, chunkLength)
		closeErr := response.Body.Close()
		if copyErr != nil || count != chunkLength {
			if copyErr != nil {
				return "", copyErr
			}
			return "", errors.New("media range ended before the expected byte count")
		}
		if closeErr != nil {
			return "", closeErr
		}
		written += count
		if progress != nil {
			progress(float64(written) / float64(stream.ContentLength))
		}
	}
	if err := file.Sync(); err != nil {
		return "", err
	}
	if err := file.Close(); err != nil {
		closed = true
		return "", err
	}
	closed = true

	outputPath := basePath + extension
	if err := os.Rename(partPath, outputPath); err != nil {
		return "", err
	}
	return outputPath, nil
}

func validGoogleVideoURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "https" || u.User != nil || u.RawQuery == "" {
		return false
	}
	host := strings.ToLower(u.Hostname())
	return host == "googlevideo.com" || strings.HasSuffix(host, ".googlevideo.com")
}

func googleVideoRangeURL(raw string, start, end int64) (string, error) {
	if start < 0 || end < start {
		return "", errors.New("invalid media range")
	}
	u, err := url.Parse(raw)
	if err != nil || !validGoogleVideoURL(raw) {
		return "", errors.New("invalid resolved media URL")
	}
	query := u.Query()
	query.Set("range", fmt.Sprintf("%d-%d", start, end))
	u.RawQuery = query.Encode()
	return u.String(), nil
}

func audioExtension(mimeType string) (string, error) {
	mediaType := strings.ToLower(strings.TrimSpace(strings.SplitN(mimeType, ";", 2)[0]))
	switch mediaType {
	case "audio/mp4":
		return ".m4a", nil
	case "audio/webm":
		return ".webm", nil
	case "audio/ogg":
		return ".ogg", nil
	case "video/mp4": // The extractor can fall back to a muxed audio/video format.
		return ".mp4", nil
	default:
		return "", fmt.Errorf("unsupported stream format %q", mediaType)
	}
}

func matchesContentRange(value string, start, end, total int64) bool {
	return value == fmt.Sprintf("bytes %d-%d/%d", start, end, total)
}

func validConsentCookie(value string) bool {
	if !strings.HasPrefix(value, consentCookiePrefix) {
		return false
	}
	suffix, err := strconv.Atoi(strings.TrimPrefix(value, consentCookiePrefix))
	return err == nil && suffix >= 100 && suffix <= 998
}

const consentCookiePrefix = "CONSENT=YES+cb.20210328-17-p0.en+FX+"

// newConsentCookie mirrors ytdl-lib's consent cookie shape without exposing
// its private per-client random state to callers.
func newConsentCookie() (string, error) {
	suffix, err := rand.Int(rand.Reader, big.NewInt(899))
	if err != nil {
		return "", err
	}
	return fmt.Sprintf("%s%d", consentCookiePrefix, suffix.Int64()+100), nil
}

// BestAudioFormat favors standalone audio and falls back to muxed formats.
func BestAudioFormat(all youtube.FormatList) (youtube.Format, error) {
	formats := all.WithAudioChannels()
	if len(formats) == 0 {
		return youtube.Format{}, errors.New("no audio format is available")
	}
	if audioOnly := formats.Type("audio/"); len(audioOnly) > 0 {
		formats = audioOnly
	}
	formats.Sort()
	return formats[0], nil
}

func validYouTubeURL(raw string) bool {
	u, err := url.Parse(raw)
	if err != nil || u.Scheme != "https" {
		return false
	}
	host := strings.ToLower(u.Hostname())
	return host == "youtube.com" || strings.HasSuffix(host, ".youtube.com") || host == "youtu.be"
}

// IsYouTubeURL reports whether a URL points to a supported public YouTube host.
func IsYouTubeURL(raw string) bool { return validYouTubeURL(raw) }
