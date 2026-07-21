package com.ruoyi.openliststrm.pt.downloader;

import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
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

class QbittorrentClientTest {

    private MockWebServer server;
    private QbittorrentClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new QbittorrentClient(new OkHttpClient());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private PtDownloaderPlus config(int id) {
        PtDownloaderPlus c = new PtDownloaderPlus();
        c.setId(id);
        c.setName("qb");
        c.setType("QBITTORRENT");
        c.setHost(server.getHostName());
        c.setPort(server.getPort());
        c.setUseHttps("0");
        c.setUsername("admin");
        c.setPassword("adminadmin");
        c.setSavePath("/data/downloads");
        c.setTag("osr-pt");
        return c;
    }

    private MockResponse loginOk() {
        return new MockResponse().setBody("Ok.").addHeader("Set-Cookie", "SID=test-sid; path=/");
    }

    @Test
    void type_返回QBITTORRENT() {
        assertEquals("QBITTORRENT", client.type());
    }

    @Test
    void addTorrent_登录后提交种子_参数正确() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("Ok."));

        client.addTorrent(config(1), "https://pt.example.com/t.torrent", "/data/downloads", "osr-pt");

        RecordedRequest login = server.takeRequest();
        assertEquals("/api/v2/auth/login", login.getPath());
        assertTrue(login.getBody().readUtf8().contains("username=admin"));

        RecordedRequest add = server.takeRequest();
        assertEquals("/api/v2/torrents/add", add.getPath());
        assertEquals("SID=test-sid", add.getHeader("Cookie"));
        String body = add.getBody().readUtf8();
        assertTrue(body.contains("savepath=%2Fdata%2Fdownloads"));
        assertTrue(body.contains("tags=osr-pt"));
    }

    @Test
    void addTorrent_登录返回Fails_抛IOException() {
        server.enqueue(new MockResponse().setBody("Fails."));

        assertThrows(IOException.class,
                () -> client.addTorrent(config(2), "https://pt.example.com/t.torrent", "/data/downloads", "osr-pt"));
    }

    @Test
    void addTorrent_添加接口返回非Ok_抛IOException() {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("Fails."));

        assertThrows(IOException.class,
                () -> client.addTorrent(config(3), "https://pt.example.com/t.torrent", "/data/downloads", "osr-pt"));
    }

    @Test
    void listByTag_解析JSON为种子快照() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("""
                [
                  {"hash":"AABBCC","name":"Show.S01E01","progress":1.0,"state":"uploading","save_path":"/data/downloads"},
                  {"hash":"DDEEFF","name":"Show.S01E02","progress":0.35,"state":"downloading","save_path":"/data/downloads"}
                ]
                """));

        List<DownloaderTorrent> list = client.listByTag(config(4), "osr-pt");

        assertEquals(2, list.size());
        assertEquals("aabbcc", list.get(0).getHash());
        assertEquals("Show.S01E01", list.get(0).getName());
        assertTrue(list.get(0).isCompleted());
        assertEquals("uploading", list.get(0).getRawState());
        assertFalse(list.get(1).isCompleted());
    }

    @Test
    void listByTag_请求带上tag参数() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("[]"));

        client.listByTag(config(5), "osr-pt");

        server.takeRequest();
        RecordedRequest info = server.takeRequest();
        assertEquals("osr-pt", info.getRequestUrl().queryParameter("tag"));
    }

    @Test
    void listByTag_SID过期返回403_自动重新登录并重试成功() throws Exception {
        PtDownloaderPlus config = config(6);

        // 第一轮：登录 + 查询成功，SID 被缓存
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("[]"));
        client.listByTag(config, "osr-pt");
        server.takeRequest();
        server.takeRequest();

        // 第二轮：缓存的 SID 已过期 → 403 → 重新登录 → 重试成功
        server.enqueue(new MockResponse().setResponseCode(403));
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("""
                [{"hash":"AABBCC","name":"Show.S01E01","progress":1.0,"state":"uploading","save_path":"/data"}]
                """));

        List<DownloaderTorrent> list = client.listByTag(config, "osr-pt");

        assertEquals(1, list.size());
        assertEquals("/api/v2/torrents/info", server.takeRequest().getPath().split("\\?")[0]);
        assertEquals("/api/v2/auth/login", server.takeRequest().getPath());
        assertEquals("/api/v2/torrents/info", server.takeRequest().getPath().split("\\?")[0]);
    }

    @Test
    void listByTag_连续两次403_抛IOException不无限重试() {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setResponseCode(403));
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setResponseCode(403));

        assertThrows(IOException.class, () -> client.listByTag(config(7), "osr-pt"));
    }

    @Test
    void testConnection_版本接口正常_判定连通() throws Exception {
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("v4.6.2"));

        assertTrue(client.testConnection(config(8)));
    }

    @Test
    void testConnection_登录失败_判定不连通而非抛异常() {
        server.enqueue(new MockResponse().setBody("Fails."));

        assertFalse(client.testConnection(config(9)));
    }

    @Test
    void testConnection_地址不可达_判定不连通() throws IOException {
        PtDownloaderPlus config = config(10);
        server.shutdown();

        assertFalse(client.testConnection(config));
    }

    @Test
    void listByTag_响应体不是合法JSON_抛IOException而非JSONException() {
        // 模拟反向代理故障：返回一个 HTML 错误页而非 qB 的 JSON 数组
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody("<html><body>502 Bad Gateway</body></html>"));

        IOException ex = assertThrows(IOException.class, () -> client.listByTag(config(11), "osr-pt"));
        assertTrue(ex.getMessage().contains("不是合法 JSON"));
    }

    @Test
    void listByTag_响应体是超长非JSON文本_异常消息不整段塞入() {
        String huge = "x".repeat(5000);
        server.enqueue(loginOk());
        server.enqueue(new MockResponse().setBody(huge));

        IOException ex = assertThrows(IOException.class, () -> client.listByTag(config(12), "osr-pt"));
        assertTrue(ex.getMessage().length() < 500);
    }
}
