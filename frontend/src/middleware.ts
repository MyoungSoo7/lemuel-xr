import { NextRequest, NextResponse } from "next/server";

/**
 * Cloudflare / Next.js 페이지 캐시 회피 — HTML 응답에 no-cache 강제.
 *
 * Next.js 의 RSC 페이지가 기본적으로 s-maxage=31536000 (1년) 으로
 * Cloudflare 에 캐싱되면서 *옛 chunk 파일명* 을 참조해
 * 클라이언트가 옛 번들 실행 → wrap 없는 decision 직렬화 → 백엔드 500.
 *
 * 정적 자산 (_next/static, /images) 은 캐싱 OK (filename 에 hash 가 있어 안전).
 * HTML/RSC 만 no-cache.
 */
export function middleware(req: NextRequest) {
  const res = NextResponse.next();
  const path = req.nextUrl.pathname;
  if (
    path.startsWith("/_next/static") ||
    path.startsWith("/images") ||
    path.startsWith("/favicon")
  ) {
    return res;
  }
  res.headers.set("Cache-Control", "no-store, max-age=0, must-revalidate");
  res.headers.set("CDN-Cache-Control", "no-store");
  res.headers.set("Cloudflare-CDN-Cache-Control", "no-store");
  return res;
}

export const config = {
  matcher: ["/((?!_next/static|_next/image|images|favicon).*)"],
};
