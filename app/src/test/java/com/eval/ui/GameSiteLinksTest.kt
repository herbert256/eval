package com.eval.ui

import org.junit.Assert.*
import org.junit.Test

class GameSiteLinksTest {
    @Test fun web_hosts_are_case_insensitive_and_support_real_subdomains() {
        assertEquals("lichess.org", gameSiteHost("HTTPS://LICHESS.ORG/abc"))
        assertEquals("lichess.org", gameSiteHost("https://www.lichess.org/game/live/123"))
        assertEquals("lichess.org", gameSiteHost("http://lichess.org/game/live/123"))
    }

    @Test fun invalid_schemes_and_lookalike_hosts_are_not_game_links() {
        listOf("lichess.org/abc", "javascript:lichess.org", "ftp://lichess.org/123",
            "https://unrelated.example/game/123", "https://lichess.org.example.com/abc", "https://example.com/lichess.org",
            "https://lichess.org@elsewhere.example/123", "https://someone@lichess.org/abc",
            "https://lichess.org/a b", "//lichess.org/123", "?").forEach {
            assertNull(it, gameSiteHost(it))
        }
    }

    @Test fun pgn_links_preserve_the_path_query_and_fragment() {
        val url = "https://lichess.org/AbCd1234/black?theme=dark#12"
        assertEquals(url, gameSiteUrl("[Site \" $url \"]\n\n*"))
        assertNull(gameSiteUrl("[Event \"Local game\"]\n\n*"))
    }
}
