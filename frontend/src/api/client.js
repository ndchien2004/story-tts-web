import axios from "axios";

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

/** Attach the bearer token to every outgoing request. */
client.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

/**
 * Normalise every failure into an `ApiError` so components never have to dig
 * through the axios error shape.
 */
client.interceptors.response.use(
  (response) => response,
  (error) => Promise.reject(toApiError(error)),
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
