import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ARTICLE_IDS = (__ENV.ARTICLE_IDS || __ENV.ARTICLE_ID || '8449')
  .split(',')
  .map((id) => id.trim())
  .filter((id) => id.length > 0);
const WARM_ARTICLE_ID = __ENV.WARM_ARTICLE_ID || ARTICLE_IDS[0] || '8449';

export const options = {
  scenarios: {
    cold_cache: {
      executor: 'shared-iterations',
      exec: 'coldCache',
      vus: Number(__ENV.COLD_VUS || Math.min(ARTICLE_IDS.length, 5)),
      iterations: Number(__ENV.COLD_ITERATIONS || ARTICLE_IDS.length),
      maxDuration: __ENV.COLD_MAX_DURATION || '30s',
    },
    warm_cache: {
      executor: 'constant-vus',
      exec: 'warmCache',
      vus: Number(__ENV.WARM_VUS || 1),
      duration: __ENV.WARM_DURATION || '15s',
      startTime: __ENV.WARM_START_TIME || '5s',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    'http_req_duration{api:perspectives,cacheScenario:cold}': ['p(95)<3000'],
    'http_req_duration{api:perspectives,cacheScenario:warm}': ['p(95)<1000'],
  },
};

function getPerspective(articleId, cacheScenario) {
  const res = http.get(`${BASE_URL}/api/news/${articleId}/perspectives`, {
    tags: {
      api: 'perspectives',
      endpoint: 'perspectives',
      cacheScenario,
    },
  });

  check(res, {
    'status is 2xx': (r) => r.status >= 200 && r.status < 300,
    'response has body': (r) => r.body && r.body.length > 0,
  });

  return res;
}

export function coldCache() {
  group('perspectives cold cache', () => {
    const index = (__ITER % ARTICLE_IDS.length);
    getPerspective(ARTICLE_IDS[index], 'cold');
  });

  sleep(1);
}

export function warmCache() {
  group('perspectives warm cache', () => {
    getPerspective(WARM_ARTICLE_ID, 'warm');
  });

  sleep(1);
}
