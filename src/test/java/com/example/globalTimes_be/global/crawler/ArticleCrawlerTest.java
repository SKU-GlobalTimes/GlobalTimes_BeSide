package com.example.globalTimes_be.global.crawler;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ArticleCrawlerTest {

    private final ArticleCrawler articleCrawler = new ArticleCrawler(5000, "test-agent");

    @Test
    void extractParagraphs_joinsNonBlankParagraphs() {
        Document doc = Jsoup.parse("""
                <html>
                    <body>
                        <p>first paragraph</p>
                        <p>   </p>
                        <p>second paragraph</p>
                    </body>
                </html>
                """);

        String result = articleCrawler.extractParagraphs(doc);

        assertThat(result).isEqualTo("first paragraph\nsecond paragraph");
    }

    @Test
    void extractParagraphs_returnsNullWhenParagraphsAreMissing() {
        Document doc = Jsoup.parse("<html><body><div>no article body</div></body></html>");

        String result = articleCrawler.extractParagraphs(doc);

        assertThat(result).isNull();
    }
}
