import axios from "axios";

const TOKEN_KEY = "lemuel_xr_guest_jwt";
const USER_KEY = "lemuel_xr_user_id";

// 운영: same-origin (/api/*) — Next.js Route Handler 가 backend 로 프록시
export const api = axios.create({
  baseURL: "",
  timeout: 30_000,
  headers: { "Content-Type": "application/json" },
});

/** 브라우저에서만 동작 — SSR safe */
function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(TOKEN_KEY);
}

function storeToken(token: string, userId: string) {
  if (typeof window === "undefined") return;
  localStorage.setItem(TOKEN_KEY, token);
  localStorage.setItem(USER_KEY, userId);
}

function clearToken() {
  if (typeof window === "undefined") return;
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(USER_KEY);
}

let pendingIssue: Promise<string> | null = null;

async function ensureGuestToken(): Promise<string> {
  const existing = getStoredToken();
  if (existing) return existing;
  if (pendingIssue) return pendingIssue;
  pendingIssue = (async () => {
    try {
      const res = await axios.post<{ userId: string; token: string }>(
        "/api/auth/guest",
        { deviceType: "web" },
        { headers: { "Content-Type": "application/json" } },
      );
      storeToken(res.data.token, res.data.userId);
      return res.data.token;
    } finally {
      pendingIssue = null;
    }
  })();
  return pendingIssue;
}

// Request interceptor — /api/auth/* 외 모든 요청에 Bearer 자동 첨부
api.interceptors.request.use(async (config) => {
  if (typeof window === "undefined") return config;
  const url = (config.url ?? "").toString();
  if (url.startsWith("/api/auth/")) return config;
  try {
    const token = await ensureGuestToken();
    config.headers = config.headers ?? {};
    (config.headers as Record<string, string>)["Authorization"] = `Bearer ${token}`;
  } catch (e) {
    // 토큰 발급 실패 시 그냥 통과 (backend 가 401 반환)
    console.warn("guest token issuance failed:", e);
  }
  return config;
});

// 401 → 토큰 만료 추정, 1회 재발급 시도
api.interceptors.response.use(
  (r) => r,
  async (error) => {
    if (
      error.response?.status === 401 &&
      error.config &&
      !error.config.__retry
    ) {
      clearToken();
      error.config.__retry = true;
      try {
        await ensureGuestToken();
      } catch {
        return Promise.reject(error);
      }
      return api.request(error.config);
    }
    return Promise.reject(error);
  },
);

export function getStoredUserId(): string | null {
  if (typeof window === "undefined") return null;
  return localStorage.getItem(USER_KEY);
}
