package com.ruoyi.openliststrm.pt.indexer;

import com.ruoyi.openliststrm.mybatisplus.domain.PtIndexerPlus;
import com.ruoyi.openliststrm.pt.model.TorrentInfo;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TorznabClientTest {

    private MockWebServer server;
    private TorznabClient client;

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:torznab="http://torznab.com/schemas/2015/feed">
              <channel>
                <item>
                  <title>Some.Show.S01E05.1080p.WEB-DL</title>
                  <link>https://pt.example.com/download.php?id=1</link>
                  <size>2147483648</size>
                  <torznab:attr name="seeders" value="12"/>
                </item>
              </channel>
            </rss>
            """;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new TorznabClient(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private PtIndexerPlus indexer(String categories) {
        PtIndexerPlus i = new PtIndexerPlus();
        i.setId(7);
        i.setName("测试索引器");
        i.setUrl(server.url("/api/v2.0/indexers/test/results/torznab/api").toString());
        i.setApiKey("secret-key");
        i.setCategories(categories);
        return i;
    }

    @Test
    void fetch_正常响应_返回解析结果并带上索引器ID() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        List<TorrentInfo> list = client.fetch(indexer("5000,5030"));

        assertEquals(1, list.size());
        assertEquals("Some.Show.S01E05.1080p.WEB-DL", list.get(0).getTitle());
        assertEquals(7, list.get(0).getIndexerId());
    }

    @Test
    void fetch_请求参数正确() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.fetch(indexer("5000,5030"));

        RecordedRequest request = server.takeRequest();
        assertEquals("secret-key", request.getRequestUrl().queryParameter("apikey"));
        assertEquals("search", request.getRequestUrl().queryParameter("t"));
        assertEquals("5000,5030", request.getRequestUrl().queryParameter("cat"));
    }

    @Test
    void fetch_分类为空_不带cat参数() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.fetch(indexer(null));

        RecordedRequest request = server.takeRequest();
        assertEquals(null, request.getRequestUrl().queryParameter("cat"));
    }

    @Test
    void fetch_HTTP错误码_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(IOException.class, () -> client.fetch(indexer(null)));
    }

    @Test
    void fetch_响应非XML_抛IllegalArgumentException() {
        server.enqueue(new MockResponse().setBody("<html>not xml"));

        assertThrows(IllegalArgumentException.class, () -> client.fetch(indexer(null)));
    }

    @Test
    void testConnection_caps接口返回正常_判定连通() throws Exception {
        server.enqueue(new MockResponse().setBody("<caps><server title=\"Jackett\"/></caps>"));

        assertTrue(client.testConnection(indexer(null)));

        RecordedRequest request = server.takeRequest();
        assertEquals("caps", request.getRequestUrl().queryParameter("t"));
    }

    @Test
    void testConnection_返回401_判定不连通() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertFalse(client.testConnection(indexer(null)));
    }

    @Test
    void testConnection_地址不可达_判定不连通而非抛异常() throws IOException {
        server.shutdown();

        assertFalse(client.testConnection(indexer(null)));
    }

    @Test
    void search_请求参数正确带上关键词与分类() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.search(indexer("5000,5030"), "Some Show S02");

        RecordedRequest request = server.takeRequest();
        assertEquals("search", request.getRequestUrl().queryParameter("t"));
        assertEquals("Some Show S02", request.getRequestUrl().queryParameter("q"));
        assertEquals("5000,5030", request.getRequestUrl().queryParameter("cat"));
        assertEquals("secret-key", request.getRequestUrl().queryParameter("apikey"));
    }

    @Test
    void search_正常响应_返回解析结果并带上索引器ID() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        List<TorrentInfo> list = client.search(indexer(null), "Some Show");

        assertEquals(1, list.size());
        assertEquals("Some.Show.S01E05.1080p.WEB-DL", list.get(0).getTitle());
        assertEquals(7, list.get(0).getIndexerId());
    }

    @Test
    void search_HTTP错误码_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(IOException.class, () -> client.search(indexer(null), "kw"));
    }

    @Test
    void getCaps_正常响应_解析出能力() throws Exception {
        server.enqueue(new MockResponse().setBody("""
                <caps>
                  <searching>
                    <movie-search available="yes" supportedParams="q,imdbid,tmdbid"/>
                  </searching>
                </caps>
                """));

        IndexerCapability cap = client.getCaps(indexer(null));

        assertTrue(cap.movieImdbSupported());
        assertTrue(cap.movieTmdbSupported());
    }

    @Test
    void getCaps_请求异常_返回NONE而不抛异常() throws IOException {
        server.shutdown();

        assertEquals(IndexerCapability.NONE, client.getCaps(indexer(null)));
    }

    @Test
    void getCaps_HTTP错误码_返回NONE而不抛异常() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertEquals(IndexerCapability.NONE, client.getCaps(indexer(null)));
    }

    @Test
    void searchByExternalId_电影按imdbid拼URL_不带season和ep() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.searchByExternalId(indexer("5000"), true, "imdbid", "tt1160419", null, null);

        RecordedRequest request = server.takeRequest();
        assertEquals("movie", request.getRequestUrl().queryParameter("t"));
        assertEquals("tt1160419", request.getRequestUrl().queryParameter("imdbid"));
        assertEquals("5000", request.getRequestUrl().queryParameter("cat"));
        assertEquals(null, request.getRequestUrl().queryParameter("season"));
        assertEquals(null, request.getRequestUrl().queryParameter("ep"));
    }

    @Test
    void searchByExternalId_剧集季包按season搜索_不带ep() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.searchByExternalId(indexer(null), false, "tmdbid", "1396", 1, null);

        RecordedRequest request = server.takeRequest();
        assertEquals("tvsearch", request.getRequestUrl().queryParameter("t"));
        assertEquals("1396", request.getRequestUrl().queryParameter("tmdbid"));
        assertEquals("1", request.getRequestUrl().queryParameter("season"));
        assertEquals(null, request.getRequestUrl().queryParameter("ep"));
    }

    @Test
    void searchByExternalId_剧集单集带season和ep() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        client.searchByExternalId(indexer(null), false, "imdbid", "tt0903747", 1, 3);

        RecordedRequest request = server.takeRequest();
        assertEquals("1", request.getRequestUrl().queryParameter("season"));
        assertEquals("3", request.getRequestUrl().queryParameter("ep"));
    }

    @Test
    void searchByExternalId_正常响应_返回解析结果并带上索引器ID() throws Exception {
        server.enqueue(new MockResponse().setBody(SAMPLE_XML));

        List<TorrentInfo> list = client.searchByExternalId(indexer(null), true, "imdbid", "tt1160419", null, null);

        assertEquals(1, list.size());
        assertEquals(7, list.get(0).getIndexerId());
    }

    @Test
    void searchByExternalId_HTTP错误码_抛IOException() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        assertThrows(IOException.class, () -> client.searchByExternalId(indexer(null), true, "imdbid", "tt1", null, null));
    }
}
