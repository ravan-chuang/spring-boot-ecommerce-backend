import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";

export const options = {
  scenarios: {
    catalog_3k: {
      executor: "constant-arrival-rate",
      rate: 3000,
      timeUnit: "1s",
      duration: "5m",
      preAllocatedVUs: 80,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.001"],
    dropped_iterations: ["count==0"],
    http_req_duration: ["p(95)<100", "p(99)<300"],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/api/products`, {
    timeout: "5s",
  });

  check(response, {
    "status is 200": (r) => r.status === 200,
  });
}