import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { NextRequest } from "next/server";
import { DELETE, GET, PATCH, POST, PUT } from "./route";

/**
 * 이 프록시는 앱의 *유일한* 백엔드 통로다 (client.ts 의 axios baseURL 이 "" 이라
 * 모든 호출이 same-origin /api/** 로 나가고 전부 여기로 들어온다).
 * 조용히 깨지면 화면은 뜨는데 아무 데이터도 안 들어오는 상태가 된다.
 *
 * 그래서 재는 축은 넷이다:
 *   1) 어디로 보내는가 (target URL — 경로·쿼리·env 오버라이드)
 *   2) 무엇을 지우고 무엇을 덮어쓰는가 (x-internal-token 제거, host 교체)
 *   3) 무엇을 되돌려 주는가 (status / statusText / content-type / body)
 *   4) 내부 전용 경로를 정말 끊는가 (rate limit 면제 구멍)
 *
 * next/server 는 실물을 쓴다. NextRequest 의 헤더 정규화·nextUrl 파싱이
 * 이 코드가 기대는 동작의 절반이라 가짜로 바꾸면 재는 게 없어진다.
 */

const DEFAULT_BACKEND = "http://lemuel-xr-backend:8080";

function makeReq(
  url: string,
  init?: { method?: string; headers?: Record<string, string>; body?: string },
) {
  return new NextRequest(new Request(`https://lemuel.example${url}`, init));
}

function ctx(...path: string[]) {
  return { params: Promise.resolve({ path }) };
}

/** upstream 응답 대역. body 를 문자열로 주면 ReadableStream 으로 나간다. */
function upstream(
  body: string | null,
  init?: {
    status?: number;
    statusText?: string;
    headers?: Record<string, string>;
  },
) {
  return new Response(body, init);
}

let fetchMock: ReturnType<typeof vi.fn>;

beforeEach(() => {
  fetchMock = vi.fn().mockResolvedValue(upstream("{}"));
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.unstubAllEnvs();
});

/** fetch 가 실제로 받은 (url, init) 쌍. */
function callArgs() {
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  return { url, init, headers: init.headers as Record<string, string> };
}

describe("프록시 대상 URL", () => {
  it("기본 백엔드는 클러스터 내부 서비스명이다", async () => {
    await GET(makeReq("/api/topics"), ctx("topics"));
    expect(callArgs().url).toBe(`${DEFAULT_BACKEND}/api/topics`);
  });

  it("BACKEND_INTERNAL_URL 은 *요청 시점* 에 읽힌다 — 이게 이 파일의 존재 이유", async () => {
    // next.config 의 rewrites() 는 빌드 시점에 굳어 localhost:8080 이 박혔다.
    // 컨테이너 런타임 env 가 반영되는지가 핵심 계약이라 실제로 바꿔 보고 잰다.
    vi.stubEnv("BACKEND_INTERNAL_URL", "http://backend.prod.svc:9090");
    await GET(makeReq("/api/topics"), ctx("topics"));
    expect(callArgs().url).toBe("http://backend.prod.svc:9090/api/topics");
  });

  it("catch-all 세그먼트를 슬래시로 다시 이어 붙인다", async () => {
    await GET(
      makeReq("/api/game/session/42/scene"),
      ctx("game", "session", "42", "scene"),
    );
    expect(callArgs().url).toBe(`${DEFAULT_BACKEND}/api/game/session/42/scene`);
  });

  it("쿼리스트링을 그대로 옮긴다", async () => {
    // 콘텐츠 목록 페이징이 여기 얹혀 있어, 빠지면 항상 1페이지만 나온다.
    await GET(makeReq("/api/topics?page=2&size=20"), ctx("topics"));
    expect(callArgs().url).toBe(`${DEFAULT_BACKEND}/api/topics?page=2&size=20`);
  });
});

describe("내부 전용 경로 차단", () => {
  // backend 의 RateLimitFilter 는 /api/internal/** 을 rate limit 에서 면제한다.
  // 이 프록시가 넘겨주면 인터넷에서 무제한으로 두들길 수 있는 구멍이 된다.
  it.each([
    ["internal", ["internal", "token"]],
    ["actuator", ["actuator", "health"]],
  ])(
    "%s 로 시작하면 404 로 존재를 숨기고 백엔드를 부르지 않는다",
    async (_name, path) => {
      const res = await GET(makeReq(`/api/${path.join("/")}`), ctx(...path));

      expect(res.status).toBe(404);
      // 403 이 아니라 404 여야 한다 — 403 은 "여기 뭔가 있다" 를 알려 준다.
      expect(fetchMock).not.toHaveBeenCalled();
    },
  );

  it("대소문자를 섞어도 막힌다", async () => {
    const res = await GET(
      makeReq("/api/InTeRnAl/token"),
      ctx("InTeRnAl", "token"),
    );
    expect(res.status).toBe(404);
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("차단은 모든 메서드에 적용된다 — GET 만 막으면 의미가 없다", async () => {
    for (const handler of [POST, PUT, DELETE, PATCH]) {
      const res = await handler(
        makeReq("/api/internal/token", { method: "POST", body: "{}" }),
        ctx("internal", "token"),
      );
      expect(res.status).toBe(404);
    }
    expect(fetchMock).not.toHaveBeenCalled();
  });

  it("접두사가 겹치기만 하는 경로는 막지 않는다", async () => {
    // includes 가 아니라 정확 일치여야 한다. "internal-notes" 같은 공개 경로까지
    // 막으면 조용히 404 만 나는 기능이 생긴다.
    await GET(makeReq("/api/internals/x"), ctx("internals", "x"));
    expect(fetchMock).toHaveBeenCalledOnce();
  });

  it("세그먼트가 하나도 없어도 터지지 않는다", async () => {
    // path[0] 이 undefined 인 경우. Next 라우팅상 흔치 않지만, 여기서 던지면
    // 프록시 전체가 500 이 된다 — `?? ""` 가드가 실제로 도는지 확인한다.
    await GET(makeReq("/api/"), ctx());
    expect(callArgs().url).toBe(`${DEFAULT_BACKEND}/api/`);
  });

  it("차단 대상이 *첫* 세그먼트일 때만 막는다", async () => {
    await GET(makeReq("/api/content/internal"), ctx("content", "internal"));
    expect(fetchMock).toHaveBeenCalledOnce();
  });
});

describe("요청 헤더 처리", () => {
  it("클라이언트가 보낸 x-internal-token 을 지운다", async () => {
    // 이게 새면 브라우저가 내부 인증을 흉내낼 수 있다. 헤더 이름은 소문자로
    // 정규화돼 들어오므로 대문자로 보내도 같은 키에 걸려야 한다.
    await GET(
      makeReq("/api/topics", {
        headers: { "X-Internal-Token": "stolen-secret" },
      }),
      ctx("topics"),
    );
    const { headers } = callArgs();
    expect(Object.keys(headers)).not.toContain("x-internal-token");
    expect(JSON.stringify(headers)).not.toContain("stolen-secret");
  });

  it("Authorization 은 그대로 넘긴다 — 게스트 JWT 가 여기로 흐른다", async () => {
    await GET(
      makeReq("/api/values/me", {
        headers: { Authorization: "Bearer guest.jwt" },
      }),
      ctx("values", "me"),
    );
    expect(callArgs().headers.authorization).toBe("Bearer guest.jwt");
  });

  it("host 헤더를 백엔드 호스트로 덮어쓴다", async () => {
    // 원래 host(lemuel.example)를 그대로 보내면 백엔드가 자기 주소를 오해한다.
    await GET(makeReq("/api/topics"), ctx("topics"));
    expect(callArgs().headers.host).toBe("lemuel-xr-backend:8080");
  });

  it("host 덮어쓰기는 env 로 바뀐 백엔드를 따라간다", async () => {
    vi.stubEnv("BACKEND_INTERNAL_URL", "http://backend.prod.svc:9090");
    await GET(makeReq("/api/topics"), ctx("topics"));
    expect(callArgs().headers.host).toBe("backend.prod.svc:9090");
  });

  it("리다이렉트를 따라가지 않는다 (redirect: manual)", async () => {
    // 프록시가 302 를 자동으로 따라가면 인증 리다이렉트가 통째로 삼켜진다.
    await GET(makeReq("/api/topics"), ctx("topics"));
    expect(callArgs().init.redirect).toBe("manual");
  });
});

describe("메서드와 바디", () => {
  it("GET 은 바디를 만들지 않는다", async () => {
    await GET(makeReq("/api/topics"), ctx("topics"));
    const { init } = callArgs();
    expect(init.method).toBe("GET");
    expect(init.body).toBeUndefined();
  });

  it.each([
    ["POST", POST],
    ["PUT", PUT],
    ["PATCH", PATCH],
  ] as const)("%s 는 바디 바이트를 그대로 옮긴다", async (method, handler) => {
    const payload = JSON.stringify({ text: "오늘 너무 외롭고 지쳐있어" });
    await handler(
      makeReq("/api/emotion/classify", {
        method,
        body: payload,
        headers: { "content-type": "application/json" },
      }),
      ctx("emotion", "classify"),
    );

    const { init } = callArgs();
    expect(init.method).toBe(method);
    // 한글이 깨지지 않고 바이트 단위로 넘어가는지까지 본다 —
    // 감정 분류 입력이 전부 한글이라 인코딩이 뭉개지면 분류가 통째로 틀어진다.
    expect(new TextDecoder().decode(init.body as ArrayBuffer)).toBe(payload);
  });

  it("DELETE 는 바디가 없어도 빈 바이트로 통과한다", async () => {
    await DELETE(
      makeReq("/api/values/1", { method: "DELETE" }),
      ctx("values", "1"),
    );
    const { init } = callArgs();
    expect(init.method).toBe("DELETE");
    expect((init.body as ArrayBuffer).byteLength).toBe(0);
  });
});

describe("응답 전달", () => {
  it("바디를 그대로 흘려 보낸다", async () => {
    fetchMock.mockResolvedValue(
      upstream(JSON.stringify({ tier: "SEEDLING" }), {
        headers: { "content-type": "application/json" },
      }),
    );
    const res = await GET(makeReq("/api/values/me"), ctx("values", "me"));
    await expect(res.json()).resolves.toEqual({ tier: "SEEDLING" });
  });

  it.each([200, 201, 400, 401, 429, 500])(
    "상태코드 %i 를 그대로 전달한다",
    async (status) => {
      // 401/403 은 client.ts 의 재발급 인터셉터가, 429 는 rate limit UI 가 본다.
      // 여기서 뭉개면 그 분기들이 전부 죽는다.
      fetchMock.mockResolvedValue(upstream("{}", { status }));
      const res = await GET(makeReq("/api/topics"), ctx("topics"));
      expect(res.status).toBe(status);
    },
  );

  it("statusText 를 유지한다", async () => {
    fetchMock.mockResolvedValue(
      upstream("{}", {
        status: 451,
        statusText: "Unavailable For Legal Reasons",
      }),
    );
    const res = await GET(makeReq("/api/content/1"), ctx("content", "1"));
    expect(res.status).toBe(451);
    expect(res.statusText).toBe("Unavailable For Legal Reasons");
  });

  it("upstream content-type 을 따라간다", async () => {
    fetchMock.mockResolvedValue(
      upstream("data", { headers: { "content-type": "audio/mpeg" } }),
    );
    const res = await GET(makeReq("/api/tts/1"), ctx("tts", "1"));
    expect(res.headers.get("content-type")).toBe("audio/mpeg");
  });

  it("upstream 이 content-type 을 안 주면 application/json 으로 가정한다", async () => {
    const bare = new Response("{}");
    bare.headers.delete("content-type");
    fetchMock.mockResolvedValue(bare);

    const res = await GET(makeReq("/api/topics"), ctx("topics"));
    expect(res.headers.get("content-type")).toBe("application/json");
  });
});

describe("백엔드가 죽었을 때", () => {
  it("연결 실패를 삼키지 않고 그대로 던진다", async () => {
    // 현재 구현에는 try/catch 가 없다. 즉 백엔드가 내려가면 이 핸들러가 throw 하고
    // Next 가 500 을 만들어 준다 — 프록시가 만든 구조화된 오류 응답이 아니다.
    // 이 테스트는 "우아하게 처리한다" 가 아니라 *지금 실제로 이렇게 동작한다* 를
    // 고정한다. 바꾸려면 이 테스트가 먼저 빨개진다.
    fetchMock.mockRejectedValue(new TypeError("fetch failed"));
    await expect(GET(makeReq("/api/topics"), ctx("topics"))).rejects.toThrow(
      "fetch failed",
    );
  });
});

describe("응답 헤더 유실 (알려진 결함 고정)", () => {
  // 소스 주석은 "응답 body + headers 그대로 전달" 이라고 적혀 있지만 실제로는
  // content-type *하나만* 옮긴다. 아래 두 테스트는 그 간극을 못 박아 둔 것이다 —
  // 지금 동작이 옳아서가 아니라, 고칠 때 무엇이 바뀌는지 보이게 하려고.
  it("Set-Cookie 가 사라진다", async () => {
    fetchMock.mockResolvedValue(
      upstream("{}", { headers: { "set-cookie": "sid=abc; HttpOnly" } }),
    );
    const res = await GET(makeReq("/api/auth/guest"), ctx("auth", "guest"));
    expect(res.headers.get("set-cookie")).toBeNull();
  });

  it("302 의 Location 과 429 의 Retry-After 가 사라진다", async () => {
    fetchMock.mockResolvedValue(
      upstream("{}", { status: 429, headers: { "retry-after": "60" } }),
    );
    const res = await GET(
      makeReq("/api/emotion/classify"),
      ctx("emotion", "classify"),
    );
    expect(res.status).toBe(429);
    // 클라이언트는 언제 재시도해야 하는지 알 방법이 없어진다.
    expect(res.headers.get("retry-after")).toBeNull();
  });
});
