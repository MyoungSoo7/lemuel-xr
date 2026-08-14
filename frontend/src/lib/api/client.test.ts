import type {
  AxiosInstance,
  AxiosResponse,
  InternalAxiosRequestConfig,
} from "axios";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/**
 * client.ts 는 이 앱의 *유일한 신원 발급기*다. 게스트 JWT 가 곧 계정이고,
 * disclaimer 동의는 백엔드 DisclaimerGateFilter 가 451 로 막는 안전 게이트다.
 * 그래서 여기서 재는 것은 "함수가 호출됐다"가 아니라 **네트워크로 실제 무엇이 나갔는가** —
 * 어떤 URL 에 어떤 헤더/바디가 몇 번 나갔고, 실패하면 무엇이 복구되는가다.
 *
 * 그러기 위해 axios 를 통째로 vi.mock 하지 않는다. 인터셉터 체인·config 병합·
 * 재시도 경로가 전부 axios 실제 구현 위에서 돌아야 의미가 있기 때문에,
 *  - 토큰 발급 경로(raw `axios.post`)는 spyOn 으로 가로채고
 *  - `api` 인스턴스는 **adapter 를 갈아끼워** 전송 계층만 대체한다.
 * 인터셉터는 진짜로 실행된다.
 */

const TOKEN_KEY = "lemuel_xr_guest_jwt";
const USER_KEY = "lemuel_xr_user_id";
const DISCLAIMER_KEY = "lemuel_xr_disclaimer_accepted";

interface AuthPost {
  url: string;
  body: unknown;
  auth?: string;
}

interface Queued {
  status: number;
  data?: unknown;
}

interface SetupOptions {
  /** n 번째 게스트 발급 호출의 결과. throw 하면 발급 실패. */
  guest?: (attempt: number) => unknown;
  /** n 번째 disclaimer 동의 호출의 결과. throw 하면 동의 실패. */
  disclaimer?: (attempt: number) => unknown;
}

interface Harness {
  api: AxiosInstance;
  resetGuestSession: () => void;
  getStoredUserId: () => string | null;
  /** `api` 인스턴스를 통해 실제로 전송 직전까지 간 요청들 */
  requests: InternalAxiosRequestConfig[];
  /** raw axios.post 로 나간 인증 계열 요청들 */
  authPosts: AuthPost[];
  /** 앞에서부터 하나씩 소비되는 응답 큐. 비면 200 을 준다. */
  queue: Queued[];
  guestPosts: () => AuthPost[];
  disclaimerPosts: () => AuthPost[];
}

/**
 * client.ts 는 모듈 스코프에 pendingIssue 를 들고 있다. 테스트끼리 그 상태가 새면
 * "발급 1회" 같은 단언이 앞 테스트 결과에 의존하게 되므로 매번 모듈을 새로 적재한다.
 */
async function setup(options: SetupOptions = {}): Promise<Harness> {
  vi.resetModules();
  const axios = (await import("axios")).default;

  const authPosts: AuthPost[] = [];
  let guestAttempts = 0;
  let disclaimerAttempts = 0;

  vi.spyOn(axios, "post").mockImplementation((async (
    url: string,
    body: unknown,
    config?: { headers?: Record<string, string> },
  ) => {
    authPosts.push({ url, body, auth: config?.headers?.Authorization });
    if (url === "/api/auth/guest") {
      guestAttempts += 1;
      if (options.guest) return await options.guest(guestAttempts);
      return {
        data: {
          token: `token-${guestAttempts}`,
          userId: `user-${guestAttempts}`,
        },
      };
    }
    if (url === "/api/auth/accept-disclaimer") {
      disclaimerAttempts += 1;
      if (options.disclaimer)
        return await options.disclaimer(disclaimerAttempts);
      return { data: {} };
    }
    throw new Error(`예상치 못한 axios.post: ${url}`);
  }) as never);

  const mod = await import("./client");

  const requests: InternalAxiosRequestConfig[] = [];
  const queue: Queued[] = [];
  mod.api.defaults.adapter = (async (config: InternalAxiosRequestConfig) => {
    requests.push(config);
    const next = queue.shift() ?? { status: 200, data: { ok: true } };
    const response = {
      data: next.data ?? {},
      status: next.status,
      statusText: "",
      headers: {},
      config,
    } as unknown as AxiosResponse;
    if (next.status >= 400) {
      const err = new Error(
        `Request failed with status code ${next.status}`,
      ) as Error & {
        config: unknown;
        response: unknown;
        isAxiosError: boolean;
      };
      err.isAxiosError = true;
      err.config = config;
      err.response = response;
      throw err;
    }
    return response;
  }) as never;

  return {
    api: mod.api,
    resetGuestSession: mod.resetGuestSession,
    getStoredUserId: mod.getStoredUserId,
    requests,
    authPosts,
    queue,
    guestPosts: () => authPosts.filter((p) => p.url === "/api/auth/guest"),
    disclaimerPosts: () =>
      authPosts.filter((p) => p.url === "/api/auth/accept-disclaimer"),
  };
}

function authHeaderOf(config: InternalAxiosRequestConfig): string | undefined {
  return (config.headers as unknown as Record<string, string>)?.Authorization;
}

let warn: ReturnType<typeof vi.spyOn>;

beforeEach(() => {
  // 실패 경로들은 의도적으로 console.warn 을 부른다 — 출력을 삼키되 호출 자체는 단언한다.
  warn = vi.spyOn(console, "warn").mockImplementation(() => {});
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.restoreAllMocks();
});

describe("게스트 세션 부트스트랩", () => {
  it("토큰이 없으면 요청 전에 발급받아 Bearer 로 붙이고 localStorage 에 남긴다", async () => {
    const h = await setup();

    await h.api.get("/api/content/topics");

    expect(h.guestPosts()).toHaveLength(1);
    expect(h.guestPosts()[0].body).toEqual({ deviceType: "web" });
    expect(authHeaderOf(h.requests[0])).toBe("Bearer token-1");
    expect(localStorage.getItem(TOKEN_KEY)).toBe("token-1");
    expect(localStorage.getItem(USER_KEY)).toBe("user-1");
  });

  it("이미 저장된 토큰이 있으면 재발급하지 않고 그 토큰을 쓴다", async () => {
    localStorage.setItem(TOKEN_KEY, "old-token");
    const h = await setup();

    await h.api.get("/api/content/topics");

    expect(h.guestPosts()).toHaveLength(0);
    expect(authHeaderOf(h.requests[0])).toBe("Bearer old-token");
  });

  it("/api/auth/* 로 나가는 요청에는 Bearer 를 붙이지 않는다", async () => {
    // 아직 토큰이 없는 상태에서 인증 endpoint 를 부르면 발급을 기다리다 자기 자신을
    // 다시 부르는 재귀가 된다. 그 고리를 끊는 게 이 분기다.
    const h = await setup();

    await h.api.post("/api/auth/guest", { deviceType: "web" });

    expect(authHeaderOf(h.requests[0])).toBeUndefined();
    expect(h.guestPosts()).toHaveLength(0);
  });

  it("동시 요청이 여러 개여도 발급은 딱 1회 (pendingIssue 공유)", async () => {
    const h = await setup();

    await Promise.all([
      h.api.get("/api/content/topics"),
      h.api.get("/api/values/me"),
      h.api.get("/api/game/joseph/1"),
    ]);

    expect(h.guestPosts()).toHaveLength(1);
    expect(h.requests.map(authHeaderOf)).toEqual([
      "Bearer token-1",
      "Bearer token-1",
      "Bearer token-1",
    ]);
    // 토큰 3개가 발급돼 마지막 것만 살아남는 일이 없어야 한다 — userId 가 곧 진행상황이다.
    expect(localStorage.getItem(USER_KEY)).toBe("user-1");
  });

  it("발급이 실패해도 요청 자체는 나간다 — Authorization 없이 (백엔드가 401 로 판정)", async () => {
    const h = await setup({
      guest: () => {
        throw new Error("guest issue 500");
      },
    });

    const res = await h.api.get("/api/content/topics");

    expect(res.status).toBe(200);
    expect(authHeaderOf(h.requests[0])).toBeUndefined();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
    expect(warn).toHaveBeenCalled();
  });

  it("발급이 한 번 실패해도 다음 요청에서 다시 시도한다 — 세션이 영구히 잠기지 않는다", async () => {
    const h = await setup({
      guest: (attempt) => {
        if (attempt === 1) throw new Error("일시적 실패");
        return { data: { token: "token-2", userId: "user-2" } };
      },
    });

    await h.api.get("/api/content/topics");
    await h.api.get("/api/content/topics");

    expect(h.guestPosts()).toHaveLength(2);
    expect(authHeaderOf(h.requests[1])).toBe("Bearer token-2");
  });
});

describe("disclaimer 자동 동의 (백엔드 451 게이트)", () => {
  it("발급 직후 새 토큰으로 동의를 확정하고 플래그를 남긴다", async () => {
    const h = await setup();

    await h.api.get("/api/content/topics");

    const accepted = h.disclaimerPosts();
    expect(accepted).toHaveLength(1);
    // 갓 발급된 토큰으로 동의해야 한다. 이전 토큰으로 보내면 백엔드가 다른 사용자를 동의시킨다.
    expect(accepted[0].auth).toBe("Bearer token-1");
    expect(localStorage.getItem(DISCLAIMER_KEY)).toBe("true");
  });

  it("동의는 토큰 발급보다 먼저 끝난다 — 첫 콘텐츠 요청이 451 을 맞지 않도록", async () => {
    const h = await setup();

    await h.api.get("/api/content/topics");

    const order = h.authPosts.map((p) => p.url);
    expect(order).toEqual(["/api/auth/guest", "/api/auth/accept-disclaimer"]);
    // 콘텐츠 요청은 동의가 끝난 뒤에야 어댑터에 도달한다.
    expect(h.requests).toHaveLength(1);
  });

  it("플래그가 이미 true 면 동의를 다시 보내지 않는다", async () => {
    localStorage.setItem(DISCLAIMER_KEY, "true");
    const h = await setup();

    await h.api.get("/api/content/topics");

    expect(h.guestPosts()).toHaveLength(1);
    expect(h.disclaimerPosts()).toHaveLength(0);
  });

  it("동의 실패는 토큰 발급을 막지 않지만 플래그도 남기지 않는다 — 다음 세션에서 재시도", async () => {
    const h = await setup({
      disclaimer: () => {
        throw new Error("disclaimer 502");
      },
    });

    await h.api.get("/api/content/topics");

    expect(localStorage.getItem(TOKEN_KEY)).toBe("token-1");
    expect(authHeaderOf(h.requests[0])).toBe("Bearer token-1");
    // 실패했는데 true 로 찍히면 451 이 영구화된다.
    expect(localStorage.getItem(DISCLAIMER_KEY)).toBeNull();
    expect(warn).toHaveBeenCalled();
  });

  it("세션을 버리면 동의 플래그도 함께 사라진다 — 새 게스트는 다시 동의한다", async () => {
    const h = await setup();
    await h.api.get("/api/content/topics");

    h.resetGuestSession();
    expect(localStorage.getItem(DISCLAIMER_KEY)).toBeNull();

    await h.api.get("/api/content/topics");

    expect(h.guestPosts()).toHaveLength(2);
    expect(h.disclaimerPosts().map((p) => p.auth)).toEqual([
      "Bearer token-1",
      "Bearer token-2",
    ]);
  });
});

describe("401/403 재인증", () => {
  it("401 이면 죽은 토큰을 버리고 재발급한 뒤 1회 재시도한다", async () => {
    const h = await setup();
    localStorage.setItem(TOKEN_KEY, "expired");
    localStorage.setItem(USER_KEY, "user-old");
    h.queue.push({ status: 401 }, { status: 200, data: { topics: [] } });

    const res = await h.api.get("/api/content/topics");

    expect(res.data).toEqual({ topics: [] });
    expect(h.requests).toHaveLength(2);
    expect(authHeaderOf(h.requests[0])).toBe("Bearer expired");
    expect(authHeaderOf(h.requests[1])).toBe("Bearer token-1");
    expect(h.guestPosts()).toHaveLength(1);
  });

  it("403 도 같은 경로로 회복한다 (AuthenticationEntryPoint 없던 백엔드 대응)", async () => {
    const h = await setup();
    localStorage.setItem(TOKEN_KEY, "expired");
    h.queue.push({ status: 403 }, { status: 200, data: { ok: 1 } });

    const res = await h.api.get("/api/content/topics");

    expect(res.data).toEqual({ ok: 1 });
    expect(authHeaderOf(h.requests[1])).toBe("Bearer token-1");
  });

  it("재발급된 토큰으로 disclaimer 동의를 다시 받는다 — 재시도가 451 로 죽지 않도록", async () => {
    const h = await setup();
    localStorage.setItem(TOKEN_KEY, "expired");
    localStorage.setItem(DISCLAIMER_KEY, "true");
    h.queue.push({ status: 401 }, { status: 200 });

    await h.api.get("/api/content/topics");

    expect(h.disclaimerPosts()).toHaveLength(1);
    expect(h.disclaimerPosts()[0].auth).toBe("Bearer token-1");
  });

  it("재시도까지 실패하면 그대로 표면화된다 — 재시도는 1회뿐", async () => {
    const h = await setup();
    h.queue.push({ status: 403 }, { status: 403 });

    await expect(h.api.get("/api/values/me")).rejects.toMatchObject({
      response: { status: 403 },
    });
    // 진짜 권한 문제일 때 무한 재발급 루프에 빠지지 않는다.
    expect(h.requests).toHaveLength(2);
  });

  it("401 인데 재발급마저 실패하면 원래 에러를 던진다", async () => {
    const h = await setup({
      guest: (attempt) => {
        if (attempt === 1)
          return { data: { token: "token-1", userId: "user-1" } };
        throw new Error("auth 서버 다운");
      },
    });
    await h.api.get("/api/content/topics"); // 1회차 발급
    h.queue.push({ status: 401 });

    await expect(h.api.get("/api/content/topics")).rejects.toMatchObject({
      response: { status: 401 },
    });
    // 재시도를 보내지 않는다 (요청 2건은 최초 2번의 GET 뿐).
    expect(h.requests).toHaveLength(2);
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });

  it("500 등 다른 상태는 그대로 던지고 토큰도 건드리지 않는다", async () => {
    const h = await setup();
    localStorage.setItem(TOKEN_KEY, "good");
    h.queue.push({ status: 500 });

    await expect(h.api.get("/api/content/topics")).rejects.toMatchObject({
      response: { status: 500 },
    });
    expect(h.requests).toHaveLength(1);
    expect(localStorage.getItem(TOKEN_KEY)).toBe("good");
  });

  it("응답이 없는 네트워크 에러는 재발급 없이 그대로 던진다", async () => {
    const h = await setup();
    localStorage.setItem(TOKEN_KEY, "good");
    h.api.defaults.adapter = (() =>
      Promise.reject(new Error("Network Error"))) as never;

    await expect(h.api.get("/api/content/topics")).rejects.toThrow(
      "Network Error",
    );
    expect(localStorage.getItem(TOKEN_KEY)).toBe("good");
  });
});

describe("세션 조회·폐기", () => {
  it("getStoredUserId 는 발급된 userId 를 노출하고 폐기 후엔 null", async () => {
    const h = await setup();
    expect(h.getStoredUserId()).toBeNull();

    await h.api.get("/api/content/topics");
    expect(h.getStoredUserId()).toBe("user-1");

    h.resetGuestSession();
    expect(h.getStoredUserId()).toBeNull();
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull();
  });
});

describe("SSR (window 없음)", () => {
  it("서버에서는 localStorage 를 건드리지 않는다", async () => {
    const h = await setup();
    vi.stubGlobal("window", undefined);

    expect(h.getStoredUserId()).toBeNull();
    expect(() => h.resetGuestSession()).not.toThrow();
  });

  it("서버 렌더 중 요청에는 토큰을 발급하지도 붙이지도 않는다", async () => {
    const h = await setup();
    vi.stubGlobal("window", undefined);

    await h.api.get("/api/content/topics");

    expect(h.guestPosts()).toHaveLength(0);
    expect(authHeaderOf(h.requests[0])).toBeUndefined();
  });
});
