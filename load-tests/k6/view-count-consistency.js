import http from 'k6/http';
import { check } from 'k6';

const baseUrl = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const articleId = __ENV.ARTICLE_ID;
const vus = Number(__ENV.VUS || 20);
const requests = Number(__ENV.REQUESTS || 20);

if (!articleId) {
  throw new Error('ARTICLE_ID is required');
}

export const options = {
  scenarios: {
    concurrentDetail: {
      executor: 'shared-iterations',
      vus,
      iterations: requests,
      maxDuration: '30s',
    },
  },
  thresholds: {
    http_req_failed: ['rate==0'],
    checks: ['rate==1'],
  },
};

export default function () {
  const response = http.get(`${baseUrl}/api/news/detail?id=${articleId}`);
  check(response, {
    'detail status is 200': (res) => res.status === 200,
  });
}
