package com.ruoyi.openliststrm.pt.downloader;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.openliststrm.mybatisplus.domain.PtDownloaderPlus;
import com.ruoyi.openliststrm.pt.downloader.model.DownloaderTorrent;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * qBittorrent Web API v2 客户端。
 * <p>
 * SID 按下载器 ID 缓存在内存中，遇到 403（会话过期）时自动重新登录并重试一次。
 * 进程重启后缓存丢失，首次调用重新登录即可，无需持久化。
 * </p>
 *
 * @author Jack
 */
@Slf4j
@Component
public class QbittorrentClient implements IDownloaderClient {

    private static final String TYPE = "QBITTORRENT";
    private static final String OK = "Ok.";

    private final OkHttpClient httpClient;

    /** downloaderId -> SID */
    private final Map<Integer, String> sidCache = new ConcurrentHashMap<>();

    public QbittorrentClient(OkHttpClient sharedOkHttpClient) {
        this.httpClient = sharedOkHttpClient;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public boolean testConnection(PtDownloaderPlus config) {
        try {
            String version = get(config, "/api/v2/app/version", Map.of());
            log.info("下载器[{}]连通，版本：{}", config.getName(), version);
            return true;
        } catch (Exception e) {
            log.warn("下载器[{}]连通性测试失败：{}", config.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public void addTorrent(PtDownloaderPlus config, String downloadUrl, String savePath, String tag) throws IOException {
        FormBody body = new FormBody.Builder()
                .add("urls", downloadUrl)
                .add("savepath", savePath)
                .add("tags", tag)
                .build();
        String response = post(config, "/api/v2/torrents/add", body);
        if (!OK.equalsIgnoreCase(response.trim())) {
            throw new IOException("qBittorrent 拒绝添加种子，响应：" + response);
        }
        log.info("已推送种子到下载器[{}]：{}", config.getName(), downloadUrl);
    }

    @Override
    public List<DownloaderTorrent> listByTag(PtDownloaderPlus config, String tag) throws IOException {
        String json = get(config, "/api/v2/torrents/info", Map.of("tag", tag));
        List<DownloaderTorrent> result = new ArrayList<>();
        if (StringUtils.isBlank(json)) {
            return result;
        }
        JSONArray array = JSONArray.parse(json);
        for (int i = 0; i < array.size(); i++) {
            JSONObject item = array.getJSONObject(i);
            DownloaderTorrent torrent = new DownloaderTorrent();
            String hash = item.getString("hash");
            torrent.setHash(hash == null ? null : hash.toLowerCase());
            torrent.setName(item.getString("name"));
            torrent.setProgress(item.getDoubleValue("progress"));
            torrent.setRawState(item.getString("state"));
            torrent.setSavePath(item.getString("save_path"));
            result.add(torrent);
        }
        return result;
    }

    // ---------- 内部：带会话管理的请求执行 ----------

    private String get(PtDownloaderPlus config, String path, Map<String, String> query) throws IOException {
        return executeWithSession(config, sid -> {
            HttpUrl.Builder builder = HttpUrl.parse(config.baseUrl() + path).newBuilder();
            query.forEach(builder::addQueryParameter);
            return new Request.Builder()
                    .url(builder.build())
                    .header("Cookie", "SID=" + sid)
                    .get()
                    .build();
        });
    }

    private String post(PtDownloaderPlus config, String path, RequestBody body) throws IOException {
        return executeWithSession(config, sid -> new Request.Builder()
                .url(config.baseUrl() + path)
                .header("Cookie", "SID=" + sid)
                .post(body)
                .build());
    }

    /**
     * 使用缓存 SID 执行请求；遇 403 视为会话过期，重新登录后重试一次。
     */
    private String executeWithSession(PtDownloaderPlus config, RequestFactory factory) throws IOException {
        String sid = sidCache.get(config.getId());
        if (sid == null) {
            sid = login(config);
        }

        try (Response response = httpClient.newCall(factory.build(sid)).execute()) {
            if (response.code() != 403) {
                return readSuccessful(response);
            }
        }

        // 403：会话过期，重新登录后重试一次
        sidCache.remove(config.getId());
        String freshSid = login(config);
        try (Response retry = httpClient.newCall(factory.build(freshSid)).execute()) {
            return readSuccessful(retry);
        }
    }

    private String readSuccessful(Response response) throws IOException {
        if (!response.isSuccessful()) {
            throw new IOException("qBittorrent 返回 HTTP " + response.code());
        }
        ResponseBody body = response.body();
        return body == null ? "" : body.string();
    }

    /**
     * 登录并缓存 SID。
     *
     * @throws IOException 凭据错误（响应体非 Ok.）或未返回 SID Cookie
     */
    private String login(PtDownloaderPlus config) throws IOException {
        FormBody body = new FormBody.Builder()
                .add("username", StringUtils.defaultString(config.getUsername(), ""))
                .add("password", StringUtils.defaultString(config.getPassword(), ""))
                .build();
        Request request = new Request.Builder()
                .url(config.baseUrl() + "/api/v2/auth/login")
                .header("Referer", config.baseUrl())
                .post(body)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String text = readSuccessful(response);
            // 注意：登录失败时 qBittorrent 同样返回 200，响应体为 Fails.
            if (!OK.equalsIgnoreCase(text.trim())) {
                throw new IOException("qBittorrent 登录失败，请检查用户名密码");
            }
            String sid = extractSid(response);
            if (sid == null) {
                throw new IOException("qBittorrent 登录成功但未返回 SID");
            }
            sidCache.put(config.getId(), sid);
            return sid;
        }
    }

    private String extractSid(Response response) {
        for (String cookie : response.headers("Set-Cookie")) {
            if (cookie.startsWith("SID=")) {
                int end = cookie.indexOf(';');
                return end > 0 ? cookie.substring(4, end) : cookie.substring(4);
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface RequestFactory {
        Request build(String sid);
    }
}
