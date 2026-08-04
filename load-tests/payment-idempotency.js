import http from "k6/http";
import { check } from "k6";
import { Counter, Rate } from "k6/metrics";

const BASE_URL = __ENV.BASE_URL || "http://host.docker.internal:8080";
const ORDER_ID = __ENV.ORDER_ID;
const TOKEN = __ENV.ACCESS_TOKEN;
const IDEMPOTENCY_KEY = __ENV.IDEMPOTENCY_KEY;
const VUS = Number(__ENV.VUS || 30);

export function setup() {
  if (!ORDER_ID || !TOKEN || !IDEMPOTENCY_KEY) {
    throw new Error(
      "Required: ORDER_ID, ACCESS_TOKEN, IDEMPOTENCY_KEY environment variables"
    );
  }
}

const paymentResponseFailures = new Counter("payment_response_failures");
const missingPaymentId = new Counter("missing_payment_id");
const paidResponseRate = new Rate("paid_response_rate");

export const options = {
  scenarios: {
    duplicate_payment_requests: {
      executor: "per-vu-iterations",
      vus: VUS,
      iterations: 1,
      maxDuration: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.05"],
    http_req_duration: ["p(95)<1000", "p(99)<2000"],
    payment_response_failures: ["count==0"],
    missing_payment_id: ["count==0"],
    paid_response_rate: ["rate==1"],
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
      timeout: "10s",
    }
  );

  let body;
  try {
    body = response.json();
  } catch {
    body = null;
  }

  const paymentId = body?.data?.id;
  const paymentStatus = body?.data?.status;

  const passed = check(response, {
    "returns HTTP 200": (r) => r.status === 200,
    "response contains payment id": () => paymentId != null,
    "payment is PAID": () => paymentStatus === "PAID",
  });

  paidResponseRate.add(paymentStatus === "PAID");

  if (paymentId == null) {
    missingPaymentId.add(1);
  }

  if (!passed) {
    paymentResponseFailures.add(1);
    console.error(
      `Unexpected response: status=${response.status} body=${response.body}`
    );
  } else {
    console.log(`paymentId=${paymentId}`);
  }
}