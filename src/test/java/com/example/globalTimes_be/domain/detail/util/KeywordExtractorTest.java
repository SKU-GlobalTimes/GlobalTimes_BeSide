package com.example.globalTimes_be.domain.detail.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordExtractorTest {

    @Test
    void extract_filtersGenericLeadingTokensForIssueSearch() {
        String title = "First round of US-Iran talks ends with encouraging progress";

        assertThat(KeywordExtractor.extractPlain(title))
                .isEqualTo("Iran talks ends encouraging");
        assertThat(KeywordExtractor.extract(title))
                .isEqualTo("+Iran +talks ends encouraging");
    }

    @Test
    void extract_keepsStrongEntityKeywordForBtsSample() {
        String title = "The BTS fans losing thousands as scammers cash in on comeback tour ticket war";

        assertThat(KeywordExtractor.extractPlain(title))
                .isEqualTo("BTS fans losing thousands");
        assertThat(KeywordExtractor.extract(title))
                .isEqualTo("+BTS +fans losing thousands");
    }

    @Test
    void extract_splitsHyphenatedEnglishWordsIntoSeparateTokens() {
        String title = "Trump-backed political outsider wins Colombia election";

        assertThat(KeywordExtractor.extractPlain(title))
                .isEqualTo("Trump backed political outsider");
        assertThat(KeywordExtractor.extract(title))
                .isEqualTo("+Trump +backed political outsider");
    }

    @Test
    void extract_keepsKoreanTokenJoinedByUnicodeEllipsis() {
        String title = "AI 반도체…대만 하이맥스 급등";

        assertThat(KeywordExtractor.extractPlain(title))
                .isEqualTo("반도체…대만 하이맥스");
        assertThat(KeywordExtractor.extract(title))
                .isEqualTo("+반도체…대만 +하이맥스");
    }

    @Test
    void extract_filtersFrenchGeneralTokenFromSample() {
        String title = "Entre Meloni et Trump, divorce a l'italienne";

        assertThat(KeywordExtractor.extractPlain(title))
                .isEqualTo("Meloni Trump divorce italienne");
        assertThat(KeywordExtractor.extract(title))
                .isEqualTo("+Meloni +Trump divorce italienne");
    }

    @Test
    void extract_returnsEmptyStringForNullOrBlankTitle() {
        assertThat(KeywordExtractor.extract(null)).isEmpty();
        assertThat(KeywordExtractor.extract("   ")).isEmpty();
        assertThat(KeywordExtractor.extractPlain(null)).isEmpty();
        assertThat(KeywordExtractor.extractPlain("   ")).isEmpty();
    }
}
