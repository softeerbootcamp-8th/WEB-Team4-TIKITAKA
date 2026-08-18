import http from 'k6/http';
import { check } from 'k6';
import { Trend } from 'k6/metrics';

const baseUrl = (__ENV.BASE_URL || 'http://localhost:8080').replace(/\/$/, '');
const workloadName = (__ENV.WORKLOAD || 'W2').toUpperCase();

const workloads = {
  W1: { sort: 'recommended', status: 'ACTIVE', page: 1, size: 16 },
  W2: { sort: 'latest', status: 'ACTIVE', page: 1, size: 16 },
  W3: { sort: 'deadline', status: 'ENDED', page: 1, size: 16 },
  W4: { sort: 'priceLow', status: 'ACTIVE', page: 1, size: 16 },
  W5: { sort: 'priceHigh', status: 'ACTIVE', page: 50, size: 100 },
};

const workload = workloads[workloadName];
if (!workload) {
  throw new Error(`Unsupported WORKLOAD "${workloadName}". Use W1, W2, W3, W4, or W5.`);
}

const responseTrend = new Trend('auction_list_response_time', true);

export const options = {
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
  scenarios: {
    auction_list: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 20),
      timeUnit: '1s',
      duration: __ENV.DURATION || '30s',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 10),
      maxVUs: Number(__ENV.MAX_VUS || 100),
    },
  },
};

function auctionListUrl() {
  const query = [
    `sort=${workload.sort}`,
    `status=${workload.status}`,
    `page=${workload.page}`,
    `size=${workload.size}`,
  ].join('&');
  return `${baseUrl}/api/v1/auctions?${query}`;
}

export default function () {
  const response = http.get(auctionListUrl(), {
    tags: { name: 'auction-list', workload: workloadName },
  });
  responseTrend.add(response.timings.duration, { workload: workloadName });

  let apiSuccess = false;
  if (response.status === 200) {
    try {
      apiSuccess = response.json('success') === true;
    } catch (_) {
      apiSuccess = false;
    }
  }

  check(response, {
    'auction list returns 200': (result) => result.status === 200,
    'auction API reports success': () => apiSuccess,
  });
}
