"use client";

import { useTtsNarration } from "@/lib/hooks/useTtsNarration";

/**
 * "🔊 듣기" 나레이션 버튼.
 *
 * 주어진 텍스트를 TTS 로 합성해 재생한다. 재생 중이면 "⏸ 정지".
 *
 * ── 합성 대기 ──
 * 캐시에 없는 문장은 서버가 워커에서 합성하므로 첫 재생까지 수십 초가 걸릴 수 있다.
 * 그동안 버튼은 "준비 중…" 을 보여주되 **비활성화하지 않는다** — 기다리기 싫은
 * 사용자가 눌러서 취소할 수 있어야 한다. (한 번 합성된 문장은 이후 즉시 재생된다.)
 *
 * ── graceful degradation ──
 * TTS 사이드카가 없거나(로컬) 다운되면 합성이 실패한다. 그 경우 버튼은 로딩 후
 * *조용히* 비활성(disabled)되거나 숨겨진다. 에러 UI 는 절대 띄우지 않으며,
 * 앱은 어떤 경우에도 깨지지 않는다.
 *
 * 접근성(고령자 대상):
 *  - 충분히 큰 터치 타깃 (min 44px)
 *  - 상태에 맞는 aria-label
 *  - aria-live 로 상태 변화 안내
 */
export function NarrationAudioButton({
  text,
  voiceId,
  label = "듣기",
  className = "",
  /**
   * 사용 불가 시 동작:
   *  - "disable" (기본): 회색 disabled 버튼 유지 (자리는 남김)
   *  - "hide": 아예 렌더 제거
   */
  onUnavailable = "disable",
}: {
  text: string;
  voiceId?: string;
  label?: string;
  className?: string;
  onUnavailable?: "disable" | "hide";
}) {
  const { status, unavailable, synthesizing, toggle, stop } =
    useTtsNarration(voiceId);

  const hasText = Boolean(text && text.trim());
  if (!hasText) return null;
  if (unavailable && onUnavailable === "hide") return null;

  const isPlaying = status === "playing";
  const isLoading = status === "loading";
  // 합성 대기는 취소 가능해야 하므로 disabled 에 넣지 않는다.
  const disabled = unavailable || isLoading;

  const ariaLabel = unavailable
    ? "음성 안내 사용 불가"
    : isPlaying
      ? `${label} 정지`
      : synthesizing
        ? "음성 준비 중 — 눌러서 취소"
        : isLoading
          ? "음성 불러오는 중"
          : `${label} — 음성으로 듣기`;

  return (
    <button
      type="button"
      onClick={() => (synthesizing ? stop() : void toggle(text))}
      disabled={disabled}
      aria-label={ariaLabel}
      aria-live="polite"
      className={
        // min-h/min-w 44px 이상 — 고령자 터치 타깃. text-base 로 충분히 큰 글자.
        "inline-flex items-center gap-1.5 min-h-[44px] px-4 py-2 rounded-full " +
        "text-base font-medium border transition select-none " +
        (disabled
          ? "border-[var(--color-primary)]/15 text-[var(--color-warm)]/30 cursor-not-allowed"
          : "border-[var(--color-primary)]/40 text-[var(--color-primary)] " +
            "hover:bg-[var(--color-primary)]/10 active:bg-[var(--color-primary)]/20") +
        (className ? " " + className : "")
      }
    >
      <span aria-hidden="true">
        {unavailable
          ? "🔇"
          : isPlaying
            ? "⏸"
            : isLoading || synthesizing
              ? "⏳"
              : "🔊"}
      </span>
      <span>
        {unavailable
          ? label
          : isPlaying
            ? "정지"
            : synthesizing
              ? "준비 중…"
              : isLoading
                ? "불러오는 중"
                : label}
      </span>
    </button>
  );
}

export default NarrationAudioButton;
