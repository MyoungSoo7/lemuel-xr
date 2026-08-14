import "@testing-library/jest-dom/vitest";
import { cleanup } from "@testing-library/react";
import { afterEach, vi } from "vitest";

afterEach(() => {
  cleanup();
  localStorage.clear();
  sessionStorage.clear();
  vi.clearAllMocks();
});

// jsdom 이 구현하지 않는 브라우저 API 들. 없으면 컴포넌트가 렌더 도중 죽는데,
// 그 실패는 "테스트가 잘못됐다"로 보이지 "환경이 없다"로 안 보여서 시간을 먹는다.
if (!window.matchMedia) {
  window.matchMedia = ((query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  })) as typeof window.matchMedia;
}

if (!window.scrollTo) {
  window.scrollTo = (() => {}) as typeof window.scrollTo;
}

// HTMLMediaElement.play 는 jsdom 에서 "Not implemented" 를 던진다.
// 나레이션 오디오를 만지는 코드가 여럿이라 전역에서 막아 둔다.
Object.defineProperty(window.HTMLMediaElement.prototype, "play", {
  configurable: true,
  writable: true,
  value: vi.fn().mockResolvedValue(undefined),
});
Object.defineProperty(window.HTMLMediaElement.prototype, "pause", {
  configurable: true,
  writable: true,
  value: vi.fn(),
});
Object.defineProperty(window.HTMLMediaElement.prototype, "load", {
  configurable: true,
  writable: true,
  value: vi.fn(),
});

if (!global.URL.createObjectURL) {
  global.URL.createObjectURL = vi.fn(() => "blob:mock");
  global.URL.revokeObjectURL = vi.fn();
}
