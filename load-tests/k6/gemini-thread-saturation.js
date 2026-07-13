import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ARTICLE_ID = __ENV.ARTICLE_ID || '7366';
const LANGUAGE = __ENV.LANGUAGE || 'English';
const DURATION = __ENV.DURATION || '30s';
const SUMMARY_SLEEP_SECONDS = Number(__ENV.SUMMARY_SLEEP_SECONDS || 0.1);
const POPULAR_SLEEP_SECONDS = Number(__ENV.POPULAR_SLEEP_SECONDS || 0.1);
const METRICS_SLEEP_SECONDS = Number(__ENV.METRICS_SLEEP_SECONDS || 1);
const SUMMARY_VUS = Number(__ENV.SUMMARY_VUS || 5);
const POPULAR_VUS = Number(__ENV.POPULAR_VUS || 5);

export const summaryRequests = new Counter('summary_requests');
export const summaryFailures = new Rate('summary_failures');
export const summaryDuration = new Trend('summary_duration', true);
export const popularRequests = new Counter('popular_requests');
export const popularFailures = new Rate('popular_failures');
export const popularDuration = new Trend('popular_duration', true);
export const tomcatThreadsBusy = new Gauge('tomcat_threads_busy');
export const tomcatThreadsCurrent = new Gauge('tomcat_threads_current');
export const tomcatThreadsMax = new Gauge('tomcat_threads_max');
export const metricsFailures = new Rate('thread_metrics_failures');

const scenarios = {
  popular_noise: {
    executor: 'constant-vus',
    exec: 'popularNoise',
    vus: POPULAR_VUS,
    duration: DURATION,
    gracefulStop: '5s',
  },
  thread_metrics: {
    executor: 'constant-vus',
    exec: 'threadMetrics',
    vus: 1,
    duration: DURATION,
    gracefulStop: '5s',
  },
};

if (SUMMARY_VUS > 0) {
  scenarios.summary_load = {
    executor: 'constant-vus',
    exec: 'summaryLoad',
    vus: SUMMARY_VUS,
    duration: DURATION,
    gracefulStop: '5s',
  };
}

export const options = {
  scenarios,
  thresholds: {
    summary_failures: ['rate<0.05'],
    popular_failures: ['rate<0.05'],
    thread_metrics_failures: ['rate<0.05'],
    'http_req_duration{endpoint:summary}': ['p(95)<15000'],
    'http_req_duration{endpoint:popular}': ['p(95)<5000'],
  },
};

function metricValue(name) {
  const res = http.get(`${BASE_URL}/actuator/metrics/${name}`, {
    tags: { api: 'thread_saturation', endpoint: 'thread_metrics', metric: name },
  });

  let value = null;
  try {
    const body = res.json();
    value = body && body.measurements && body.measurements.length > 0
      ? Number(body.measurements[0].value)
      : null;
  } catch (e) {
    value = null;
  }

  const failed = res.status !== 200 || value === null || Number.isNaN(value);
  metricsFailures.add(failed, { metric: name });
  check(res, { [`${name} metric is available`]: () => !failed });
  return failed ? null : value;
}

export function summaryLoad() {
  const res = http.get(
    `${BASE_URL}/api/ai/${encodeURIComponent(ARTICLE_ID)}/summary?language=${encodeURIComponent(LANGUAGE)}`,
    { tags: { api: 'thread_saturation', endpoint: 'summary' } },
  );

  const failed = res.status !== 200;
  summaryRequests.add(1);
  summaryFailures.add(failed);
  summaryDuration.add(res.timings.duration);
  check(res, { 'summary status is 200': () => !failed });
  sleep(SUMMARY_SLEEP_SECONDS);
}

export function popularNoise() {
  const res = http.get(`${BASE_URL}/api/articles/popular?page=0&size=20`, {
    tags: { api: 'thread_saturation', endpoint: 'popular' },
  });

  const failed = res.status !== 200;
  popularRequests.add(1);
  popularFailures.add(failed);
  popularDuration.add(res.timings.duration);
  check(res, { 'popular status is 200': () => !failed });
  sleep(POPULAR_SLEEP_SECONDS);
}

export function threadMetrics() {
  const busy = metricValue('tomcat.threads.busy');
  const current = metricValue('tomcat.threads.current');
  const max = metricValue('tomcat.threads.config.max');

  if (busy !== null) tomcatThreadsBusy.add(busy);
  if (current !== null) tomcatThreadsCurrent.add(current);
  if (max !== null) tomcatThreadsMax.add(max);
  sleep(METRICS_SLEEP_SECONDS);
}
