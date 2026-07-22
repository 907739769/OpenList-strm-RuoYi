package com.ruoyi.openliststrm.pt.indexer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorznabCapsParserTest {

    @Test
    void parse_movie与tv均支持imdbid和tmdbid() {
        String xml = """
                <caps>
                  <searching>
                    <search available="yes" supportedParams="q"/>
                    <tv-search available="yes" supportedParams="q,season,ep,imdbid,tmdbid"/>
                    <movie-search available="yes" supportedParams="q,imdbid,tmdbid"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertTrue(cap.movieImdbSupported());
        assertTrue(cap.movieTmdbSupported());
        assertTrue(cap.tvImdbSupported());
        assertTrue(cap.tvTmdbSupported());
    }

    @Test
    void parse_只支持imdbid不支持tmdbid() {
        String xml = """
                <caps>
                  <searching>
                    <movie-search available="yes" supportedParams="q,imdbid"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertTrue(cap.movieImdbSupported());
        assertFalse(cap.movieTmdbSupported());
    }

    @Test
    void parse_available为no时视为不支持() {
        String xml = """
                <caps>
                  <searching>
                    <movie-search available="no" supportedParams="q,imdbid,tmdbid"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertFalse(cap.movieImdbSupported());
        assertFalse(cap.movieTmdbSupported());
    }

    @Test
    void parse_无searching节点_返回NONE() {
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse("<caps></caps>"));
    }

    @Test
    void parse_无movieSearch或tvSearch节点_对应能力为false() {
        String xml = """
                <caps>
                  <searching>
                    <search available="yes" supportedParams="q"/>
                  </searching>
                </caps>
                """;

        IndexerCapability cap = TorznabCapsParser.parse(xml);

        assertFalse(cap.movieImdbSupported());
        assertFalse(cap.tvImdbSupported());
    }

    @Test
    void parse_空字符串或null_返回NONE() {
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse(""));
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse(null));
    }

    @Test
    void parse_非法XML_返回NONE而不抛异常() {
        assertEquals(IndexerCapability.NONE, TorznabCapsParser.parse("<caps><searching>"));
    }
}
