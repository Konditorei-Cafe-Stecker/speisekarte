// Load test: the traffic of 10,000 guests on the website at the same time.
//
// On a laptop, run it inside the cluster: deploy/loadtest/run-in-cluster.sh
// (from the Mac, the Colima tunnel becomes the bottleneck, see
// deploy/README.md). Directly with k6, e.g. against a cloud system:
//   k6 run deploy/loadtest/guests.js                     # 10,000 guests
//   k6 run -e GUESTS=20000 deploy/loadtest/guests.js     # other numbers
//   k6 run -e BASE_URL=https://staging.example deploy/loadtest/guests.js
//   k6 run -e RAMP=20s -e HOLD=1m deploy/loadtest/guests.js # shorter run
//
// k6 cannot hold 10,000 real browsers on a laptop, so it sends the
// requests those guests would send, at the same rate:
//   - a visit lasts about 4 minutes, so 10,000 guests = 42 new visits/s;
//   - each visit loads the page (index.html and the files it references:
//     script, stylesheet, hero image), the menu and today's
//     recommendations: 6 requests;
//   - 30 % order; a waiting guest checks the order status every 10 s
//     (frontend Menu.tsx) for about 2 minutes until the waiter confirms.
// That is about 415 requests per second (42 x 6 + 13 orders + 150 status
// checks), held for 3 minutes after a 1-minute ramp.
//
// Orders from the test carry the note "Lasttest (k6)"; delete them with
// the command in deploy/README.md. Only run this against a test system.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8088';
const GUESTS = Number(__ENV.GUESTS || 10000);
const VISIT_SECONDS = 240;
const ORDER_SHARE = 0.3;
const WAIT_SECONDS = 120;
const RAMP = __ENV.RAMP || '1m';
const HOLD = __ENV.HOLD || '3m';
const POLL_SECONDS = 10;

const visitsPerSecond = GUESTS / VISIT_SECONDS;
const ordersPerSecond = visitsPerSecond * ORDER_SHARE;
const pollsPerSecond = (ordersPerSecond * WAIT_SECONDS) / POLL_SECONDS;

// Enough virtual users to keep the rate even if answers take 10 s (while
// a new copy starts, single answers took up to 8 s). Iterations (a page
// visit, a menu load, an order, a status check) that k6 could not start in
// time are counted (dropped_iterations) and fail the test, so a slow
// system cannot hide behind a lower rate.
const WORST_SECONDS = 10;

function scenario(exec, rate) {
  const perSecond = Math.max(1, Math.round(rate));
  return {
    executor: 'ramping-arrival-rate',
    exec,
    startRate: 0,
    timeUnit: '1s',
    preAllocatedVUs: Math.max(10, perSecond),
    maxVUs: Math.max(20, perSecond * WORST_SECONDS),
    stages: [
      { target: perSecond, duration: RAMP },   // the rush builds up
      { target: perSecond, duration: HOLD },   // 10,000 guests on the site
      { target: 0, duration: '20s' },
    ],
  };
}

export const options = {
  scenarios: {
    page: scenario('loadPage', visitsPerSecond),
    menu: scenario('loadMenu', visitsPerSecond),
    order: scenario('sendOrder', ordersPerSecond),
    status: scenario('checkStatus', pollsPerSecond),
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],                       // < 1 % errors
    dropped_iterations: ['count==0'],                     // the full load was sent
    'http_req_duration{kind:page}': ['p(95)<500'],
    'http_req_duration{kind:menu}': ['p(95)<500'],
    'http_req_duration{kind:recommendations}': ['p(95)<500'],
    'http_req_duration{kind:status}': ['p(95)<500'],
    'http_req_duration{kind:order}': ['p(95)<1000'],
  },
  summaryTrendStats: ['avg', 'p(50)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
  const page = http.get(`${BASE}/`);
  if (page.status !== 200) {
    throw new Error(`The website at ${BASE} is not reachable `
      + `(status ${page.status}${page.error ? `: ${page.error}` : ''}).`);
  }
  const assets = [...page.body.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map((m) => m[1]);

  const menu = http.get(`${BASE}/api/menu`).json();
  const products = menu.filter((item) => item.available).map((item) => item.id);
  if (products.length === 0) throw new Error('No available products: import the menu first.');

  // The orders that waiting guests check on: as many as are waiting at
  // the same time (orders per second x waiting time).
  const poolSize = Math.min(3000, Math.round(ordersPerSecond * WAIT_SECONDS));
  const references = [];
  for (let start = 0; start < poolSize; start += 50) {
    const batch = [];
    for (let i = start; i < Math.min(poolSize, start + 50); i++) {
      batch.push(['POST', `${BASE}/api/orders`, orderBody(products),
        { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'setup' } }]);
    }
    for (const response of http.batch(batch)) {
      if (response.status === 201) references.push(response.json().orderReference);
    }
  }
  if (references.length === 0) throw new Error('Could not create any order.');
  console.log(`Guests: ${GUESTS}; per second: ${visitsPerSecond.toFixed(0)} visits, `
    + `${ordersPerSecond.toFixed(1)} orders, ${pollsPerSecond.toFixed(0)} status checks; `
    + `assets per page: ${assets.length}; orders being waited on: ${references.length}`);
  return { assets, products, references };
}

function jsonHeaders() {
  return { headers: { 'Content-Type': 'application/json' }, tags: { kind: 'order' } };
}

function orderBody(products) {
  const lines = 1 + Math.floor(Math.random() * 3);
  const items = [];
  for (let i = 0; i < lines; i++) {
    const id = products[Math.floor(Math.random() * products.length)];
    if (!items.some((item) => item.menuItemId === id)) {
      items.push({ menuItemId: id, quantity: 1 + Math.floor(Math.random() * 2) });
    }
  }
  return JSON.stringify({ items, note: 'Lasttest (k6)' });
}

export function loadPage(data) {
  const responses = http.batch([
    ['GET', `${BASE}/`, null, { tags: { kind: 'page' } }],
    ...data.assets.map((path) => ['GET', `${BASE}${path}`, null, { tags: { kind: 'page' } }]),
  ]);
  check(responses[0], { 'page 200': (r) => r.status === 200 });
}

export function loadMenu() {
  // The home and menu pages also show today's recommendations.
  const [menu, recommendations] = http.batch([
    ['GET', `${BASE}/api/menu`, null, { tags: { kind: 'menu' } }],
    ['GET', `${BASE}/api/recommendations/today`, null, { tags: { kind: 'recommendations' } }],
  ]);
  check(menu, { 'menu 200': (r) => r.status === 200 });
  check(recommendations, { 'recommendations 200': (r) => r.status === 200 });
}

export function sendOrder(data) {
  const response = http.post(`${BASE}/api/orders`, orderBody(data.products), jsonHeaders());
  check(response, { 'order 201': (r) => r.status === 201 });
}

export function checkStatus(data) {
  const reference = data.references[Math.floor(Math.random() * data.references.length)];
  const response = http.get(`${BASE}/api/orders/${reference}`, { tags: { kind: 'status' } });
  check(response, { 'status 200': (r) => r.status === 200 });
}
