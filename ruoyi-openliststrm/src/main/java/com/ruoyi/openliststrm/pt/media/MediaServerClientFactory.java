package com.ruoyi.openliststrm.pt.media;

import com.ruoyi.openliststrm.mybatisplus.domain.PtMediaServerPlus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按 pt_media_server.type 分发到具体的媒体服务器实现。
 * <p>
 * Jellyfin 与 Emby 的相关接口完全兼容，故 JELLYFIN 复用 EmbyClient。
 * 未来接入 Plex 等异构服务器时，实现 IMediaServerClient 并注册为 Bean 即可。
 * </p>
 *
 * @author Jack
 */
@Component
public class MediaServerClientFactory {

    private final Map<String, IMediaServerClient> clients;
    private final EmbyClient embyClient;

    public MediaServerClientFactory(List<IMediaServerClient> clientList, EmbyClient embyClient) {
        this.clients = clientList.stream()
                .collect(Collectors.toMap(IMediaServerClient::type, Function.identity()));
        this.embyClient = embyClient;
    }

    /**
     * @throws IllegalArgumentException 配置的类型没有对应实现
     */
    public IMediaServerClient get(PtMediaServerPlus config) {
        if ("JELLYFIN".equals(config.getType())) {
            return embyClient;
        }
        IMediaServerClient client = clients.get(config.getType());
        if (client == null) {
            throw new IllegalArgumentException("不支持的媒体服务器类型：" + config.getType());
        }
        return client;
    }
}
