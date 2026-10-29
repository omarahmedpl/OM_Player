package bridge

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"

	youtube "github.com/lvcoi/ytdl-lib/v2"
)

func TestParseRenderer(t *testing.T) {
	raw := `{"videoId":"abc123","title":{"runs":[{"text":"A & B"}]},"ownerText":{"simpleText":"Artist"},"lengthText":{"simpleText":"1:23"},"thumbnail":{"thumbnails":[{"url":"https://img.example/s.jpg"}]}}`
	var m map[string]any
	if err := json.Unmarshal([]byte(raw), &m); err != nil {
		t.Fatal(err)
	}
	track, ok := parseRenderer(m)
	if !ok || track.ID != "abc123" || track.Title != "A & B" || track.Artist != "Artist" || track.Duration != 83_000 || !track.NeedsResolve {
		t.Fatalf("unexpected track: %#v (ok=%v)", track, ok)
	}
}

func TestSearchParsesResultsWithBoundedLimit(t *testing.T) {
	body := "var ytInitialData = {\"contents\":{\"items\":[{\"videoRenderer\":{\"videoId\":\"one\",\"title\":{\"simpleText\":\"Brace } and quote\"}}},{\"videoRenderer\":{\"videoId\":\"two\",\"title\":{\"simpleText\":\"Two\"}}}]}};"
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { fmt.Fprint(w, body) }))
	defer server.Close()
	old := searchEndpoint
	searchEndpoint = server.URL
	defer func() { searchEndpoint = old }()
	tracks, err := Search(context.Background(), "song", 1)
	if err != nil {
		t.Fatal(err)
	}
	if len(tracks) != 1 || tracks[0].ID != "one" {
		t.Fatalf("unexpected search tracks: %#v", tracks)
	}
}

func TestExtractInitialDataHandlesNestedObjectsAndQuotedBraces(t *testing.T) {
	got := extractInitialData([]byte("var ytInitialData = {\"a\":{\"b\":\"} { { }\"}};"))
	var value map[string]any
	if err := json.Unmarshal(got, &value); err != nil {
		t.Fatalf("extracted invalid JSON %q: %v", got, err)
	}
	if value["a"].(map[string]any)["b"] != "} { { }" {
		t.Fatalf("unexpected JSON payload: %q", value)
	}
}

func TestParsesLiveVideoWithContextRendererAndDeduplicatesInInputOrder(t *testing.T) {
	body := `var ytInitialData = {"contents":{"items":[
		{"videoWithContextRenderer":{"videoId":"one","headline":{"runs":[{"text":"First"}]},"shortBylineText":{"runs":[{"text":"Artist One"}]},"lengthText":{"simpleText":"2:03"},"thumbnail":{"thumbnails":[{"url":"https://img.example/one.jpg"}]}}},
		{"videoWithContextRenderer":{"videoId":"one","headline":{"simpleText":"Duplicate"}}},
		{"videoRenderer":{"videoId":"two","title":{"simpleText":"Second"}}}
	]}};`
	payload := extractInitialData([]byte(body))
	tracks := make([]Track, 0, 3)
	collectVideoRenderers(payload, &tracks, 30)
	if len(tracks) != 2 || tracks[0].ID != "one" || tracks[1].ID != "two" {
		t.Fatalf("unexpected ordered results: %#v", tracks)
	}
	if tracks[0].Title != "First" || tracks[0].Artist != "Artist One" || tracks[0].Duration != 123_000 {
		t.Fatalf("unexpected context renderer fields: %#v", tracks[0])
	}
}

func TestAudioFormatPrefersStandaloneAudioAndUsesMuxedAsFallback(t *testing.T) {
	all := youtube.FormatList{
		{ItagNo: 18, MimeType: `video/mp4; codecs="avc1, mp4a"`, AudioChannels: 2, Width: 640, Height: 360},
		{ItagNo: 140, MimeType: `audio/mp4; codecs="mp4a"`, AudioChannels: 2},
	}
	got, err := BestAudioFormat(all)
	if err != nil || got.ItagNo != 140 {
		t.Fatalf("expected standalone audio format, got %#v, %v", got, err)
	}
	got, err = BestAudioFormat(all[:1])
	if err != nil || got.ItagNo != 18 {
		t.Fatalf("expected muxed fallback, got %#v, %v", got, err)
	}
}

func TestSearchRejectsMalformedOrOversizedResponse(t *testing.T) {
	for _, body := range []string{"not a results page", "var ytInitialData = {\"x\":\"" + strings.Repeat("a", maxSearchBytes) + "\"};"} {
		server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) { _, _ = w.Write([]byte(body)) }))
		old := searchEndpoint
		searchEndpoint = server.URL
		_, err := Search(context.Background(), "song", 5)
		searchEndpoint = old
		server.Close()
		if err == nil {
			t.Fatal("expected invalid response error")
		}
	}
}

func TestResolveRejectsNonYouTubeURL(t *testing.T) {
	if _, err := Resolve(context.Background(), "https://example.com/video"); err == nil {
		t.Fatal("expected unsupported address")
	}
}

func TestDownloadResolvedUsesResolvedURLHeadersAndRanges(t *testing.T) {
	var gotRange string
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		gotRange = request.URL.Query().Get("range")
		if request.URL.Host != "r1.googlevideo.com" || request.URL.Query().Get("sig") != "signed" {
			t.Fatalf("request lost the resolved URL: %s", request.URL)
		}
		if request.Header.Get("Cookie") != "CONSENT=YES+cb.20210328-17-p0.en+FX+234" ||
			request.Header.Get("User-Agent") == "" || request.Header.Get("Accept-Encoding") != "identity" {
			t.Fatalf("request did not preserve stream headers: %#v", request.Header)
		}
		body := []byte("audio")
		return &http.Response{
			StatusCode:    http.StatusOK,
			Header:        http.Header{"Content-Length": []string{strconv.Itoa(len(body))}},
			Body:          io.NopCloser(strings.NewReader(string(body))),
			Request:       request,
			ContentLength: int64(len(body)),
		}, nil
	})}
	base := filepath.Join(t.TempDir(), "track")
	progress := -1.0
	path, err := downloadResolvedWithClient(context.Background(), ResolvedStream{
		URL: "https://r1.googlevideo.com/videoplayback?sig=signed", ContentLength: 5,
		ConsentCookie: "CONSENT=YES+cb.20210328-17-p0.en+FX+234", MIMEType: `audio/mp4; codecs="mp4a.40.2"`, ItagNo: 140,
	}, base, func(value float64) { progress = value }, client)
	if err != nil {
		t.Fatal(err)
	}
	if gotRange != "0-4" {
		t.Fatalf("unexpected requested range %q", gotRange)
	}
	if filepath.Ext(path) != ".m4a" || progress != 1 {
		t.Fatalf("unexpected output metadata: path=%q progress=%v", path, progress)
	}
	got, err := os.ReadFile(path)
	if err != nil || string(got) != "audio" {
		t.Fatalf("unexpected output bytes %q: %v", got, err)
	}
}

func TestDownloadResolvedRejectsHTTPFailureWithoutPublishingFile(t *testing.T) {
	client := &http.Client{Transport: roundTripFunc(func(request *http.Request) (*http.Response, error) {
		return &http.Response{
			StatusCode: http.StatusForbidden, Header: make(http.Header),
			Body: io.NopCloser(strings.NewReader("blocked")), Request: request,
		}, nil
	})}
	base := filepath.Join(t.TempDir(), "track")
	_, err := downloadResolvedWithClient(context.Background(), ResolvedStream{
		URL: "https://r1.googlevideo.com/videoplayback?sig=signed", ContentLength: 5,
		ConsentCookie: "CONSENT=YES+cb.20210328-17-p0.en+FX+234", MIMEType: "audio/mp4", ItagNo: 140,
	}, base, nil, client)
	if err == nil || !strings.Contains(err.Error(), "HTTP 403") {
		t.Fatalf("expected an HTTP 403 error, got %v", err)
	}
	if _, statErr := os.Stat(base + ".m4a"); !os.IsNotExist(statErr) {
		t.Fatalf("failed transfer published output: %v", statErr)
	}
}

type roundTripFunc func(*http.Request) (*http.Response, error)

func (f roundTripFunc) RoundTrip(request *http.Request) (*http.Response, error) { return f(request) }

func TestNewConsentCookieMatchesYtdlLibShape(t *testing.T) {
	cookie, err := newConsentCookie()
	if err != nil {
		t.Fatal(err)
	}
	if !strings.HasPrefix(cookie, consentCookiePrefix) {
		t.Fatalf("cookie has unexpected prefix %q", cookie)
	}
	suffix, err := strconv.Atoi(strings.TrimPrefix(cookie, consentCookiePrefix))
	if err != nil || suffix < 100 || suffix > 998 {
		t.Fatalf("cookie suffix is outside ytdl-lib range: %q (%v)", cookie, err)
	}
}
