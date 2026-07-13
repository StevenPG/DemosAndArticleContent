import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

// Hammers the API well past its limit so you can watch 429s appear exactly
// where the bucket math says they should.
//
// Run with: k6 run k6/rate-limit-test.js

const ok = new Counter("responses_200");
const limited = new Counter("responses_429");

export const options = {
  scenarios: {
    anonymous: {
      executor: "constant-arrival-rate",
      rate: 60, // 60 req/min limit is 20 -> expect ~2/3 of these to 429
      timeUnit: "1m",
      duration: "2m",
      preAllocatedVUs: 10,
    },
    with_api_key: {
      executor: "constant-arrival-rate",
      rate: 90, // under the 100/min key limit -> expect ~zero 429s
      timeUnit: "1m",
      duration: "2m",
      preAllocatedVUs: 10,
      exec: "withApiKey",
    },
  },
};

export default function () {
  const res = http.get("http://localhost:8080/api/quote");
  tally(res);
}

export function withApiKey() {
  const res = http.get("http://localhost:8080/api/quote", {
    headers: { "X-Api-Key": "demo-key-1" },
  });
  tally(res);
}

function tally(res) {
  check(res, { "status is 200 or 429": (r) => r.status === 200 || r.status === 429 });
  if (res.status === 200) ok.add(1);
  if (res.status === 429) limited.add(1);
}
