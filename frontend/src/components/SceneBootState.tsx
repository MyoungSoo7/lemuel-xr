"use client";

import { resetGuestSession } from "@/lib/api/client";

/**
 * 인물 세션이 아직 안 열린 동안 보여주는 화면.
 *
 * 원래 7개 인물 페이지가 전부 `if (!scene) return <p>세션 시작 중...</p>` 하나만 두고
 * 실패 분기가 없었다. start 뮤테이션이 에러로 끝나면 useEffect 의 재시도 가드
 * (`!start.isError`) 때문에 다시 호출도 안 하고, 화면은 "세션 시작 중..." 에서
 * 영원히 멈춰 있었다 — 사용자에겐 그냥 먹통이다(2026-08-06 /joseph 제보).
 *
 * 실패했으면 실패했다고 말하고, 다시 시도할 손잡이를 준다.
 */
export function SceneBootState({
  isError,
  error,
  onRetry,
}: {
  isError: boolean;
  error: unknown;
  onRetry: () => void;
}) {
  if (!isError) {
    return (
      <main className="min-h-screen flex items-center justify-center px-6">
        <p className="text-[var(--color-warm)]/60">세션 시작 중...</p>
      </main>
    );
  }

  const status = statusOf(error);
  const isAuth = status === 401 || status === 403;

  return (
    <main className="min-h-screen flex flex-col items-center justify-center gap-4 px-6 text-center">
      <p className="text-[var(--color-warm)]">
        {isAuth
          ? "세션이 만료됐습니다. 다시 시작해 주세요."
          : "세션을 시작하지 못했습니다."}
      </p>
      {status !== null && (
        <p className="text-sm text-[var(--color-warm)]/50">
          오류 코드 {status}
        </p>
      )}
      <button
        type="button"
        onClick={() => {
          // 만료된 게스트 토큰이 localStorage 에 박혀 있으면 재시도해도 같은 결과다. 버리고 간다.
          if (isAuth) resetGuestSession();
          onRetry();
        }}
        className="inline-flex items-center justify-center min-h-11 rounded-lg border border-[var(--color-warm)]/30 px-5 py-2 text-[var(--color-warm)] hover:bg-[var(--color-warm)]/10"
      >
        다시 시도
      </button>
    </main>
  );
}

/** axios 에러든 아니든 안전하게 HTTP 상태만 꺼낸다. */
function statusOf(error: unknown): number | null {
  if (typeof error !== "object" || error === null) return null;
  const res = (error as { response?: { status?: unknown } }).response;
  return typeof res?.status === "number" ? res.status : null;
}
