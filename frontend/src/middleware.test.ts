import { describe, expect, it } from "vitest";
import { NextRequest } from "next/server";
import { config, middleware } from "./middleware";

/**
 * 이 미들웨어가 하는 일은 하나뿐이다 — HTML/RSC 응답에 no-store 를 박아
 * Cloudflare 가 *옛 chunk 파일명을 참조하는 HTML* 을 1년짜리로 물고 있지
 * 못하게 하는 것(파일 상단 주석 참조). 실패해도 화면은 멀쩡히 뜬다.
 * 며칠 뒤에 "왜 옛 번들이 돌지" 로 나타나는 종류의 고장이라, 헤더 이름과
 * 값을 문자열 단위로 못 박아 둔다.
 *
 * next/server 를 모킹하지 않고 실물로 쓴다 — 여기서 재고 싶은 것은
 * "NextResponse.next() 가 만든 진짜 응답에 헤더가 붙는가" 이지
 * "우리가 만든 가짜 객체의 set 이 호출됐는가" 가 아니다.
 */

const CACHE_HEADERS = [
  "cache-control",
  "cdn-cache-control",
  "cloudflare-cdn-cache-control",
];

function req(path: string) {
  return new NextRequest(new Request(`https://lemuel.example${path}`));
}

describe("middleware — 캐시 회피 헤더", () => {
  it("일반 페이지 요청에 3종 no-store 헤더를 모두 붙인다", () => {
    const res = middleware(req("/"));

    // 브라우저/Next 캐시 · 일반 CDN · Cloudflare 전용 — 셋이 각각 다른 계층이라
    // 하나라도 빠지면 그 계층에서만 옛 HTML 이 살아남는다.
    expect(res.headers.get("Cache-Control")).toBe(
      "no-store, max-age=0, must-revalidate",
    );
    expect(res.headers.get("CDN-Cache-Control")).toBe("no-store");
    expect(res.headers.get("Cloudflare-CDN-Cache-Control")).toBe("no-store");
  });

  it("중첩 경로·쿼리스트링이 붙은 페이지에도 붙는다", () => {
    const res = middleware(req("/joseph/scene/3?step=2"));
    expect(res.headers.get("Cache-Control")).toContain("no-store");
  });

  it.each(["/_next/static/chunks/main.js", "/images/hero.png", "/favicon.ico"])(
    "해시가 박힌 정적 자산 %s 는 캐시 헤더를 건드리지 않는다",
    (path) => {
      // 파일명에 hash 가 있어 캐싱이 안전한 자산까지 no-store 를 박으면
      // 매 요청마다 번들을 다시 받게 된다 — 캐시 회피의 대가가 뒤집힌다.
      const res = middleware(req(path));
      for (const h of CACHE_HEADERS) {
        expect(res.headers.get(h)).toBeNull();
      }
    },
  );

  it("경로 *중간* 에 있는 images 는 정적 자산이 아니므로 no-store 대상이다", () => {
    // startsWith 가드라서 접두사일 때만 통과해야 한다. 여기서 새면
    // /blog/images-guide 같은 HTML 이 조용히 캐싱된다.
    const res = middleware(req("/guide/images/intro"));
    expect(res.headers.get("Cache-Control")).toContain("no-store");
  });

  it("응답 자체는 통과(next)다 — 리다이렉트·차단이 아니다", () => {
    // 이 미들웨어가 실수로 응답을 가로채면 앱 전체가 죽는다.
    const res = middleware(req("/values"));
    expect(res.status).toBe(200);
    expect(res.headers.get("x-middleware-next")).toBe("1");
  });
});

describe("middleware config.matcher", () => {
  // matcher 는 런타임에 Next 가 평가하는 정규식이다. 코드 안의 startsWith 가드와
  // *같은 경로 집합* 을 빼야 하는데, 둘이 어긋나면 한쪽만 고쳤을 때 조용히 새거나
  // 조용히 막힌다. 여기서 실제로 정규식을 돌려 교차 검증한다.
  const re = new RegExp(`^${config.matcher[0]}$`);

  it.each(["/", "/values", "/joseph", "/api/emotion/classify"])(
    "%s 는 미들웨어 대상이다",
    (path) => {
      expect(re.test(path)).toBe(true);
    },
  );

  it.each([
    "/_next/static/chunks/main.js",
    "/_next/image?url=x",
    "/images/hero.png",
    "/favicon.ico",
  ])("%s 는 matcher 단계에서 이미 제외된다", (path) => {
    expect(re.test(path)).toBe(false);
  });
});
