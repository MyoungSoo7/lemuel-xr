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

async function proxy(req: NextRequest, ctx: { params: Promise<{ path: string[] }> }) {
  const { path } = await ctx.params;
  const target = `${BACKEND()}/api/${path.join("/")}${req.nextUrl.search}`;
  const init: RequestInit = {
    method: req.method,
    headers: {
      ...Object.fromEntries(req.headers),
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
      "content-type": upstream.headers.get("content-type") ?? "application/json",
    },
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const DELETE = proxy;
export const PATCH = proxy;
