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
  localStorage.removeItem(DISCLAIMER_KEY);
}

/**
 * 저장된 게스트 세션을 버린다 — 다음 요청에서 새로 발급된다.
 *
 * 사용자 계정 기능이 따로 없어 이 토큰이 유일한 신원이다. 만료된 토큰이 localStorage 에
 * 박혀 있으면 앱 전체가 잠기므로, 화면에서도 직접 버릴 수 있어야 한다.
 */
export function resetGuestSession() {
  clearToken();
}

let pendingIssue: Promise<string> | null = null;

const DISCLAIMER_KEY = "lemuel_xr_disclaimer_accepted";

/**
 * 게스트 토큰 발급 직후 disclaimer 동의를 확정한다. 실패하더라도 토큰 발급 자체는
 * 유효하므로 조용히 넘어간다 (콘텐츠 요청 시 451 이 다시 표면화됨).
 * 세션당 1회만 시도하도록 localStorage 플래그로 가드.
 */
async function ensureDisclaimerAccepted(token: string): Promise<void> {
  if (typeof window === "undefined") return;
  if (localStorage.getItem(DISCLAIMER_KEY) === "true") return;
  try {
    await axios.post(
      "/api/auth/accept-disclaimer",
      {},
      {
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${token}`,
        },
      },
    );
    localStorage.setItem(DISCLAIMER_KEY, "true");
  } catch (e) {
    console.warn("disclaimer acceptance failed:", e);
  }
}

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
      // 갓 발급된 게스트는 disclaimer 미동의 상태 — 콘텐츠/게임 endpoint 는
      // DisclaimerGateFilter 가 451 로 차단한다. 발급 직후 동의를 확정해
      // 이후 요청이 게이트를 통과하도록 한다.
      await ensureDisclaimerAccepted(res.data.token);
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
    (config.headers as Record<string, string>)["Authorization"] =
      `Bearer ${token}`;
  } catch (e) {
    // 토큰 발급 실패 시 그냥 통과 (backend 가 401 반환)
    console.warn("guest token issuance failed:", e);
  }
  return config;
});

// 401 → 토큰 만료 추정, 1회 재발급 시도.
// 403 도 같이 본다: 백엔드에 AuthenticationEntryPoint 가 없던 시절(~2026-08-06) 미인증
// 요청이 전부 403 으로 나갔고, 여기서 401 만 보다가 죽은 토큰이 localStorage 에 영구히
// 박혀 앱 전체가 잠겼다. 백엔드는 고쳤지만, 롤아웃이 끝나기 전 브라우저나 되돌아간
// 배포에서도 스스로 빠져나올 수 있게 두 상태를 함께 처리한다. 진짜 권한 문제라면
// 재발급 후 한 번 더 403 이 나고 그대로 표면화된다(재시도는 1회뿐).
api.interceptors.response.use(
  (r) => r,
  async (error) => {
    if (
      (error.response?.status === 401 || error.response?.status === 403) &&
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
