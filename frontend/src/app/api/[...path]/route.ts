/**
 * Runtime proxy to lemuel-xr-backend.
 *
 * next.config.ts 의 rewrites() 는 *빌드 시점* 에 평가돼 BACKEND_INTERNAL_URL
 * 이 'http://localhost:8080' 으로 박힘. Route Handler 는 *요청 시점* 에
 * process.env 를 읽으므로 standalone 컨테이너의 런타임 env 가 반영됨.
 */
import type { NextRequest } from "next/server";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

const BACKEND = () =>
  process.env.BACKEND_INTERNAL_URL ?? "http://lemuel-xr-backend:8080";

/**
 * 브라우저가 절대 부를 일 없는, 서비스 간 전용 경로.
 * backend 는 /api/internal/** 를 ROLE_INTERNAL 로 막지만 *rate limit 은 면제* 한다
 * (RateLimitFilter.limitFor → 0). 이 프록시가 다 넘겨주면 인터넷에서 무제한으로
 * 토큰을 때려볼 수 있게 되므로, 여기서 먼저 끊는다. 404 로 존재 자체를 숨긴다.
 */
const BLOCKED_PREFIXES = ["internal", "actuator"];

async function proxy(
  req: NextRequest,
  ctx: { params: Promise<{ path: string[] }> },
) {
  const { path } = await ctx.params;
  if (BLOCKED_PREFIXES.includes((path[0] ?? "").toLowerCase())) {
    return new Response(null, { status: 404 });
  }
  const target = `${BACKEND()}/api/${path.join("/")}${req.nextUrl.search}`;
  const headers = Object.fromEntries(req.headers);
  // 클라이언트가 내부 인증 헤더를 흉내내지 못하게 한다 (헤더 이름은 소문자로 정규화돼 옴).
  delete headers["x-internal-token"];
  const init: RequestInit = {
    method: req.method,
    headers: {
      ...headers,
      // host 헤더 그대로 보내면 backend 가 헷갈림
      host: new URL(BACKEND()).host,
    },
    redirect: "manual",
  };
  if (!["GET", "HEAD"].includes(req.method)) {
    init.body = await req.arrayBuffer();
  }
  const upstream = await fetch(target, init);
  // 응답 body + headers 그대로 전달
  return new Response(upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: {
      "content-type":
        upstream.headers.get("content-type") ?? "application/json",
    },
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const DELETE = proxy;
export const PATCH = proxy;
