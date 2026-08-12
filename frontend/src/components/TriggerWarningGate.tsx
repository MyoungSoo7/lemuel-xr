"use client";

import type { ReactNode } from "react";

/**
 * R4 동의 게이트 — 시나리오 yml 의 `trigger_warning` 을 화면이 소비하는 단일 지점.
 *
 * ─────────────── 왜 공용 컴포넌트로 뽑았나 ───────────────
 *
 * 2026-08-12 전수 조사 결과, `trigger_warning` 을 선언한 시나리오 7개(david·elijah·
 * jesus·job·joseph·ruth·solomon) 중 화면이 있는 6개의 상태가 서로 달랐다.
 *
 *   · job · elijah · solomon — payload.trigger_warning 을 읽는다 (정상)
 *   · jesus — 카드는 있으나 payload 를 읽지 않는다. 조건이 `sceneType === "contemplative"`
 *     이고 문구·레벨·스킵 목적지가 전부 page.tsx 에 박혀 있었다.
 *   · david — 카드가 `extras.violence_warning` 이라는 **레거시 boolean** 으로 떴다.
 *     david.yml 이 그 줄에 스스로 "legacy flag — 구조화된 trigger_warning 병행" 이라고
 *     적어 둔, 이행하다 만 상태였다.
 *   · joseph — **동의 카드가 아예 없었다.** joseph.yml Scene 4 는 level medium ·
 *     content [betrayal, family_trauma] · skip_alternative_scene_id 5 를 선언해 뒀는데,
 *     화면에는 경고도 건너뛰기도 없어 가족 배신 씬으로 바로 들어갔다.
 *
 * 즉 같은 안전 규칙(R4)이 화면마다 다른 배선으로 구현돼 있었고, 그중 둘은 yml 과
 * 끊겨 있었다. 끊긴 쪽이 더 위험한 이유는 *고장 난 것처럼 보이지 않기* 때문이다 —
 * 안전 검토자가 yml 의 경고 수준을 올려도 화면은 그대로다. 고친 사람은 고쳐진 줄 안다.
 *
 * 그래서 배선을 하나로 모은다. 새 화면은 이 컴포넌트를 쓰면 자동으로 payload 를 읽고,
 * 안 쓰면 `scripts/check_frontend_trigger_warning.py` 가 CI 에서 잡는다.
 *
 * ─────────────── 문구는 누가 소유하나 ───────────────
 *
 * `consent_card_ko` 가 payload 에 있으면 **그 정본을 그대로 렌더한다** (현재 solomon·ruth).
 * 없으면 화면이 `fallbackProse` 로 문구를 소유한다 (david·jesus·job·joseph·elijah).
 * 이 우선순위는 뒤집지 말 것 — 정본이 있는데 화면 문구를 쓰면, 문구 개정이 또 안 따라온다.
 */
export interface TriggerWarning {
  level?: string;
  content?: string[];
  consent_card_id?: string;
  /** 저작 정본 동의 카드 본문. 있으면 fallbackProse 보다 우선한다. */
  consent_card_ko?: string;
  skip_alternative_scene_id?: number;
}

/** payload 에서 trigger_warning 을 꺼낸다. 없으면 undefined — 게이트를 띄우지 않는다. */
export function readTriggerWarning(
  payload: Record<string, unknown>,
): TriggerWarning | undefined {
  return payload.trigger_warning as TriggerWarning | undefined;
}

/**
 * content 태그의 한국어 라벨.
 *
 * 모르는 태그는 **원문 그대로 노출한다.** 조용히 버리지 않는 게 핵심이다 —
 * yml 에 새 트리거(예: `self_harm`)가 추가됐는데 라벨이 없어서 화면에서 사라지면,
 * 그건 "경고가 없는 것" 과 구별되지 않는다. 영문 토큰이 그대로 뜨는 건 보기 나쁘지만
 * *보인다*. 보이면 고쳐진다.
 */
const CONTENT_LABEL: Record<string, string> = {
  violence: "폭력",
  suffering: "고통",
  death: "죽음",
  death_wish: "죽음을 바라는 마음",
  birth_lament: "출생에 대한 비탄",
  betrayal: "배신",
  family_trauma: "가족 관계의 상처",
  infant_loss: "영아 상실",
  despair: "절망",
  isolation: "고립",
};

const LEVEL_LABEL: Record<string, string> = {
  low: "낮음",
  medium: "중간",
  high: "높음",
};

export function TriggerWarningGate({
  warning,
  fallbackProse,
  continueLabel = "준비됐어요 · 들어갈게요",
  skipLabel = "이 장면은 건너뛸게요 →",
  pending,
  onContinue,
  onSkip,
  children,
}: {
  warning: TriggerWarning;
  /** consent_card_ko 가 없을 때 쓸 화면 소유 문구. */
  fallbackProse: ReactNode;
  continueLabel?: string;
  skipLabel?: string;
  pending: boolean;
  onContinue: () => void;
  onSkip: () => void;
  /** 강도 조절 같은 화면 고유 컨트롤 (선택). 산문과 버튼 사이에 들어간다. */
  children?: ReactNode;
}) {
  /*
    정본(consent_card_ko) 안의 `[계속한다] [건너뛰기 …]` 줄은 *UI 지시* 라 산문에서
    걷어내고 실제 버튼으로 렌더한다. 문구를 프론트에 다시 적지 않기 위한 최소 가공.
  */
  const canonical = (warning.consent_card_ko ?? "")
    .split("\n")
    .filter((l) => l.trim().length > 0)
    .filter((l) => !l.trim().startsWith("["))
    .filter((l) => !/^음성\/자막 강도:/.test(l.trim()))
    .join("\n");

  const tags = (warning.content ?? []).map((c) => CONTENT_LABEL[c] ?? c);

  return (
    <div className="space-y-4 px-4 py-4 rounded-lg border border-[var(--color-primary)]/40 bg-black/30">
      <p className="text-xs uppercase tracking-wider text-amber-400/80">
        잠깐 — 다음 장면 안내
      </p>

      {canonical ? (
        <p className="text-sm text-[var(--color-warm)]/90 whitespace-pre-line leading-relaxed">
          {canonical}
        </p>
      ) : (
        <div className="text-sm text-[var(--color-warm)]/90 leading-relaxed">
          {fallbackProse}
        </div>
      )}

      {tags.length > 0 && (
        <div className="flex flex-wrap gap-2">
          {tags.map((t) => (
            <span
              key={t}
              className="text-[11px] px-2 py-1 rounded-full border border-amber-500/40 bg-amber-500/10 text-[var(--color-warm)]/80"
            >
              {t}
            </span>
          ))}
        </div>
      )}

      {children}

      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        {/* min-h-11 — 44px (WCAG 2.5.5 / Apple HIG). 부담을 느낀 사람이 누르는 버튼이다. */}
        <button
          type="button"
          onClick={onContinue}
          disabled={pending}
          className="min-h-11 px-4 py-3 rounded-lg bg-[var(--color-primary)] text-black font-semibold disabled:opacity-40"
        >
          {continueLabel}
        </button>
        <button
          type="button"
          onClick={onSkip}
          disabled={pending}
          className="min-h-11 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm text-[var(--color-warm)]/90 disabled:opacity-40"
        >
          {skipLabel}
        </button>
      </div>

      <p className="text-[10px] text-[var(--color-warm)]/40">
        {warning.level && (
          <>정서 강도: {LEVEL_LABEL[warning.level] ?? warning.level}</>
        )}
        {warning.level && warning.skip_alternative_scene_id != null && " · "}
        {warning.skip_alternative_scene_id != null && (
          <>
            건너뛰면 Scene {warning.skip_alternative_scene_id} 으로 이어집니다
          </>
        )}
      </p>
    </div>
  );
}
