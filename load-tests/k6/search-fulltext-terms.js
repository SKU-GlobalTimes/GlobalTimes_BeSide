import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SEARCH_TERMS = (__ENV.SEARCH_TERMS || 'war,korea,economy,technology,대구,AI')
  .split(',')
  .map((term) => term.trim())
  .filter((term) => term.length > 0);
const COUNTRY = __ENV.COUNTRY || '';
const CATEGORY = __ENV.CATEGORY || '';
const DATE = __ENV.DATE || '';
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 1);

export const searchResultCount = new Trend('search_result_count');

export const options = {
  scenarios: {
    search_fulltext_terms: {
      executor: 'constant-vus',
      exec: 'searchFulltextTerms',
      vus: Number(__ENV.SEARCH_VUS || 1),
      duration: __ENV.SEARCH_DURATION || '30s',
    },
  },
  thresholds: {
    'http_req_failed{api:search_fulltext_terms}': ['rate<0.01'],
    'http_req_duration{api:search_fulltext_terms}': ['p(95)<1500'],
  },
};

function query(params) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
}

function searchResultSummary(res) {
  try {
    const body = res.json();
    const articles = body && body.data && body.data.searchArticles;
    return {
      count: Array.isArray(articles) ? articles.length : 0,
      hasArray: Array.isArray(articles),
    };
  } catch (e) {
    return {
      count: 0,
      hasArray: false,
    };
  }
}

export function searchFulltextTerms() {
  group('search fulltext terms', () => {
    for (const term of SEARCH_TERMS) {
      const params = query({
        text: term,
        country: COUNTRY,
        category: CATEGORY,
        date: DATE,
      });

      const res = http.get(`${BASE_URL}/api/search?${params}`, {
        tags: {
          api: 'search_fulltext_terms',
          endpoint: 'search',
          searchTerm: term,
        },
      });
      const result = searchResultSummary(res);
      const resultCount = result.count;
      searchResultCount.add(resultCount, { searchTerm: term });

      check(res, {
        'status is 2xx': (r) => r.status >= 200 && r.status < 300,
        'response has body': (r) => r.body && r.body.length > 0,
        'response has searchArticles array': () => result.hasArray,
      });

      sleep(SLEEP_SECONDS);
    }
  });
}
