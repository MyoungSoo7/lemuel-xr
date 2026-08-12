import type { MetadataRoute } from "next";

/**
 * PWA manifest — 홈화면 설치를 가능하게 하는 최소 계약.
 *
 * `display: "standalone"` 이면 브라우저 주소창이 사라진다. 그 순간 **하단 안전영역이
 * 앱 책임이 된다** — Safari 의 하단 바가 가려주던 홈 인디케이터 영역을 이제 우리가
 * 비워야 하고, 그래서 `layout.tsx` 의 `viewportFit: "cover"` 와
 * `globals.css` 의 `env(safe-area-inset-bottom)` 이 이 파일과 한 벌이다.
 * 셋 중 하나만 빠지면 위기 상담 푸터가 홈 인디케이터에 깔린다.
 *
 * 아이콘은 `scripts/gen_frontend_icons.py` 가 만든다 — 색이 globals.css 의 @theme 와
 * 같은 숫자여야 해서, 손으로 그린 바이너리를 두지 않는다.
 */
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Lemuel XR — 절망에 대비하는 영적 단련",
    short_name: "Lemuel XR",
    description:
      "성경 인물의 절망 극복 스토리 + 일상 가치 습관. 자살예방 교육 콘텐츠이며 임상 진단·치료 도구가 아닙니다.",
    start_url: "/",
    scope: "/",
    display: "standalone",
    orientation: "portrait",
    background_color: "#1a1d24",
    theme_color: "#1a1d24",
    lang: "ko",
    icons: [
      {
        src: "/icons/icon-192.png",
        sizes: "192x192",
        type: "image/png",
        purpose: "any",
      },
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "any",
      },
      // maskable 은 플랫폼이 바깥을 잘라내므로 같은 파일을 재사용한다 — 생성기가 내용물을
      // 가운데 80% 안에 두기 때문에 잘려도 해와 지평선이 남는다.
      {
        src: "/icons/icon-512.png",
        sizes: "512x512",
        type: "image/png",
        purpose: "maskable",
      },
    ],
  };
}
