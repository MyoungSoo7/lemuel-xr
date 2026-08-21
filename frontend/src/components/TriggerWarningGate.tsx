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
  /**
   * 카드를 **거절** 했을 때의 목적지. 현재 정의된 값은 `closing` 하나뿐이다.
   *
   * `skip` 과 `decline` 은 다른 문이다 — skip 은 이 씬만 건너뛰고 이야기가 이어지고,
   * decline 은 카드 자체를 거절해 종결 화면으로 간다(백엔드 `SceneSkipResolver`).
   * 이 키를 읽지 않으면 화면은 두 문을 구별할 수 없고, 「여기서 마친다」라고 적힌
   * 버튼이 조용히 *건너뛰기* 를 보내게 된다 — 마치겠다고 고른 사람이 다음 씬으로
   * 들어간다. 백엔드에서 방금 고친 결함(스킵 목적지 ≠ next)과 같은 모양이다.
   *
   * 리포 전체에서 이 키를 선언하는 카드는 룻 Scene 3 하나다(`ruth.yml`).
   */
  declined_route?: string;
  /**
   * 건너뛴 구간을 잇는 저작된 한 줄. 현재 룻 Scene 1 진입 카드에만 있다.
   *
   * 그 카드는 Scene 1·2 를 함께 덮고 목적지가 3 이라, 건너뛴 사용자는 룻이라는
   * 이름이 처음 나오는 대목을 통째로 지나친다. 이 줄이 없으면 그 사람은 **누구
   * 이야기인지 모르는 채로** Scene 3 에 도착한다. 건너뛰기가 이야기를 잃는 일이
   * 되지 않게 하려고 저작자가 적어 둔 다리이므로, 화면이 읽지 않으면 그 의도가
   * yml 안에서만 참이 된다.
   */
  skip_bridge_narration_ko?: string;
}

/** content 를 문자열 배열로 맞춘다. 배열이 아니어도 버리지 않는다. */
function toTags(raw: unknown): string[] | undefined {
  /*
    yml 에 `content: violence` 처럼 대괄호를 빠뜨리면 문자열이 온다. 예전엔 그대로
    렌더러로 흘러가 `"violence".map is not a function` 으로 **씬 전체가 터졌다**.
    경고를 붙였다는 이유로 화면이 죽는 건 최악의 실패다 — 저작자는 경고를 안 붙이는
    쪽으로 학습한다. 한 개짜리 목록으로 받아 준다.
  */
  if (typeof raw === "string") {
    const one = raw.trim();
    return one ? [one] : undefined;
  }
  if (!Array.isArray(raw)) return undefined;
  const tags = raw
    .filter((v) => v != null)
    .map((v) => String(v).trim())
    .filter((v) => v.length > 0);
  return tags.length > 0 ? tags : undefined;
}

/** skip 목적지. yml 이 따옴표를 붙여 문자열로 와도 숫자로 읽는다. */
function toSceneId(raw: unknown): number | undefined {
  if (typeof raw === "number" && Number.isFinite(raw)) return raw;
  if (typeof raw === "string" && /^\d+$/.test(raw.trim())) {
    return Number(raw.trim());
  }
  return undefined;
}

function toText(raw: unknown): string | undefined {
  if (typeof raw !== "string") return undefined;
  return raw.trim() ? raw : undefined;
}

/**
 * payload 에서 trigger_warning 을 꺼내 규약 모양으로 맞춘다.
 *
 * **판정 기준은 "모양이 맞나" 가 아니라 "저작자가 뭔가 적었나" 다.** 적혀 있는데
 * 모양이 규약과 다르면 게이트를 *띄운다*. 예전 구현은 `as TriggerWarning` 캐스팅
 * 하나뿐이어서, 레거시 boolean(`trigger_warning: true`) 이나 문자열이 들어오면
 * 카드는 뜨는데 레벨·트리거 종류·건너뛰기 목적지가 전부 빈 채로 떴다. 경고했다는
 * 흔적만 남고 *무엇을* 경고하는지는 전달되지 않는 카드였다.
 *
 * 이제 그런 값은 빈 객체(`{}`)로 정규화된다. 카드는 화면이 소유한 fallbackProse 와
 * 계속·건너뛰기 두 손잡이를 갖춘 채로 뜬다 — 메타데이터는 없어도 *동의를 구하는
 * 문* 으로서는 온전하다. 그게 경고 없이 씬에 들어가는 것보다 낫다.
 *
 * ─────────────── 여기서 못 막는 것 ───────────────
 *
 * yml 이 `trigger_warning:` 키만 적고 값을 비우면 SnakeYAML 은 null 을 싣고,
 * `spring.jackson.default-property-inclusion: non_null` (application.yml) 이
 * **그 키를 JSON 에서 통째로 지운다.** 즉 프론트에는 "빈 선언" 과 "선언 없음" 이
 * 똑같이 `undefined` 로 도착한다 — 여기서는 원리적으로 구별할 수 없다.
 * 그 경우는 yml 을 직접 읽는 `scripts/check_frontend_trigger_warning.py` 가
 * 빈 선언으로 잡아 CI 를 빨갛게 만든다. 두 곳이 나눠 막는 이유가 이것이다.
 */
export function readTriggerWarning(
  payload: Record<string, unknown>,
): TriggerWarning | undefined {
  const raw = payload.trigger_warning;

  // 선언이 없거나 명시적으로 끈 경우만 게이트가 없다.
  // 경고 없는 씬까지 카드를 띄우면 사용자는 카드를 습관적으로 넘기게 되고,
  // 그때부터 진짜 경고도 안 읽힌다. 없으면 없어야 한다.
  if (raw === undefined || raw === false) return undefined;

  // null 은 *선언은 했는데 비어 있다* 는 뜻이라 게이트를 연다(fail-closed).
  // 위에 적었듯 현재 백엔드 설정에서는 이 값이 프론트까지 오지 않는다. 그래도
  // 여기서 undefined 와 뭉뚱그리지 않는 이유는, 직렬화 설정이 바뀌어 null 이
  // 도착하게 되는 날 조용히 문이 열리는 쪽이 아니라 닫히는 쪽이어야 해서다.
  if (raw === null) return {};

  // 레거시 boolean(true) — david.yml 의 violence_warning 이 쓰던 모양이다.
  // 정보는 0 이지만 "경고할 씬" 이라는 선언이므로 게이트는 연다.
  if (raw === true) return {};

  // 스칼라 문자열. 저작자가 무엇을 의도했는지(레벨? 태그? 문구?) 알 수 없으므로
  // 넘겨짚지 않고 동의 카드 본문으로 보여 준다 — 적힌 글자를 사용자에게 전달한다.
  if (typeof raw === "string") {
    const text = raw.trim();
    return text ? { consent_card_ko: text } : {};
  }

  if (typeof raw !== "object" || Array.isArray(raw)) return {};

  const src = raw as Record<string, unknown>;
  return {
    level: toText(src.level),
    content: toTags(src.content),
    consent_card_id: toText(src.consent_card_id),
    consent_card_ko: toText(src.consent_card_ko),
    skip_alternative_scene_id: toSceneId(src.skip_alternative_scene_id),
    declined_route: toText(src.declined_route),
    skip_bridge_narration_ko: toText(src.skip_bridge_narration_ko),
  };
}

/**
 * content 태그의 한국어 라벨.
 *
 * 모르는 태그는 **원문 그대로 노출한다.** 조용히 버리지 않는 게 핵심이다 —
 * yml 에 새 트리거가 추가됐는데 라벨이 없어서 화면에서 사라지면, 그건 "경고가
 * 없는 것" 과 구별되지 않는다. 영문 토큰이 그대로 뜨는 건 보기 나쁘지만 *보인다*.
 * 보이면 고쳐진다.
 *
 * 다만 그 안전망에 기대 방치하지는 말 것. 배포된 yml 이 쓰는 태그는 여기 등재한다.
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
  // solomon.yml 이 실제로 싣고 있는데 라벨이 없어 영문 토큰으로 뜨던 것들.
  // 원문 노출은 *모르는* 태그를 위한 안전망이지, 이미 배포된 태그의 자리가 아니다.
  bereavement: "사별",
  child_endangerment: "아이가 위험에 놓임",
  emptiness: "공허",
  existential_despair: "삶의 의미를 잃은 절망",
  self_harm: "자해",
};

const LEVEL_LABEL: Record<string, string> = {
  low: "낮음",
  low_medium: "낮음~중간", // solomon.yml Scene 4 가 쓴다.
  low_mid: "낮음~중간", // ruth.yml Scene 3. `low_medium` 과 같은 뜻의 다른 표기다.
  medium: "중간",
  mid: "중간", // ruth.yml Scene 1.
  high: "높음",
};

/*
  정본에서 걷어낼 *UI 지시줄* — `[계속한다] [건너뛰기 — Scene 5]` 처럼 줄 전체가
  대괄호 토큰으로만 이뤄진 경우다.

  예전 필터는 `startsWith("[")` 였다. 그래서 "[주의] 이 장면에는 자해 묘사가
  있습니다" 처럼 대괄호 머리말을 단 **안전 문구가 통째로 증발했다.** 화면은 멀쩡해
  보이고 어떤 게이트도 빨개지지 않는다 — 검토자는 문구를 넣었다고 믿는다.
  대괄호는 한국어 안전 카피에서 머리말로 흔히 쓰는 기호라 특히 밟기 쉬웠다.
*/
const UI_INSTRUCTION_LINE = /^(?:\[[^\]]*\]\s*)+$/;

/**
 * 동의 카드 정본에서 **두 문의 이름** 을 꺼낸다.
 *
 * 카드마다 문 이름이 다르다 — 「사별 장면은 건너뛴다」(룻 S1) · 「여기서 마친다」
 * (룻 S3) · 「건너뛰기 — 성문 장면으로 이동」(룻 S4) · 「건너뛰기」(베드로 S2).
 * 이건 문구 취향이 아니라 **그 문이 무엇을 하는지에 대한 설명** 이라, 공용 기본값
 * ("이 장면은 건너뛸게요 →")으로 덮으면 룻 Scene 3 에서는 거짓말이 된다.
 *
 * 파싱에 실패하면 undefined 를 준다 — 게이트 기본 라벨로 돌아가고, 문 자체는 남는다.
 * 라벨을 못 읽었다고 나갈 문을 없애는 쪽이 훨씬 나쁘다.
 *
 * 「음성/자막 강도: [ 자막만 ] …」 줄은 대괄호로 *시작하지 않으므로* 여기 안 걸린다.
 *
 * 이 함수는 원래 `app/ruth/page.tsx` 에 있었다. 2026-08-22 베드로 화면이 두 번째
 * 사용자가 되면서 이리로 옮겼다 — 정본 라벨을 쓰는 화면이 늘어날 때 각자 베끼면
 * 위 `readTriggerWarning` 이 없던 시절과 똑같은 갈래가 다시 생긴다.
 */
export function cardDoors(
  consentCardKo?: string,
): { continueLabel: string; skipLabel: string } | undefined {
  const line = (consentCardKo ?? "")
    .split("\n")
    .map((l) => l.trim())
    .find((l) => UI_INSTRUCTION_LINE.test(l));
  if (!line) return undefined;

  const doors = Array.from(line.matchAll(/\[([^\]]*)\]/g))
    .map((m) => m[1].trim())
    .filter((t) => t.length > 0);
  if (doors.length < 2) return undefined;

  return { continueLabel: doors[0], skipLabel: doors[1] };
}

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
  /**
   * 둘째 문을 골랐을 때. **어느 문인지를 인자로 받는다.**
   *
   * `warning.declined_route` 가 있는 카드의 둘째 문은 건너뛰기가 아니라 *거절* 이고,
   * 백엔드가 기다리는 결정값도 `"skip"` 이 아니라 `"decline"` 이다. 화면이 이를
   * 구별하지 않으면 「여기서 마친다」 버튼이 다음 씬을 여는 상태로 조용히 초록이 된다.
   *
   * 기존 호출부는 인자를 무시하는 `() => void` 그대로 둬도 된다 — 그 카드들에는
   * `declined_route` 가 없어 항상 `"skip"` 만 온다.
   */
  onSkip: (action: "skip" | "decline") => void;
  /** 강도 조절 같은 화면 고유 컨트롤 (선택). 산문과 버튼 사이에 들어간다. */
  children?: ReactNode;
}) {
  /*
    정본(consent_card_ko) 안의 `[계속한다] [건너뛰기 …]` 줄은 *UI 지시* 라 산문에서
    걷어내고 실제 버튼으로 렌더한다. 문구를 프론트에 다시 적지 않기 위한 최소 가공.
    걷어내는 범위는 좁게 — 판단이 서지 않는 줄은 그대로 보여 준다. 안전 문구를
    잘못 지우는 쪽의 대가가, 지시줄이 한 줄 남는 쪽의 대가보다 훨씬 크다.
  */
  const canonical = (warning.consent_card_ko ?? "")
    .split("\n")
    .filter((l) => l.trim().length > 0)
    .filter((l) => !UI_INSTRUCTION_LINE.test(l.trim()))
    .filter((l) => !/^음성\/자막 강도:/.test(l.trim()))
    .join("\n");

  const tags = (warning.content ?? []).map((c) => CONTENT_LABEL[c] ?? c);

  /*
    둘째 문이 *거절* 인가 *건너뛰기* 인가.

    `declined_route` 를 선언한 카드에서 둘째 문은 이야기를 이어 가는 문이 아니라
    끝내는 문이고, 백엔드가 기다리는 결정값도 `"decline"` 이다(SceneSkipResolver).
    이 구별을 화면이 안 하면 카드 문구는 「여기서 마친다」인데 실제로는 다음 씬이
    열린다 — 그만두겠다고 고른 사람이 계속 들어가는, 가장 나쁜 방향의 불일치다.

    선언이 없으면 지금까지와 똑같이 `"skip"` 이다. 기존 6개 화면의 동작은 안 바뀐다.
  */
  const declines = !!warning.declined_route?.trim();

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
          onClick={() => onSkip(declines ? "decline" : "skip")}
          disabled={pending}
          className="min-h-11 px-4 py-3 rounded-lg border border-[var(--color-primary)]/40 hover:border-[var(--color-primary)] text-sm text-[var(--color-warm)]/90 disabled:opacity-40"
        >
          {skipLabel}
        </button>
      </div>

      {/*
        건너뛰기 안내는 *항상* 적는다.

        예전에는 skip_alternative_scene_id 가 없으면 이 줄이 통째로 비었다.
        레거시 boolean 처럼 메타데이터가 없는 선언에서는 카드에 산문과 버튼만 남아,
        건너뛰면 이야기를 잃는 건지 아닌지 모르는 채로 고르게 했다. 목적지를
        모를 때도 "이어진다" 는 사실만은 말해 줄 수 있고, 그게 이 문의 요지다.

        거절 문(declined_route)일 때는 반대로 **이어지지 않는다** 고 적어야 한다.
        룻 Scene 3 은 `skip_alternative_scene_id: 4` 와 `declined_route: closing` 을
        둘 다 선언하지만 카드가 여는 문은 「여기서 마친다」 하나다. 여기서 앞의 값을
        읽으면 "건너뛰면 Scene 4 로 이어집니다" 라고 적힌 버튼이 종결로 보내게 된다.
      */}
      <p className="text-[10px] text-[var(--color-warm)]/40">
        {warning.level && (
          <>정서 강도: {LEVEL_LABEL[warning.level] ?? warning.level} · </>
        )}
        {declines ? (
          <>여기서 마치면 다음 장면 없이 종결 화면으로 갑니다</>
        ) : warning.skip_alternative_scene_id != null ? (
          <>
            건너뛰면 Scene {warning.skip_alternative_scene_id} 으로 이어집니다
          </>
        ) : (
          <>건너뛰어도 이야기는 이어집니다</>
        )}
      </p>
    </div>
  );
}
