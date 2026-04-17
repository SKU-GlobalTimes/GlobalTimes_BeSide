package com.example.globalTimes_be.bootstrap;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * 프로젝트 루트 {@code .env}를 로드해 Spring이 읽는 {@link System#setProperty}에 넣는다.
 * Gradle {@code bootRun}·IDE 모두에서 동일하게 동작하도록 {@code GlobalTimesBeApplication} 기동 전에 호출한다.
 * <p>
 * Spring Boot는 {@code .env} 파일을 자동으로 읽지 않는다. {@code docker compose}는 {@code env_file}로 주입하지만,
 * 로컬에서 {@code ./gradlew bootRun}만 할 때는 이 클래스가 없으면 JDBC URL이 비어 Hibernate가 실패한다.
 */
public final class DotenvBootstrap {

    private DotenvBootstrap() {
    }

    public static void apply() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        applyDataSource(dotenv);
        setIfUnset("gemini.api-key", dotenv.get("GEMINI_API_KEY"));
        setIfUnset("spring.google.api-key", dotenv.get("GOOGLE_API_KEY"));
        setIfUnset("spring.newsapi.api-key", dotenv.get("NEWS_API_KEY"));
        setIfUnset("spring.openai.api-key", dotenv.get("OPENAI_API_KEY"));
        setIfUnset("jwt.secret", dotenv.get("JWT_SECRET"));
        setIfUnset("oauth2.redirect-uri", dotenv.get("OAUTH2_REDIRECT_URI"));

        setIfUnset(
                "spring.security.oauth2.client.registration.google.client-id",
                dotenv.get("GOOGLE_OAUTH_CLIENT_ID")
        );
        setIfUnset(
                "spring.security.oauth2.client.registration.google.client-secret",
                dotenv.get("GOOGLE_OAUTH_CLIENT_SECRET")
        );

        setIfUnset(
                "spring.data.redis.host",
                firstNonBlank(dotenv.get("SPRING_DATA_REDIS_HOST"), "localhost")
        );
        setIfUnset(
                "spring.data.redis.port",
                firstNonBlank(dotenv.get("SPRING_DATA_REDIS_PORT"), "6379")
        );
    }

    private static void applyDataSource(Dotenv dotenv) {
        if (hasDataSourceAlready()) {
            return;
        }

        String jdbcUrl = dotenv.get("SPRING_DATASOURCE_URL");
        if (isNotBlank(jdbcUrl)) {
            System.setProperty("spring.datasource.url", jdbcUrl);
            setIfUnset(
                    "spring.datasource.username",
                    firstNonBlank(
                            dotenv.get("SPRING_DATASOURCE_USERNAME"),
                            dotenv.get("LOCAL_MYSQL_USER"),
                            "root"
                    )
            );
            setIfUnset(
                    "spring.datasource.password",
                    firstNonBlank(
                            dotenv.get("SPRING_DATASOURCE_PASSWORD"),
                            dotenv.get("LOCAL_MYSQL_PASSWORD"),
                            dotenv.get("MYSQL_ROOT_PASSWORD")
                    )
            );
            return;
        }

        String db = firstNonBlank(
                dotenv.get("LOCAL_MYSQL_DATABASE"),
                dotenv.get("MYSQL_DATABASE"),
                "globaltimes"
        );
        String user = firstNonBlank(dotenv.get("LOCAL_MYSQL_USER"), "root");
        String pass = firstNonBlank(
                dotenv.get("LOCAL_MYSQL_PASSWORD"),
                dotenv.get("MYSQL_ROOT_PASSWORD"),
                ""
        );

        String url = String.format(
                "jdbc:mysql://localhost:3306/%s?useSSL=false&useUnicode=true&serverTimezone=Asia/Seoul&allowPublicKeyRetrieval=true",
                db
        );

        System.setProperty("spring.datasource.url", url);
        System.setProperty("spring.datasource.username", user);
        System.setProperty("spring.datasource.password", pass);
    }

    private static boolean hasDataSourceAlready() {
        if (isNotBlank(System.getProperty("spring.datasource.url"))) {
            return true;
        }
        return isNotBlank(System.getenv("SPRING_DATASOURCE_URL"));
    }

    private static void setIfUnset(String springKey, String value) {
        if (isBlank(value)) {
            return;
        }
        if (isNotBlank(System.getProperty(springKey))) {
            return;
        }
        System.setProperty(springKey, value);
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return "";
        }
        for (String s : candidates) {
            if (isNotBlank(s)) {
                return s;
            }
        }
        return "";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static boolean isNotBlank(String s) {
        return !isBlank(s);
    }
}
