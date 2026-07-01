import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";

export const options = {
  stages: [
    { duration: "20s", target: 50 },
    { duration: "40s", target: 100 },
    { duration: "40s", target: 200 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500", "p(99)<1000"],
  },
};

export default function () {
  const response = http.get(`${BASE_URL}/api/products`);

  check(response, {
    "status is 200": (r) => r.status === 200,
  });
}
