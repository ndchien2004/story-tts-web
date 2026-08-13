import axios from "axios";
import { normalizeDeep } from "../utils/text";

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

const TOKEN_STORAGE_KEY = "storytts.token";

export function getStoredToken() {
  return localStorage.getItem(TOKEN_STORAGE_KEY);
}

export function setStoredToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_STORAGE_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  }
}

const client = axios.create({
  baseURL: API_BASE_URL,
  headers: { "Content-Type": "application/json" },
});

/* ------------------------------------------------------------------ */
/* In-flight requests                                                  */
/* ------------------------------------------------------------------ */

/*
 * How many calls are outstanding right now, and who wants to know.
 *
 * The backend sleeps after a spell with no traffic and takes about a minute to
 * come back, during which every request simply hangs. Without a signal for that
 * the app looks broken: a click, then a blank page, then nothing. The loading
 * screen subscribes here so it can stay up for as long as the server is
 * actually being waited on rather than for a fixed beat.
 *
 * Deliberately a plain counter rather than a context: it is written from an
 * axios interceptor, which sits outside React entirely.
 */
let inFlight = 0;
const inFlightListeners = new Set();

function publishInFlight() {
  for (const listener of inFlightListeners) listener(inFlight);
}

/**
 * Watch the number of outstanding requests.
 *
 * Calls back immediately with the current count, then on every change. Returns
 * the unsubscribe function.
 */
export function onInFlightChange(listener) {
  inFlightListeners.add(listener);
  listener(inFlight);
  return () => inFlightListeners.delete(listener);
}

/** Attach the bearer token to every outgoing request, and count it. */
client.interceptors.request.use(
  (config) => {
    const token = getStoredToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Marked on the config rather than counted blind, so the settle below only
    // ever decrements a request this interceptor actually incremented — a
    // request rejected before it got here must not push the count negative.
    config.counted = true;
    inFlight += 1;
    publishInFlight();

    return config;
  },
  (error) => Promise.reject(error),
);

function settle(config) {
  if (!config?.counted) return;
  config.counted = false;
  inFlight = Math.max(0, inFlight - 1);
  publishInFlight();
}

/**
 * Fold response text to Unicode NFC, then normalise every failure into an
 * `ApiError` so components never have to dig through the axios error shape.
 */
client.interceptors.response.use(
  (response) => {
    settle(response.config);
    response.data = normalizeDeep(response.data);
    return response;
  },
  (error) => {
    // Settled on the way out of every failure too, including the timeout and
    // the no-connection cases — a count that only came back down on success
    // would strand the loading screen the moment the server refused.
    settle(error.config);
    return Promise.reject(toApiError(error));
  },
);

export class ApiError extends Error {
  constructor({ status, code, message, requiredAccessLevel, fieldErrors }) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.requiredAccessLevel = requiredAccessLevel ?? null;
    this.fieldErrors = fieldErrors ?? null;
  }

  /** True when the server refused because the chapter is locked. */
  get isLocked() {
    return this.status === 403 && this.requiredAccessLevel !== null;
  }
}

function toApiError(error) {
  if (error.response) {
    const { status, data } = error.response;
    return new ApiError({
      status,
      code: data?.error,
      message: data?.message ?? "Đã có lỗi xảy ra. Vui lòng thử lại.",
      requiredAccessLevel: data?.requiredAccessLevel,
      fieldErrors: data?.fieldErrors,
    });
  }

  if (error.code === "ECONNABORTED") {
    return new ApiError({ status: 0, message: "Yêu cầu quá thời gian chờ." });
  }

  return new ApiError({
    status: 0,
    message: "Không kết nối được tới máy chủ. Kiểm tra xem backend đã chạy chưa.",
  });
}

export default client;
