import "./globals.css";
import type { Metadata, Viewport } from "next";
import { Providers } from "./providers";
import { CrisisFooter } from "@/components/CrisisFooter";

export const metadata: Metadata = {
  title: "Lemuel XR — 절망에 대비하는 영적 단련",
  description:
    "성경 4인의 절망 극복 스토리(VR) + 일상 7가지 가치 습관(AR) — 자살예방 교육 콘텐츠. 임상 진단·치료 도구 아님.",
  // 홈화면 설치 시 iOS 는 manifest 의 icons 를 보지 않는다 — apple-touch-icon 만 본다.
  appleWebApp: {
    capable: true,
    title: "Lemuel XR",
    statusBarStyle: "black-translucent",
  },
  icons: { apple: "/icons/apple-touch-icon.png" },
};

/**
 * `viewportFit: "cover"` 가 없으면 iOS 는 노치·홈 인디케이터 영역을 통째로 letterbox
 * 로 비워 버리고, `env(safe-area-inset-*)` 은 전부 0 을 준다. 즉 이 한 줄이 없으면
 * `globals.css` 의 안전영역 처리가 **조용히 아무것도 안 한다** — 화면은 멀쩡해 보인다.
 *
 * `userScalable` 은 건드리지 않는다(기본 허용). 확대를 막는 것은 저시력 사용자에게
 * 직접적인 배제이고, 이 앱의 사용자층에서 그 대가가 크다.
 */
export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
  themeColor: "#1a1d24",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="ko">
      {/*
        하단 여백 클래스가 여기 없는 것이 의도다. 예전에는 `pb-12`(48px) 로 위기 푸터가
        덮는 자리를 상수로 짐작했는데, 그 푸터는 접힘/펼침·화면 폭에 따라 33px ~ 205px
        사이에서 변한다 — 펼치면 본문 하단 157px 이 깔렸고, iPhone SE 폭(320px)에서는
        접힌 상태(49px)조차 48px 를 넘겨 1px 이 가려졌다(2026-08-12 실측).
        지금은 `CrisisFooter` 가 흐름 안에 같은 높이의 사본을 심어 자리를 스스로 잡는다.
        여기에 다시 상수 여백을 넣지 말 것 — 그만큼 두 번 밀린다.
      */}
      <body>
        <Providers>{children}</Providers>
        <CrisisFooter />
      </body>
    </html>
  );
}
