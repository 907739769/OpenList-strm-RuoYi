package com.ruoyi.openliststrm.pt.subscription;

import com.ruoyi.openliststrm.config.OpenlistConfig;
import com.ruoyi.openliststrm.pt.subscription.dto.TmdbSearchItem;
import com.ruoyi.openliststrm.tmdb.TMDbApiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TmdbSearchServiceTest {

    @Mock
    private TMDbApiService tmDbApiService;

    @Mock
    private OpenlistConfig openlistConfig;

    @InjectMocks
    private TmdbSearchService service;

    @BeforeEach
    void setUp() {
        // lenient：search_关键词为空 用例中关键词校验会在调用 TMDb 前直接返回，
        // 不会用到这个桩，严格模式下会被判定为多余 stubbing
        lenient().when(openlistConfig.getTmdbApiKey()).thenReturn("test-key");
    }

    @Test
    void search_剧集_取name与first_air_date() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("""
                        {"results":[
                          {"id":1396,"name":"绝命毒师","original_name":"Breaking Bad",
                           "first_air_date":"2008-01-20","poster_path":"/abc.jpg","overview":"简介"}
                        ]}
                        """);

        List<TmdbSearchItem> items = service.search("TV", "绝命毒师");

        assertEquals(1, items.size());
        TmdbSearchItem item = items.get(0);
        assertEquals("1396", item.getTmdbId());
        assertEquals("绝命毒师", item.getTitle());
        assertEquals("Breaking Bad", item.getOriginalTitle());
        assertEquals("2008", item.getYear());
        assertEquals("/abc.jpg", item.getPosterPath());
        assertEquals("TV", item.getMediaType());
    }

    @Test
    void search_电影_取title与release_date() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("""
                        {"results":[
                          {"id":550,"title":"搏击俱乐部","original_title":"Fight Club",
                           "release_date":"1999-10-15","poster_path":"/x.jpg"}
                        ]}
                        """);

        List<TmdbSearchItem> items = service.search("MOVIE", "搏击俱乐部");

        assertEquals("550", items.get(0).getTmdbId());
        assertEquals("搏击俱乐部", items.get(0).getTitle());
        assertEquals("Fight Club", items.get(0).getOriginalTitle());
        assertEquals("1999", items.get(0).getYear());
        assertEquals("MOVIE", items.get(0).getMediaType());
    }

    @Test
    void search_日期缺失或格式异常_年份为null而非抛异常() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("""
                        {"results":[
                          {"id":1,"name":"无日期","first_air_date":""},
                          {"id":2,"name":"缺字段"},
                          {"id":3,"name":"怪日期","first_air_date":"待定"}
                        ]}
                        """);

        List<TmdbSearchItem> items = service.search("TV", "x");

        assertEquals(3, items.size());
        assertNull(items.get(0).getYear());
        assertNull(items.get(1).getYear());
        assertNull(items.get(2).getYear());
    }

    @Test
    void search_空结果_返回空列表() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("{\"results\":[]}");

        assertTrue(service.search("TV", "不存在的剧").isEmpty());
    }

    @Test
    void search_响应无results字段_返回空列表而非NPE() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("{\"status_message\":\"Invalid API key\"}");

        assertTrue(service.search("TV", "x").isEmpty());
    }

    @Test
    void search_响应非法JSON_返回空列表而非抛异常() {
        when(tmDbApiService.search(anyString(), anyString(), anyString(), any()))
                .thenReturn("<html>error</html>");

        assertTrue(service.search("TV", "x").isEmpty());
    }

    @Test
    void search_关键词为空_直接返回空列表且不调TMDb() {
        assertTrue(service.search("TV", "  ").isEmpty());
    }

    @Test
    void getSeasonEpisodeCount_取指定季的集数() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"id":1396,"seasons":[
                          {"season_number":0,"episode_count":8},
                          {"season_number":1,"episode_count":7},
                          {"season_number":2,"episode_count":13}
                        ]}
                        """);

        assertEquals(7, service.getSeasonEpisodeCount("1396", 1));
        assertEquals(13, service.getSeasonEpisodeCount("1396", 2));
    }

    @Test
    void getSeasonEpisodeCount_季号0是特别篇_不是电影_同样能查到() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"seasons":[{"season_number":0,"episode_count":8},{"season_number":1,"episode_count":7}]}
                        """);

        assertEquals(8, service.getSeasonEpisodeCount("1396", 0));
    }

    @Test
    void getSeasonEpisodeCount_季不存在_抛IllegalArgumentException() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("{\"seasons\":[{\"season_number\":1,\"episode_count\":7}]}");

        assertThrows(IllegalArgumentException.class, () -> service.getSeasonEpisodeCount("1396", 99));
    }

    @Test
    void getSeasonEpisodeCount_响应无seasons_抛IllegalArgumentException() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("{\"id\":1396}");

        assertThrows(IllegalArgumentException.class, () -> service.getSeasonEpisodeCount("1396", 1));
    }

    @Test
    void getDetail_取标题年份海报() {
        when(tmDbApiService.getDetails(anyString(), anyString(), anyInt()))
                .thenReturn("""
                        {"id":1396,"name":"绝命毒师","original_name":"Breaking Bad",
                         "first_air_date":"2008-01-20","poster_path":"/abc.jpg"}
                        """);

        TmdbSearchItem detail = service.getDetail("TV", "1396");

        assertEquals("绝命毒师", detail.getTitle());
        assertEquals("2008", detail.getYear());
        assertEquals("/abc.jpg", detail.getPosterPath());
    }
}
