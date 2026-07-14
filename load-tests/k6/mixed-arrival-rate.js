import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Gauge, Rate, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const ARTICLE_ID = __ENV.ARTICLE_ID || '7366';
const SEARCH_TEXT = __ENV.SEARCH_TEXT || 'war';
const LANGUAGE = __ENV.LANGUAGE || 'English';
const DURATION = __ENV.DURATION || '30s';
const TOTAL_RPS = Number(__ENV.TOTAL_RPS || 20);
const METRICS_INTERVAL_SECONDS = Number(__ENV.METRICS_INTERVAL_SECONDS || 5);

const SUPPORTED_TOTAL_RPS = [20, 40, 60];
if (!SUPPORTED_TOTAL_RPS.includes(TOTAL_RPS)) {
  throw new Error(`TOTAL_RPS must be one of ${SUPPORTED_TOTAL_RPS.join(', ')}`);
}

const rates = {
  latest: TOTAL_RPS * 0.35,
  popular: TOTAL_RPS * 0.25,
  detail: TOTAL_RPS * 0.20,
  search: TOTAL_RPS * 0.15,
  summary: TOTAL_RPS * 0.05,
};

export const latestRequests = new Counter('latest_requests');
export const latestFailures = new Rate('latest_failures');
export const latestDuration = new Trend('latest_duration', true);
export const popularRequests = new Counter('popular_requests');
export const popularFailures = new Rate('popular_failures');
export const popularDuration = new Trend('popular_duration', true);
export const detailRequests = new Counter('detail_requests');
export const detailFailures = new Rate('detail_failures');
export const detailDuration = new Trend('detail_duration', true);
export const searchRequests = new Counter('search_requests');
export const searchFailures = new Rate('search_failures');
export const searchDuration = new Trend('search_duration', true);
export const summaryRequests = new Counter('summary_requests');
export const summaryResponses200 = new Counter('summary_responses_200');
export const summaryResponses503 = new Counter('summary_responses_503');
export const summaryResponsesUnexpected = new Counter('summary_responses_unexpected');
export const summaryUnexpectedFailures = new Rate('summary_unexpected_failures');
export const summaryAcceptedDuration = new Trend('summary_accepted_duration', true);
export const summaryRejectedDuration = new Trend('summary_rejected_duration', true);
export const tomcatThreadsBusy = new Gauge('tomcat_threads_busy');
export const tomcatThreadsCurrent = new Gauge('tomcat_threads_current');
export const hikariConnectionsActive = new Gauge('hikari_connections_active');
export const hikariConnectionsPending = new Gauge('hikari_connections_pending');
export const asyncExecutorActive = new Gauge('async_executor_active');
export const asyncExecutorQueued = new Gauge('async_executor_queued');
export const metricsFailures = new Rate('mixed_metrics_failures');

function regularVuBudget(rate) {
  const preAllocatedVUs = Math.max(2, Math.ceil(rate / 2));
  return { preAllocatedVUs, maxVUs: Math.max(preAllocatedVUs, Math.ceil(rate)) };
}

function summaryVuBudget(rate) {
  const preAllocatedVUs = Math.max(5, Math.ceil(rate * 4));
  return { preAllocatedVUs, maxVUs: Math.max(preAllocatedVUs + 5, Math.ceil(rate * 10)) };
}

function arrivalScenario(exec, rate, vuBudget) {
  return {
    executor: 'constant-arrival-rate',
    exec,
    rate,
    timeUnit: '1s',
    duration: DURATION,
    preAllocatedVUs: vuBudget.preAllocatedVUs,
    maxVUs: vuBudget.maxVUs,
    gracefulStop: '20s',
  };
}

export const options = {
  scenarios: {
    latest_arrivals: arrivalScenario('latestLoad', rates.latest, regularVuBudget(rates.latest)),
    popular_arrivals: arrivalScenario('popularLoad', rates.popular, regularVuBudget(rates.popular)),
    detail_arrivals: arrivalScenario('detailLoad', rates.detail, regularVuBudget(rates.detail)),
    search_arrivals: arrivalScenario('searchLoad', rates.search, regularVuBudget(rates.search)),
    summary_arrivals: arrivalScenario('summaryLoad', rates.summary, summaryVuBudget(rates.summary)),
    server_metrics: {
      executor: 'constant-vus',
      exec: 'serverMetrics',
      vus: 1,
      startTime: '2s',
      duration: DURATION,
      gracefulStop: '5s',
    },
  },
  thresholds: {
    dropped_iterations: ['count==0'],
    latest_failures: ['rate<0.05'],
    popular_failures: ['rate<0.05'],
    detail_failures: ['rate<0.05'],
    search_failures: ['rate<0.05'],
    summary_unexpected_failures: ['rate==0'],
    mixed_metrics_failures: ['rate==0'],
    'http_req_duration{endpoint:latest}': ['p(95)<1500'],
    'http_req_duration{endpoint:popular}': ['p(95)<1500'],
    'http_req_duration{endpoint:detail}': ['p(95)<2000'],
    'http_req_duration{endpoint:search}': ['p(95)<2000'],
    summary_accepted_duration: ['p(95)<15000'],
  },
};

function recordRead(res, name, requests, failures, duration) {
  const failed = res.status !== 200;
  requests.add(1);
  failures.add(failed);
  duration.add(res.timings.duration);
  check(res, { [`${name} status is 200`]: () => !failed });
}

export function latestLoad() {
  const res = http.get(`${BASE_URL}/api/articles/latest?page=0&size=20`, {
    tags: { api: 'mixed_arrival', endpoint: 'latest' },
  });
  recordRead(res, 'latest', latestRequests, latestFailures, latestDuration);
}

export function popularLoad() {
  const res = http.get(`${BASE_URL}/api/articles/popular?page=0&size=20`, {
    tags: { api: 'mixed_arrival', endpoint: 'popular' },
  });
  recordRead(res, 'popular', popularRequests, popularFailures, popularDuration);
}

export function detailLoad() {
  const res = http.get(`${BASE_URL}/api/news/detail?id=${encodeURIComponent(ARTICLE_ID)}`, {
    tags: { api: 'mixed_arrival', endpoint: 'detail' },
  });
  recordRead(res, 'detail', detailRequests, detailFailures, detailDuration);
}

export function searchLoad() {
  const res = http.get(`${BASE_URL}/api/search?text=${encodeURIComponent(SEARCH_TEXT)}`, {
    tags: { api: 'mixed_arrival', endpoint: 'search' },
  });
  recordRead(res, 'search', searchRequests, searchFailures, searchDuration);
}

export function summaryLoad() {
  const res = http.get(
    `${BASE_URL}/api/ai/${encodeURIComponent(ARTICLE_ID)}/summary?language=${encodeURIComponent(LANGUAGE)}`,
    { tags: { api: 'mixed_arrival', endpoint: 'summary' } },
  );

  const accepted = res.status === 200;
  const rejected = res.status === 503;
  const unexpected = !accepted && !rejected;
  summaryRequests.add(1);
  if (accepted) {
    summaryResponses200.add(1);
    summaryAcceptedDuration.add(res.timings.duration);
  }
  if (rejected) {
    summaryResponses503.add(1);
    summaryRejectedDuration.add(res.timings.duration);
  }
  if (unexpected) summaryResponsesUnexpected.add(1);
  summaryUnexpectedFailures.add(unexpected);
  check(res, { 'summary status is 200 or 503': () => accepted || rejected });
}

function metricValue(name, query = '') {
  const res = http.get(`${BASE_URL}/actuator/metrics/${name}${query}`, {
    tags: { api: 'mixed_arrival_metrics', endpoint: 'metrics', metric: name },
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

export function serverMetrics() {
  const tomcatBusy = metricValue('tomcat.threads.busy');
  const tomcatCurrent = metricValue('tomcat.threads.current');
  const hikariActive = metricValue('hikaricp.connections.active');
  const hikariPending = metricValue('hikaricp.connections.pending');
  const executorQuery = '?tag=name:aiSummaryExecutor';
  const executorActive = metricValue('executor.active', executorQuery);
  const executorQueued = metricValue('executor.queued', executorQuery);

  if (tomcatBusy !== null) tomcatThreadsBusy.add(tomcatBusy);
  if (tomcatCurrent !== null) tomcatThreadsCurrent.add(tomcatCurrent);
  if (hikariActive !== null) hikariConnectionsActive.add(hikariActive);
  if (hikariPending !== null) hikariConnectionsPending.add(hikariPending);
  if (executorActive !== null) asyncExecutorActive.add(executorActive);
  if (executorQueued !== null) asyncExecutorQueued.add(executorQueued);
  sleep(METRICS_INTERVAL_SECONDS);
}
