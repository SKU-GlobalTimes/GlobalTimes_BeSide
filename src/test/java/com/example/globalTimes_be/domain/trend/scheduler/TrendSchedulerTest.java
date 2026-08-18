package com.example.globalTimes_be.domain.trend.scheduler;

import com.example.globalTimes_be.domain.trend.dto.resonse.TrendDTO;
import com.example.globalTimes_be.domain.trend.service.TrendService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class TrendSchedulerTest {

    private final RestTemplate restTemplate = mock(RestTemplate.class);
    private final TrendService trendService = mock(TrendService.class);
    private final TrendScheduler scheduler = new TrendScheduler(restTemplate, trendService);

    @Test
    void collectionEntryPoints_doNothingWhenTrendFetchIsDisabled() {
        ReflectionTestUtils.setField(scheduler, "fetchEnabled", false);

        scheduler.init();
        scheduler.saveTrendApi();

        org.mockito.Mockito.verifyNoInteractions(restTemplate, trendService);
    }

    @Test
    void saveTrendApi_limitsCountriesAndItemsPerCountry() {
        ReflectionTestUtils.setField(scheduler, "fetchEnabled", true);
        ReflectionTestUtils.setField(scheduler, "fetchCountries", "kr");
        ReflectionTestUtils.setField(scheduler, "maxItemsPerCountry", 1);
        when(restTemplate.getForObject(
                "https://trends.google.co.kr/trending/rss?geo=KR", String.class))
                .thenReturn(trendXml());

        scheduler.saveTrendApi();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrendDTO>> trends = ArgumentCaptor.forClass(List.class);
        verify(trendService).replaceTrendKeywords(org.mockito.ArgumentMatchers.eq("KR"), trends.capture());
        assertThat(trends.getValue()).hasSize(1);
        assertThat(trends.getValue().get(0).getKeyword()).isEqualTo("First trend");
        verify(restTemplate, times(1)).getForObject(
                "https://trends.google.co.kr/trending/rss?geo=KR", String.class);
        verifyNoMoreInteractions(restTemplate, trendService);
    }

    @Test
    void selectedCountries_normalizesAndRemovesDuplicates() {
        ReflectionTestUtils.setField(scheduler, "fetchCountries", "kr, US,kr");

        assertThat(scheduler.selectedCountries()).containsExactly("KR", "US");
    }

    private String trendXml() {
        return """
                <rss xmlns:ht="https://trends.google.com/trending/rss"><channel>
                  <item><title>First trend</title><ht:news_item>
                    <ht:news_item_title>First article</ht:news_item_title>
                    <ht:news_item_url>https://news.example/first</ht:news_item_url>
                    <ht:news_item_source>First source</ht:news_item_source>
                    <ht:news_item_picture>https://news.example/first.jpg</ht:news_item_picture>
                  </ht:news_item></item>
                  <item><title>Second trend</title><ht:news_item>
                    <ht:news_item_title>Second article</ht:news_item_title>
                    <ht:news_item_url>https://news.example/second</ht:news_item_url>
                    <ht:news_item_source>Second source</ht:news_item_source>
                    <ht:news_item_picture>https://news.example/second.jpg</ht:news_item_picture>
                  </ht:news_item></item>
                </channel></rss>
                """;
    }
}
