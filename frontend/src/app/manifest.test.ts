import { describe, expect, it } from "vitest";
import manifest from "./manifest";
import { viewport } from "./layout";

/**
 * PWA manifest 는 "홈화면에 설치되는가" 를 정하는 계약이다. 틀려도 웹에서는
 * 아무 증상이 없어서, 설치한 사용자만 조용히 다른 앱을 쓰게 된다.
 *
 * 특히 `display: "standalone"` 은 파일 상단 주석대로 layout.tsx 의
 * viewportFit:"cover" · globals.css 의 env(safe-area-inset-bottom) 과 한 벌이다.
 * 셋 중 하나만 빠지면 위기 상담 푸터가 iOS 홈 인디케이터에 깔린다 —
 * 이 앱에서 가장 급한 UI 가 손가락이 닿지 않는 자리로 간다는 뜻이라
 * 그 결합을 여기서 교차 검증한다.
 */
describe("PWA manifest", () => {
  const m = manifest();

  it("설치에 필요한 최소 필드가 다 있다", () => {
    expect(m.name).toContain("Lemuel XR");
    expect(m.short_name).toBe("Lemuel XR");
    expect(m.start_url).toBe("/");
    expect(m.scope).toBe("/");
    expect(m.lang).toBe("ko");
  });

  it("설치 앱은 세로 고정 standalone 이다", () => {
    // 이 앱은 한 손 세로 사용을 전제로 자막·버튼 폭을 잡았다.
    expect(m.display).toBe("standalone");
    expect(m.orientation).toBe("portrait");
  });

  it("standalone 은 layout 의 viewportFit:cover 와 한 벌이어야 한다", () => {
    // 하나만 남으면 env(safe-area-inset-*) 이 전부 0 이 되고 안전영역 처리가
    // *조용히 아무것도 안 한다*. 그 조합을 코드로 못 박는다.
    expect(m.display).toBe("standalone");
    expect(viewport.viewportFit).toBe("cover");
  });

  it("스플래시 색이 layout 의 themeColor 와 같은 값이다", () => {
    // 다르면 앱 실행 순간 색이 한 번 튄다 (스플래시 → 첫 페인트).
    expect(m.background_color).toBe("#1a1d24");
    expect(m.theme_color).toBe("#1a1d24");
    expect(viewport.themeColor).toBe(m.theme_color);
  });

  it("설명에 임상 도구가 아니라는 고지가 들어간다", () => {
    // 자살예방 교육 콘텐츠라 스토어/설치 카드 수준에서도 면책이 필요하다.
    expect(m.description).toContain("자살예방");
    expect(m.description).toMatch(/진단|치료/);
    expect(m.description).toContain("아닙니다");
  });

  describe("아이콘", () => {
    const icons = m.icons ?? [];

    it("안드로이드 설치 배너가 요구하는 192·512 any 아이콘이 있다", () => {
      // 512 any 가 없으면 Chrome 은 설치 프롬프트를 아예 띄우지 않는다.
      const any = icons.filter((i) => i.purpose === "any");
      expect(any.map((i) => i.sizes)).toEqual(
        expect.arrayContaining(["192x192", "512x512"]),
      );
    });

    it("maskable 아이콘이 따로 선언돼 있다", () => {
      // 없으면 안드로이드가 아이콘 둘레에 흰 배경을 깔아 버린다.
      const maskable = icons.filter((i) => i.purpose === "maskable");
      expect(maskable).toHaveLength(1);
      expect(maskable[0].sizes).toBe("512x512");
    });

    it("모든 아이콘이 png 이고 생성기가 쓰는 /icons 아래를 가리킨다", () => {
      // 아이콘 바이너리는 scripts/gen_frontend_icons.py 가 만든다.
      // 경로가 어긋나면 404 아이콘이 되고, 설치 배너는 그냥 안 뜬다.
      expect(icons.length).toBeGreaterThanOrEqual(3);
      for (const i of icons) {
        expect(i.type).toBe("image/png");
        expect(i.src).toMatch(/^\/icons\/icon-\d+\.png$/);
      }
    });
  });

  it("호출할 때마다 같은 내용을 준다 — 순수 함수", () => {
    expect(manifest()).toEqual(m);
  });
});
