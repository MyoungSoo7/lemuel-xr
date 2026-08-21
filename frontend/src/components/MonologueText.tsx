"use client";

import { NarrationAudioButton } from "@/components/NarrationAudioButton";
import { refsOf, type Monologue } from "@/lib/content/scripture-quote";
import type { MonologueTexts } from "@/lib/hooks/useMonologueTexts";

/**
 * 모놀로그 한 편을 화면에 그린다 — 성경 자구는 `/api/scripture` 응답에서 채워 넣는다.
 *
 * 미션 화면 넷이 같은 자리를 두 번씩 쓴다: 눈으로 읽는 `<p>` 와 귀로 듣는 TTS 버튼.
 * 둘이 **같은 문자열** 이어야 한다는 게 이 컴포넌트가 존재하는 첫 번째 이유다. 예전에는
 * 각 화면이 문자열을 따로 집어넣었고, 인용이 API 로 옮겨 오면 "화면엔 떴는데 낭독은
 * 빈 문자열" 같은 어긋남이 네 화면에 각각 생길 자리가 있었다.
 *
 * 두 번째 이유는 **실패를 보이게 하는 것** 이다. `resolve` 는 인용 자구가 하나라도
 * 비면 `null` 을 준다(`scripture-quote.ts` 참고). 그걸 빈 문자열로 눕히면 산문만 남은
 * 문장이 아무 표시 없이 뜬다 — 이 리포가 #83·#84 에서 두 번 데인 실패 모드의 다음
 * 변종이다. 그래서 여기서 세 갈래를 **전부 눈에 보이는 것으로** 갈라 놓는다:
 *
 *  1) 자구가 다 왔다 → 그린다.
 *  2) 아직 안 온 참조가 있다 → 로드 중(또는 그 요청이 실패했다면 오류)을 적는다.
 *  3) 참조는 다 왔는데도 해석이 실패했다 → **클립 인덱스 자체가 틀린 것** 이다.
 *     로드 중으로 뭉뚱그리면 영영 안 끝나는 로딩으로 보이므로 따로 적는다.
 *     (`docs/monologue-quotes.lock.txt` diff 로 잡히는 게 정상이고, 여기 뜨면 그
 *     기준선이 시드 변경을 못 따라간 것이다.)
 */
export function MonologueText({
  monologue,
  source,
  className = "",
  audio = true,
  audioWrapperClassName = "mt-3",
  testId = "monologue-text",
}: {
  monologue: Monologue | null | undefined;
  /** `useMonologueTexts(<module>.allRefs)` 결과를 그대로 넘긴다. */
  source: MonologueTexts;
  /** `<p>` 에 붙일 클래스. `whitespace-pre-line` 은 여기서 항상 붙인다. */
  className?: string;
  audio?: boolean;
  audioWrapperClassName?: string;
  testId?: string;
}) {
  if (!monologue) return null;

  const text = source.resolve(monologue);
  if (text !== null) {
    return (
      <>
        <p data-testid={testId} className={`whitespace-pre-line ${className}`}>
          {text}
        </p>
        {audio && (
          <div className={audioWrapperClassName}>
            <NarrationAudioButton text={text} onUnavailable="hide" />
          </div>
        )}
      </>
    );
  }

  const pending = refsOf(monologue).filter(
    (ref) => source.texts[ref] === undefined,
  );

  if (pending.length === 0) {
    return (
      <p data-testid="monologue-broken" className="text-sm text-red-400">
        인용 구간이 본문과 맞지 않습니다. 관리자에게 알려 주세요.
      </p>
    );
  }

  if (source.isError) {
    return (
      <p data-testid="monologue-error" className="text-sm text-red-400">
        인용한 성경 본문을 불러올 수 없습니다:{" "}
        {source.error?.message ?? "알 수 없는 오류"}
      </p>
    );
  }

  return (
    <p
      data-testid="monologue-loading"
      className="text-sm text-[var(--color-warm)]/40"
    >
      본문 로드 중...
    </p>
  );
}
