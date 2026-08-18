import axios from "axios";
import { normalizeDeep } from "../utils/text";

export const API_BASE_URL =
  import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

export const TOKEN_STORAGE_KEY = "storytts.token";

/**
 * Set just before a forced sign-out so the page that loads next can explain
 * why. `sessionStorage`, not `localStorage`: the explanation belongs to this
 * one tab and this one moment, and should not resurface tomorrow.
 */
const LOCKED_NOTICE_KEY = "storytts.lockedOut";

/** The server's machine-readable word for "this account has been locked". */
export const ACCOUNT_LOCKED = "ACCOUNT_LOCKED";

/** Shown when the session ends, and used for calls refused after it has. */
export const LOCKED_MESSAGE = "Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.";

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

/* ------------------------------------------------------------------ */
/* Forced sign-out                                                     */
/* ------------------------------------------------------------------ */

/*
 * One place decides that the session is over, and it is here rather than in any
 * page.
 *
 * The alternative — every screen checking for itself — was never going to hold:
 * a locked account can be discovered by any of several dozen calls, made from
 * pages, hooks, poll loops and background timers, and the one that happens to
 * find out first is not predictable. Handling it per page means the answer is
 * only as good as the least-maintained screen.
 *
 * Once this has fired the session is over for good, for this page load. It
 * cannot be undone by a later response, which is the point: a request that left
 * before the account was locked can still come back 200 afterwards, and treating
 * that as evidence of a valid session would put the user straight back in.
 */
let sessionTerminated = false;
const terminationListeners = new Set();

/** True once the session has been force-ended; late responses must not undo it. */
export function isSessionTerminated() {
  return sessionTerminated;
}

/**
 * Run something when the session is force-ended. Returns the unsubscribe
 * function. Fires at most once per page load.
 */
export function onSessionTerminated(listener) {
  terminationListeners.add(listener);
  return () => terminationListeners.delete(listener);
}

/** Whether the last sign-out was forced, clearing the flag as it reports it. */
export function consumeLockedNotice() {
  try {
    const found = sessionStorage.getItem(LOCKED_NOTICE_KEY) !== null;
    sessionStorage.removeItem(LOCKED_NOTICE_KEY);
    return found;
  } catch {
    // Private browsing modes can refuse storage entirely. Losing the notice is
    // survivable; failing to sign the user out would not be.
    return false;
  }
}

/**
 * End the session now.
 *
 * The token goes first, before any listener runs, so that anything already
 * queued behind this cannot carry it. Removing it also writes to
 * `localStorage`, which is what other tabs are watching — see `SessionGuard`.
 */
export function terminateSession() {
  if (sessionTerminated) return;
  sessionTerminated = true;

  setStoredToken(null);
  try {
    sessionStorage.setItem(LOCKED_NOTICE_KEY, "1");
  } catch {
    // See consumeLockedNotice.
  }

  for (const listener of terminationListeners) listener();
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
    // Nothing else leaves this page once the session is over.
    //
    // Signing out is not enough on its own: poll loops, retry timers and
    // background refreshes are already scheduled, and each would fire one more
    // request on the way out. Every one of those would be rejected anyway, so
    // the only thing they can still produce is noise in the server log and a
    // burst of failures racing the redirect.
    if (sessionTerminated) {
      return Promise.reject(
        new ApiError({ status: 401, code: ACCOUNT_LOCKED, message: LOCKED_MESSAGE }),
      );
    }

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

    const apiError = toApiError(error);

    // The one refusal that ends the session rather than being reported to the
    // caller. Recognised by the code, never by the message: the wording is
    // Vietnamese prose that anyone might reasonably reword, and a sign-out that
    // depends on an exact sentence is a sign-out waiting to stop working.
    //
    // Deliberately outside any retry: retrying this can only produce the same
    // answer, and there is no refresh-token flow to attempt either — the server
    // issues one token and re-checks the account on every request.
    if (apiError.code === ACCOUNT_LOCKED) {
      terminateSession();
    }

    return Promise.reject(apiError);
  },
);

export class ApiError extends Error {
  constructor({ status, code, message, requiredAccessLevel, fieldErrors, details }) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.requiredAccessLevel = requiredAccessLevel ?? null;
    this.fieldErrors = fieldErrors ?? null;

    /**
     * Numbers the screen needs to render the refusal — the chapter's coin price
     * and the reader's balance, for instance. Separate from `fieldErrors`, which
     * is about a form the user filled in wrong.
     */
    this.details = details ?? null;
  }

  /** True when the server refused because the chapter is locked behind a rank. */
  get isLocked() {
    return this.status === 403 && this.requiredAccessLevel !== null;
  }

  /**
   * True when the chapter is behind a coin price the reader can actually pay.
   *
   * A separate question from `isLocked`, and the difference matters on screen:
   * a rank lock is a dead end that sends people to the upgrade page, while this
   * one has a button right there.
   */
  get isPurchaseRequired() {
    return this.status === 402;
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
      details: data?.details,
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
