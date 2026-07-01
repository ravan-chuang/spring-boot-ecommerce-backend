import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";

export const options = {
  scenarios: {
    catalog_read_rate: {
      executor: "ramping-arrival-rate",
      startRate: 500,
      timeUnit: "1s",
      preAllocatedVUs: 50,
      maxVUs: 300,
      stages: [
        { target: 1000, duration: "20s" },
        { target: 2000, duration: "30s" },
        { target: 3000, duration: "30s" },
        { target: 4000, duration: "30s" },
        { target: 0, duration: "20s" },
      ],
    },
  },

  thresholds: {
    http_req_failed: ["rate<0.001"],
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