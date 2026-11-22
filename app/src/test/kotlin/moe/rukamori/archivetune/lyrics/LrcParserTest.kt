package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

    @Test
    fun `parseLyrics parses LRC line-synced lyrics`() {
        val lrc = "[00:12.34] Hello world"
        val parsed = LrcParser.parseLyrics(lrc)
        assertEquals(1, parsed.size)
        assertEquals(12340L, parsed[0].time)
        assertEquals("Hello world", parsed[0].text)
    }

    @Test
    fun `parseLyrics parses YRC word-synced lyrics`() {
        val yrc = "[123,456](0,100)Hello"
        val parsed = LrcParser.parseLyrics(yrc)
        assertEquals(1, parsed.size)
        assertEquals(123L, parsed[0].time)
        assertEquals("Hello", parsed[0].text)
    }

    @Test
    fun `parseTtml parses TTML lyrics`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:00:12.340" end="00:00:15.000">Hello TTML</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()
        val parsed = LrcParser.parseTtml(ttml)
        assertEquals(1, parsed.size)
        assertEquals(12340L, parsed[0].time)
        assertEquals("Hello TTML", parsed[0].text)
    }

    @Test
    fun `parseLyrics merges duplicate timestamps with translations`() {
        val lrc = """
            [00:12.34] Hello world
            [00:12.34] Привет мир
        """.trimIndent()
        val parsed = LrcParser.parseLyrics(lrc)
        assertEquals(1, parsed.size)
        assertEquals(12340L, parsed[0].time)
        assertEquals("Hello world", parsed[0].text)
        assertEquals("Привет мир", parsed[0].providerTranslationText)
    }

    @Test
    fun `insertInstrumentalBreaks inserts breaks correctly`() {
        val entries = listOf(
            LyricsEntry(time = 10000L, text = "First line"),
            LyricsEntry(time = 20000L, text = "Last line")
        )
        val withBreaks = LrcParser.insertInstrumentalBreaks(entries, 30000L)
        
        // Intro break: 10000 - 1000 = 9000ms (> 5000ms threshold)
        // Outro break: 30000 - (20000 + 2500) = 7500ms (> 5000ms threshold)
        
        assertEquals(4, withBreaks.size)
        
        assertTrue(withBreaks[0].isInstrumental)
        assertEquals(1000L, withBreaks[0].time)
        assertEquals(9000L, withBreaks[0].durationMs)
        
        assertEquals("First line", withBreaks[1].text)
        assertEquals("Last line", withBreaks[2].text)
        
        assertTrue(withBreaks[3].isInstrumental)
        assertEquals(22500L, withBreaks[3].time)
        assertEquals(7500L, withBreaks[3].durationMs)
    }

    @Test
    fun `parseLyrics estimates word timings with decimal fraction padding`() {
        // [00:10.49] should be parsed as 10490 ms (49 padded to 3 digits is 490)
        val lrc = """
            [00:10.49] Hello world
            [00:14.00] Next line
        """.trimIndent()
        val parsed = LrcParser.parseLyrics(lrc)
        assertEquals(2, parsed.size)
        assertEquals(10490L, parsed[0].time)
        assertNotNull(parsed[0].words)
        val words = parsed[0].words!!
        assertEquals(2, words.size)
        assertEquals(10.49, words[0].startTime, 0.001)
        assertTrue(words[0].endTime > words[0].startTime)
        assertEquals(words[0].endTime, words[1].startTime, 0.001)
        assertTrue(words[1].endTime <= 14.0)
    }

    @Test
    fun `estimator caps long instrumental gaps and does not stretch words`() {
        // Text length 11 -> maxSing = 11 * 120ms = 1320ms
        // Next line is 30 seconds away (instrumental gap)
        // end must be min(nextStart - 80ms, start + maxSing) = start + 1320ms = 11320ms
        val lrc = """
            [00:10.00] Hello world
            [00:40.00] Guitar solo ended
        """.trimIndent()
        val parsed = LrcParser.parseLyrics(lrc)
        val firstWords = parsed[0].words
        assertNotNull(firstWords)
        val lastWord = firstWords!!.last()
        assertEquals(11.32, lastWord.endTime, 0.001)
    }

    @Test
    fun `estimator weights words by length plus two`() {
        // "I" length 1 -> weight 3
        // "wonderful" length 9 -> weight 11
        // Total weight 14. Duration divided 3/14 vs 11/14.
        val lrc = """
            [00:00.00] I wonderful
            [00:10.00] End
        """.trimIndent()
        val parsed = LrcParser.parseLyrics(lrc)
        val words = parsed[0].words
        assertNotNull(words)
        val dur1 = words!![0].endTime - words[0].startTime
        val dur2 = words[1].endTime - words[1].startTime
        assertTrue("Longer word should have significantly longer duration", dur2 > dur1 * 3)
    }

    @Test
    fun `resolveSyncType correctly categorizes WORD, LINE, and PLAIN`() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body><div><p begin="00:00:12.340" end="00:00:15.000"><span begin="00:00:12.340" end="00:00:13.000">Word</span></p></div></body>
            </tt>
        """.trimIndent()
        val lrc = "[00:12.34] Hello world"
        val plain = "Just plain text\nNo timestamps here"

        assertEquals(LyricsSyncType.WORD, LrcParser.resolveSyncType(ttml, "BetterLyrics"))
        assertEquals(LyricsSyncType.WORD, LrcParser.resolveSyncType(ttml, "YouLyPlus"))
        assertEquals(LyricsSyncType.LINE, LrcParser.resolveSyncType(lrc, "LrcLib"))
        assertEquals(LyricsSyncType.LINE, LrcParser.resolveSyncType(lrc, "SimpMusic"))
        assertEquals(LyricsSyncType.LINE, LrcParser.resolveSyncType(lrc, "KuGou"))
        assertEquals(LyricsSyncType.PLAIN, LrcParser.resolveSyncType(plain, "Unison"))
        assertEquals(LyricsSyncType.PLAIN, LrcParser.resolveSyncType(lrc, "Unison"))
        assertEquals(LyricsSyncType.PLAIN, LrcParser.resolveSyncType(plain, "LrcLib"))
    }

    @Test
    fun `plain text never gets word timings`() {
        val plain = "Plain line 1\nPlain line 2"
        val parsed = LrcParser.parseLyrics(plain)
        assertTrue(parsed.isEmpty() || parsed.all { it.words == null })
    }
}
