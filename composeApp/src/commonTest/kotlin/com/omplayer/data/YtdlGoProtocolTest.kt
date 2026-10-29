package com.omplayer.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class YtdlGoProtocolTest {
    @Test fun parsesSearchTracksAndKeepsThePersistedSourceId() {
        val response = parseYtdlGoMessage(
            """{"type":"search","tracks":[{"id":"abc123","title":"Track","artist":"Artist","duration_ms":185000,"artwork_url":"https://img.example/cover.jpg","stream_url":"https://youtube.com/watch?v=abc123","page_url":"https://youtube.com/watch?v=abc123","can_download":true,"source_id":"other","needs_resolve":true}]}""",
        )

        val track = assertIs<YtdlGoMessage.Search>(response).tracks.single()
        assertEquals("abc123", track.id)
        assertEquals("Track", track.title)
        assertEquals("Artist", track.artist)
        assertEquals(185_000L, track.durationMs)
        assertEquals("https://img.example/cover.jpg", track.artworkUrl)
        assertEquals("https://youtube.com/watch?v=abc123", track.pageUrl)
        assertEquals("ytdlp", track.sourceId)
        assertTrue(track.canDownload)
        assertTrue(track.needsResolve)
    }

    @Test fun dropsInvalidSearchEntriesAndDefaultsOptionalMetadata() {
        val response = parseYtdlGoMessage(
            """{"type":"search","tracks":[{"title":"missing id","page_url":"https://youtube.com/watch?v=1"},{"id":"unsafe","page_url":"file:///tmp/a"},{"id":"ok","title":" ","page_url":"https://youtube.com/watch?v=ok","duration_ms":-2}]}""",
        )

        val track = assertIs<YtdlGoMessage.Search>(response).tracks.single()
        assertEquals("ok", track.id)
        assertEquals("Untitled", track.title)
        assertEquals(0L, track.durationMs)
        assertEquals("https://youtube.com/watch?v=ok", track.streamUrl)
    }

    @Test fun parsesResolveDownloadProgressAndFailures() {
        assertEquals(
            "https://cdn.example/audio.m4a",
            assertIs<YtdlGoMessage.Resolve>(
                parseYtdlGoMessage(
                    """{"type":"resolve","url":"https://cdn.example/audio.m4a","content_length":3145728,"consent_cookie":"CONSENT=YES+cb.20210328-17-p0.en+FX+234","mime_type":"audio/mp4; codecs=\"mp4a.40.2\"","itag":140}""",
                ),
            ).url,
        )
        val resolved = assertIs<YtdlGoMessage.Resolve>(
            parseYtdlGoMessage(
                """{"type":"resolve","url":"https://cdn.example/audio.m4a","content_length":3145728,"consent_cookie":"CONSENT=YES+cb.20210328-17-p0.en+FX+234","mime_type":"audio/mp4; codecs=\"mp4a.40.2\"","itag":140}""",
            ),
        )
        assertEquals(3_145_728L, resolved.contentLength)
        assertEquals("CONSENT=YES+cb.20210328-17-p0.en+FX+234", resolved.consentCookie)
        assertEquals("audio/mp4; codecs=\"mp4a.40.2\"", resolved.mimeType)
        assertEquals(140, resolved.itag)
        assertEquals(1f, assertIs<YtdlGoMessage.Progress>(parseYtdlGoMessage("""{"type":"progress","progress":1.4}""")).value)
        assertEquals("/files/song.m4a", assertIs<YtdlGoMessage.Done>(parseYtdlGoMessage("""{"type":"done","path":"/files/song.m4a"}""")).path)
        assertEquals("blocked", assertIs<YtdlGoMessage.Error>(parseYtdlGoMessage("""{"type":"error","error":"blocked"}""")).message)
        val invalidResolve = assertIs<YtdlGoMessage.Resolve>(
            parseYtdlGoMessage(
                """{"type":"resolve","url":"http://cdn.example/audio.m4a","content_length":0,"consent_cookie":"CONSENT=bad","mime_type":"video/x-msvideo","itag":0}""",
            ),
        )
        assertNull(invalidResolve.url)
        assertNull(invalidResolve.contentLength)
        assertNull(invalidResolve.consentCookie)
        assertNull(invalidResolve.mimeType)
        assertNull(invalidResolve.itag)
    }

    @Test fun malformedOrUnknownMessagesAreIgnored() {
        assertNull(parseYtdlGoMessage(""))
        assertNull(parseYtdlGoMessage("not json"))
        assertNull(parseYtdlGoMessage("""{"type":"unknown"}"""))
        assertNull(parseYtdlGoMessage("""{"type":"progress","progress":"many"}"""))
    }

    @Test fun omittedSearchTracksMeansNoResults() {
        assertEquals(emptyList(), assertIs<YtdlGoMessage.Search>(parseYtdlGoMessage("""{"type":"search"}""")).tracks)
    }
}
