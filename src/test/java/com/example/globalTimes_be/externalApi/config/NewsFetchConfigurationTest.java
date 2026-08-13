package com.example.globalTimes_be.externalApi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NewsFetchConfigurationTest {

    @Test
    void allCollectorsAreDisabledByDefault() throws IOException {
        ConfigurableEnvironment environment = applicationEnvironment();

        assertThat(environment.getProperty("news-fetch.news-api-enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("news-fetch.rss-enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("news-fetch.trend-enabled", Boolean.class)).isFalse();
    }

    @Test
    void legacyGlobalFlagStillEnablesAllCollectors() throws IOException {
        ConfigurableEnvironment environment = applicationEnvironment("NEWS_FETCH_ENABLED=true");

        assertThat(environment.getProperty("news-fetch.news-api-enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("news-fetch.rss-enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("news-fetch.trend-enabled", Boolean.class)).isTrue();
    }

    @Test
    void providerFlagsAndRssLimitsOverrideTheLegacyFlag() throws IOException {
        ConfigurableEnvironment environment = applicationEnvironment(
                "NEWS_FETCH_ENABLED=true",
                "NEWS_API_FETCH_ENABLED=false",
                "RSS_FETCH_ENABLED=true",
                "TREND_FETCH_ENABLED=false",
                "RSS_FETCH_COUNTRIES=kr",
                "RSS_MAX_FEEDS_PER_RUN=1",
                "RSS_MAX_ARTICLES_PER_FEED=2"
        );

        assertThat(environment.getProperty("news-fetch.news-api-enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("news-fetch.rss-enabled", Boolean.class)).isTrue();
        assertThat(environment.getProperty("news-fetch.trend-enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("news-fetch.rss-countries")).isEqualTo("kr");
        assertThat(environment.getProperty("news-fetch.rss-max-feeds-per-run", Integer.class)).isEqualTo(1);
        assertThat(environment.getProperty("news-fetch.rss-max-articles-per-feed", Integer.class)).isEqualTo(2);
    }

    private ConfigurableEnvironment applicationEnvironment(String... properties) throws IOException {
        MockEnvironment environment = new MockEnvironment();
        for (String property : properties) {
            int separator = property.indexOf('=');
            environment.setProperty(property.substring(0, separator), property.substring(separator + 1));
        }

        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(
                "application", new ClassPathResource("application.yml"));
        sources.forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
