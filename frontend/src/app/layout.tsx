import "./globals.css";
import type { Metadata } from "next";
import { Providers } from "./providers";
import { CrisisFooter } from "@/components/CrisisFooter";

export const metadata: Metadata = {
  title: "Lemuel XR — 절망에 대비하는 영적 단련",
  description:
    "성경 4인의 절망 극복 스토리(VR) + 일상 7가지 가치 습관(AR) — 자살예방 교육 콘텐츠. 임상 진단·치료 도구 아님.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="ko">
      <body className="pb-12">
        <Providers>{children}</Providers>
        <CrisisFooter />
      </body>
    </html>
  );
}
