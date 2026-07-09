import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ARTICLE_IDS = (__ENV.ARTICLE_IDS || '1')
  .split(',')
  .map((id) => id.trim())
  .filter((id) => id.length > 0);
const LANGUAGE = __ENV.LANGUAGE || '영어';
const SCENARIO_LABEL = __ENV.SCENARIO_LABEL || 'summary_baseline';
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

export const crawlerFallbackRate = new Rate('article_crawler_fallback');
export const summarySuccessRate = new Rate('article_summary_success');
export const summaryPayloadSize = new Trend('article_summary_payload_size');

export const options = {
  scenarios: {
    article_crawl_summary: {
      executor: 'constant-vus',
      exec: 'articleCrawlSummary',
      vus: Number(__ENV.ARTICLE_CRAWL_VUS || 1),
      duration: __ENV.ARTICLE_CRAWL_DURATION || '30s',
    },
  },
  thresholds: {
    'http_req_failed{api:article_crawl_summary}': ['rate<0.05'],
    'http_req_duration{api:article_crawl_summary}': ['p(95)<6000'],
  },
};

function query(params) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
}

function parseSummaryResponse(res) {
  try {
    const body = res.json();
    const message = body && typeof body.message === 'string' ? body.message : '';
    const data = body && body.data !== undefined && body.data !== null ? String(body.data) : '';

    return {
      parsed: true,
      isSuccess: body && body.isSuccess === true,
      isCrawlerFallback:
        res.status === 202 ||
        body && body.isSuccess === false && message.includes('크롤링'),
      payloadSize: data.length,
    };
  } catch (e) {
    return {
      parsed: false,
      isSuccess: false,
      isCrawlerFallback: false,
      payloadSize: 0,
    };
  }
}

export function articleCrawlSummary() {
  group('article crawl summary baseline', () => {
    for (const articleId of ARTICLE_IDS) {
      const params = query({ language: LANGUAGE });
      const res = http.get(`${BASE_URL}/api/ai/${articleId}/summary?${params}`, {
        tags: {
          api: 'article_crawl_summary',
          endpoint: 'summary',
          articleId,
          scenario: SCENARIO_LABEL,
        },
      });

      const summary = parseSummaryResponse(res);
      crawlerFallbackRate.add(summary.isCrawlerFallback, {
        articleId,
        scenario: SCENARIO_LABEL,
      });
      summarySuccessRate.add(summary.isSuccess, {
        articleId,
        scenario: SCENARIO_LABEL,
      });
      summaryPayloadSize.add(summary.payloadSize, {
        articleId,
        scenario: SCENARIO_LABEL,
      });

      check(res, {
        'status is not 5xx': (r) => r.status < 500,
        'response is ApiResponse JSON': () => summary.parsed,
        'response has handled summary outcome': () => summary.isSuccess || summary.isCrawlerFallback,
      });

      sleep(SLEEP_SECONDS);
    }
  });
}
