package com.example.globalTimes_be.domain.detail.util;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class KeywordExtractor {

    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
            "in", "on", "at", "for", "to", "of", "and", "or", "but", "nor",
            "with", "by", "from", "as", "into", "through", "about", "against",
            "that", "this", "these", "those", "it", "its", "their", "they",
            "has", "have", "had", "will", "would", "could", "should", "may", "might",
            "not", "no", "so", "yet", "both", "either", "up", "out", "over",
            "says", "said", "new", "after", "before", "more", "than", "also",
            "what", "how", "when", "where", "who", "which", "can", "do", "did",
            "he", "she", "we", "you", "his", "her", "our", "your"
    );

    private static final int MAX_KEYWORDS = 4;

    /**
     * 기사 제목에서 stop words를 제거하고 핵심 키워드를 추출한다.
     * GPT 호출 없이 서버에서 직접 처리.
     *
     * @param title 기사 제목
     * @return FULLTEXT BOOLEAN MODE용 키워드 문자열 (예: "+Trump +tariffs China")
     */
    public static String extract(String title) {
        if (title == null || title.isBlank()) return "";

        List<String> keywords = Arrays.stream(title.split("[\\s\\p{Punct}]+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word.toLowerCase()))
                .distinct()
                .limit(MAX_KEYWORDS)
                .collect(Collectors.toList());

        if (keywords.isEmpty()) return title;

        // 첫 2개는 필수(+), 나머지는 선택 → FULLTEXT 정확도 향상
        return formatBooleanModeKeywords(keywords);
    }

    // 로깅/응답용 평문 키워드 (+ 기호 없이)
    public static String extractPlain(String title) {
        if (title == null || title.isBlank()) return "";

        return Arrays.stream(title.split("[\\s\\p{Punct}]+"))
                .filter(word -> word.length() > 2)
                .filter(word -> !STOP_WORDS.contains(word.toLowerCase()))
                .distinct()
                .limit(MAX_KEYWORDS)
                .collect(Collectors.joining(" "));
    }

    private static String formatBooleanModeKeywords(List<String> keywords) {
        return java.util.stream.IntStream.range(0, keywords.size())
                .mapToObj(i -> i < 2 ? "+" + keywords.get(i) : keywords.get(i))
                .collect(Collectors.joining(" "));
    }
}
