import { describe, expect, it } from "vitest";
import { render, screen } from "@testing-library/react";
import RootLayout, { metadata, viewport } from "./layout";

/**
 * 루트 레이아웃이 보장해야 하는 것은 몇 개 안 되지만 전부 안전 요건이다:
 *
 *  1) 위기 상담 푸터가 *모든* 페이지에 붙는다 (docs/safety-guidelines.md §1).
 *     여기서 빠지면 어떤 페이지에서도 빠진다. 그리고 화면은 멀쩡해 보인다.
 *  2) viewport 의 viewportFit:"cover" — 이게 없으면 env(safe-area-inset-*) 이
 *     전부 0 을 주고, globals.css 의 안전영역 처리가 조용히 무효가 된다.
 *  3) body 에 상수 하단 여백이 되살아나지 않는다.
 *
 * jsdom 렌더 시 주의 — React 19 는 <html>/<body> 를 컨테이너 안에 새로 만들지 않고
 * **실제 document 의 것에 병합** 한다. 그래서 lang 은 document.documentElement 에서,
 * children 은 RTL 컨테이너에서 확인해야 한다 (2026-08 실측).
 */
function renderLayout(children: React.ReactNode) {
  return render(<RootLayout>{children}</RootLayout>);
}

describe("RootLayout", () => {
  it("html lang 이 ko 다 — 스크린 리더 발음과 폰트 선택이 여기서 갈린다", () => {
    renderLayout(<p>본문</p>);
    expect(document.documentElement).toHaveAttribute("lang", "ko");
  });

  it("children 을 문서 흐름에 그대로 그린다", () => {
    renderLayout(<main data-testid="page">페이지 내용</main>);
    expect(screen.getByTestId("page")).toHaveTextContent("페이지 내용");
  });

  it("모든 페이지에 위기 상담 푸터가 붙는다", () => {
    renderLayout(<p>본문</p>);
    // CrisisFooter 는 자리지기 사본까지 두 벌을 그린다. 접근성 트리에 남는 것은
    // 한 벌(region) 이어야 스크린 리더가 상담 자원을 두 번 읽지 않는다.
    expect(
      screen.getByRole("region", { name: "위기 상담 자원 안내" }),
    ).toBeInTheDocument();
  });

  it("푸터는 children 보다 *뒤* 에 온다 — 본문을 밀어내지 않고 덮는다", () => {
    renderLayout(<main data-testid="page">페이지 내용</main>);
    const page = screen.getByTestId("page");
    const footer = screen.getByRole("region", { name: "위기 상담 자원 안내" });
    // 순서가 뒤집히면 fixed 푸터가 본문 위 z-index 싸움에 지거나,
    // 자리지기 사본이 본문 *위* 로 올라가 첫 화면이 빈 줄로 시작한다.
    expect(
      page.compareDocumentPosition(footer) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("body 에 상수 하단 여백을 다시 넣지 않는다", () => {
    // 예전의 pb-12(48px) 는 푸터 실제 높이(33~205px)와 안 맞아 본문을 깔았고,
    // 지금은 CrisisFooter 의 자리지기 사본이 스스로 높이를 잡는다.
    // 여기에 상수 여백이 부활하면 그만큼 *두 번* 밀린다 (파일 주석 참조).
    renderLayout(<p>본문</p>);
    expect(document.body.className).not.toMatch(/\bpb-/);
  });
});

describe("metadata", () => {
  it("제목·설명에 임상 도구가 아니라는 고지가 들어간다", () => {
    expect(metadata.title).toContain("Lemuel XR");
    expect(metadata.description).toContain("자살예방");
    expect(metadata.description).toMatch(/임상 진단·치료 도구 아님/);
  });

  it("iOS 홈화면 설치용 apple-touch-icon 을 따로 준다", () => {
    // iOS 는 설치 시 manifest 의 icons 를 보지 않는다 — 이 항목만 본다.
    expect(metadata.icons).toEqual({ apple: "/icons/apple-touch-icon.png" });
    expect(metadata.appleWebApp).toMatchObject({
      capable: true,
      title: "Lemuel XR",
      statusBarStyle: "black-translucent",
    });
  });
});

describe("viewport", () => {
  it("viewportFit 이 cover 다 — 없으면 안전영역 CSS 가 조용히 무효가 된다", () => {
    expect(viewport.viewportFit).toBe("cover");
    expect(viewport.width).toBe("device-width");
    expect(viewport.initialScale).toBe(1);
  });

  it("확대를 막지 않는다 — 저시력 사용자를 배제하지 않기 위한 의도적 공백", () => {
    expect(viewport.userScalable).toBeUndefined();
    expect(viewport.maximumScale).toBeUndefined();
  });
});
