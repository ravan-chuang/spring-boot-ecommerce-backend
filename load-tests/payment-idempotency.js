import http from "k6/http";
import { check } from "k6";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";
const ORDER_ID = __ENV.ORDER_ID;
const TOKEN = __ENV.ACCESS_TOKEN;
const IDEMPOTENCY_KEY = __ENV.IDEMPOTENCY_KEY;

if (!ORDER_ID || !TOKEN || !IDEMPOTENCY_KEY) {
  throw new Error(
    "Required: ORDER_ID, ACCESS_TOKEN, IDEMPOTENCY_KEY environment variables"
  );
}

export const options = {
  scenarios: {
    duplicate_payment_requests: {
      executor: "per-vu-iterations",
      vus: 30,
      iterations: 1,
      maxDuration: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
  },
};

export default function () {
  const response = http.post(
    `${BASE_URL}/api/orders/${ORDER_ID}/payments`,
    JSON.stringify({ method: "CREDIT_CARD" }),
    {
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: `Bearer ${TOKEN}`,
        "Idempotency-Key": IDEMPOTENCY_KEY,
      },
    }
  );

  const ok = check(response, {
    "returns HTTP 200": (r) => r.status === 200,
    "payment is PAID": (r) => {
      try {
        return JSON.parse(r.body)?.data?.status === "PAID";
      } catch {
        return false;
      }
    },
  });

  if (!ok) {
    console.error(`Unexpected response: ${response.status} ${response.body}`);
  }
}
