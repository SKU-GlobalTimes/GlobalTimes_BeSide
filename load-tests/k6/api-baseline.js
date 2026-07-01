import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ARTICLE_ID = __ENV.ARTICLE_ID || '1';
const SEARCH_TEXT = __ENV.SEARCH_TEXT || 'war';
const COUNTRY = __ENV.COUNTRY || '';
const CATEGORY = __ENV.CATEGORY || '';
const DATE = __ENV.DATE || '';

export const options = {
  scenarios: {
    articles_baseline: {
      executor: 'constant-vus',
      exec: 'articlesBaseline',
      vus: Number(__ENV.ARTICLES_VUS || 5),
      duration: __ENV.ARTICLES_DURATION || '1m',
    },
    search_baseline: {
      executor: 'constant-vus',
      exec: 'searchBaseline',
      vus: Number(__ENV.SEARCH_VUS || 3),
      duration: __ENV.SEARCH_DURATION || '1m',
      startTime: '5s',
    },
    perspectives_repeated: {
      executor: 'constant-vus',
      exec: 'perspectivesRepeated',
      vus: Number(__ENV.PERSPECTIVES_VUS || 3),
      duration: __ENV.PERSPECTIVES_DURATION || '1m',
      startTime: '10s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
    'http_req_duration{api:articles}': ['p(95)<800'],
    'http_req_duration{api:search}': ['p(95)<1500'],
    'http_req_duration{api:perspectives}': ['p(95)<2000'],
  },
};

function get(path, tags) {
  const res = http.get(`${BASE_URL}${path}`, { tags });

  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  return res;
}

function query(params) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
}

export function articlesBaseline() {
  group('articles list baseline', () => {
    get('/api/articles/latest?page=0&size=20', { api: 'articles', endpoint: 'latest' });
    get('/api/articles/cursor?size=20', { api: 'articles', endpoint: 'cursor' });
    get('/api/articles/popular?page=0&size=20', { api: 'articles', endpoint: 'popular' });

    const params = query({
      country: COUNTRY,
      category: CATEGORY,
      date: DATE,
      size: '20',
    });

    get(`/api/articles/explore?${params}`, { api: 'articles', endpoint: 'explore' });
  });

  sleep(1);
}

export function searchBaseline() {
  group('search baseline', () => {
    const params = query({
      text: SEARCH_TEXT,
      country: COUNTRY,
      category: CATEGORY,
      date: DATE,
    });

    get(`/api/search?${params}`, { api: 'search', endpoint: 'search' });
  });

  sleep(1);
}

export function perspectivesRepeated() {
  group('perspectives repeated call baseline', () => {
    get(`/api/news/${ARTICLE_ID}/perspectives`, {
      api: 'perspectives',
      endpoint: 'perspectives',
      cacheScenario: 'repeated',
    });
  });

  sleep(1);
}
