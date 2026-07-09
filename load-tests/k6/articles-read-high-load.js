import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const SIZE = __ENV.ARTICLES_SIZE || '20';
const SLEEP_SECONDS = Number(__ENV.SLEEP_SECONDS || 0.2);

const LATEST_PAGES = list(__ENV.LATEST_PAGES || '0,1,5,20');
const POPULAR_PAGES = list(__ENV.POPULAR_PAGES || '0,1,5');
const CURSORS = list(__ENV.CURSORS || '');
const DETAIL_IDS = list(__ENV.DETAIL_IDS || '7366,8449');
const EXPLORE_COUNTRIES = list(__ENV.EXPLORE_COUNTRIES || 'us,kr,jp');
const EXPLORE_CATEGORIES = list(__ENV.EXPLORE_CATEGORIES || 'general,business,technology');
const EXPLORE_DATES = list(__ENV.EXPLORE_DATES || '');

export const articlesPayloadSize = new Trend('articles_payload_size');

export const options = {
  scenarios: {
    articles_latest_high_load: {
      executor: 'constant-vus',
      exec: 'articlesLatest',
      vus: Number(__ENV.LATEST_VUS || 5),
      duration: __ENV.LATEST_DURATION || '30s',
    },
    articles_cursor_high_load: {
      executor: 'constant-vus',
      exec: 'articlesCursor',
      vus: Number(__ENV.CURSOR_VUS || 5),
      duration: __ENV.CURSOR_DURATION || '30s',
      startTime: __ENV.CURSOR_START_TIME || '5s',
    },
    articles_popular_high_load: {
      executor: 'constant-vus',
      exec: 'articlesPopular',
      vus: Number(__ENV.POPULAR_VUS || 5),
      duration: __ENV.POPULAR_DURATION || '30s',
      startTime: __ENV.POPULAR_START_TIME || '10s',
    },
    articles_explore_high_load: {
      executor: 'constant-vus',
      exec: 'articlesExplore',
      vus: Number(__ENV.EXPLORE_VUS || 5),
      duration: __ENV.EXPLORE_DURATION || '30s',
      startTime: __ENV.EXPLORE_START_TIME || '15s',
    },
    news_detail_high_load: {
      executor: 'constant-vus',
      exec: 'newsDetail',
      vus: Number(__ENV.DETAIL_VUS || 3),
      duration: __ENV.DETAIL_DURATION || '30s',
      startTime: __ENV.DETAIL_START_TIME || '20s',
    },
  },
  thresholds: {
    'http_req_failed{api:articles_read_high_load}': ['rate<0.05'],
    'http_req_duration{api:articles_read_high_load}': ['p(95)<1500'],
    'http_req_duration{endpoint:latest}': ['p(95)<1000'],
    'http_req_duration{endpoint:cursor}': ['p(95)<1000'],
    'http_req_duration{endpoint:popular}': ['p(95)<1500'],
    'http_req_duration{endpoint:explore}': ['p(95)<1500'],
    'http_req_duration{endpoint:detail}': ['p(95)<1500'],
  },
};

function list(value) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter((item) => item.length > 0);
}

function pick(values, fallback) {
  if (values.length === 0) {
    return fallback;
  }
  return values[__ITER % values.length];
}

function query(params) {
  return Object.entries(params)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(value)}`)
    .join('&');
}

function get(path, endpoint, extraTags = {}) {
  const res = http.get(`${BASE_URL}${path}`, {
    tags: {
      api: 'articles_read_high_load',
      endpoint,
      ...extraTags,
    },
  });

  articlesPayloadSize.add(res.body ? res.body.length : 0, { endpoint });

  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  sleep(SLEEP_SECONDS);
  return res;
}

export function articlesLatest() {
  group('articles latest high load', () => {
    const page = pick(LATEST_PAGES, '0');
    get(`/api/articles/latest?page=${encodeURIComponent(page)}&size=${encodeURIComponent(SIZE)}`, 'latest', { page });
  });
}

export function articlesCursor() {
  group('articles cursor high load', () => {
    const cursor = pick(CURSORS, '');
    const params = query({ cursor, size: SIZE });
    get(`/api/articles/cursor?${params}`, 'cursor', { cursorProvided: cursor !== '' ? 'true' : 'false' });
  });
}

export function articlesPopular() {
  group('articles popular high load', () => {
    const page = pick(POPULAR_PAGES, '0');
    get(`/api/articles/popular?page=${encodeURIComponent(page)}&size=${encodeURIComponent(SIZE)}`, 'popular', { page });
  });
}

export function articlesExplore() {
  group('articles explore high load', () => {
    const country = pick(EXPLORE_COUNTRIES, '');
    const category = pick(EXPLORE_CATEGORIES, '');
    const date = pick(EXPLORE_DATES, '');
    const params = query({ country, category, date, size: SIZE });

    get(`/api/articles/explore?${params}`, 'explore', {
      country: country || 'none',
      category: category || 'none',
      dateProvided: date !== '' ? 'true' : 'false',
    });
  });
}

export function newsDetail() {
  group('news detail high load', () => {
    const id = pick(DETAIL_IDS, '1');
    get(`/api/news/detail?id=${encodeURIComponent(id)}`, 'detail', { articleId: id });
  });
}
