"use client";

import { useQuery } from "@tanstack/react-query";
import { fetchScripturePassage } from "@/lib/api/content";
import { PassageBody } from "@/components/PassageBody";

/**
 * 미션 씬의 성경 본문 — `scenePayload.scriptureRef` 를 실제로 **연다**.
 *
 * ─────────────── 왜 이게 따로 필요했나 ───────────────
 *
 * 시나리오 8개는 씬마다 `scripture_ref` 를 선언하고(2026-08-20 실측 60개),
 * `ScenePayloadAssembler` 가 그걸 payload 에 실어 보낸다. 그런데 **어느 미션 화면도
 * 그 값을 읽지 않았다.** 본문은 오직 `/topics` 계열에서만 열렸다. 즉 사용자는
 * 미션을 처음부터 끝까지 걸어도 성경 본문을 한 줄도 보지 못했고, 대신 yml 의
 * `static_text` — *저자가 요약한 산문* — 만 읽었다.
 *
 * 그 상태에서 초록이던 검사가 둘 있었다. `scripture-ref:all` 은 "참조가 시드 행과
 * 맞느냐" 를, `scripture-text:all` 은 "그 행의 자구가 라벨과 맞느냐" 를 쟀다. 둘 다
 * **열 수 있다** 까지만 재고 **열린다** 는 재지 않았다. 그래서 60개 참조와 92행의
 * 축자 정렬이 화면에 한 번도 도달하지 않은 채 전부 PASS 였다.
 *
 * ─────────────── 이 컴포넌트가 지키는 것 ───────────────
 *
 * 1) **본문을 지어내지 않는다.** 텍스트는 오직 `/api/scripture/{ref}` 응답에서만 온다.
 *    프론트에 사본을 두지 않는다 — 이 리포는 한 번 데었다(`V20260717073355` 의
 *    의역 시드를 `V20260718004903` 이 되돌렸다).
 * 2) **실패를 조용히 숨기지 않는다.** 404 든 네트워크 오류든 참조와 함께 한 줄로
 *    드러낸다. 아무것도 안 그리면 「본문이 없는 씬」과 구별되지 않고, 그게 바로
 *    지금까지 아무도 못 알아챈 실패 모드다.
 * 3) **동의 게이트 안쪽에 둔다.** 배치는 각 화면의 책임이다 — R4 씬에서 본문이
 *    `TriggerWarningGate` 바깥에 놓이면 동의 전에 자극 본문이 노출된다.
 *    `scripts/mission_passage_check.py` 가 그 배치를 전수로 잰다.
 *
 * 번역본은 지정하지 않는다 — `fetchScripturePassage` 의 기본값 `modern` 을 쓴다.
 * 백엔드에 폴백이 없어서(`GetScripturePassageUseCase` 는 없으면 던진다) 라벨을 여기서
 * 바꾸면 그 즉시 전 씬이 404 가 된다.
 */
export function ScenePassage({
  reference,
  additional,
}: {
  reference?: string;
  /**
   * `extras.additional_refs` — 한 씬이 참조를 여러 개 다는 경우다.
   *
   * 저작 관례상 참조 키는 **절 단위** 라서(`docs/SCRIPTURE-REF-CONVENTION.md`),
   * 시편 23편처럼 편 전체를 쓰는 씬은 `scripture_ref: ps-23:1` 하나로 끝나지 않고
   * 나머지 다섯 절을 `additional_refs` 로 편다. 이걸 안 읽으면 화면에 1절만 뜨고
   * 나머지는 조용히 사라진다 — `scripture_ref_check.py` 는 그 5개도 세어서 초록을
   * 주므로(david 12 · ruth 15 · job 7), 여기서 빠지면 아무 검사도 못 잡는다.
   */
  additional?: string[];
}) {
  const refs = [reference, ...(additional ?? [])].filter(
    (r): r is string => !!r,
  );
  if (refs.length === 0) return null;

  return (
    <section
      data-testid="scene-passage"
      data-reference={refs.join(",")}
      className="rounded-lg border border-[var(--color-primary)]/25 bg-black/25 px-4 py-3 space-y-4"
    >
      {refs.map((r) => (
        <OnePassage key={r} reference={r} />
      ))}
    </section>
  );
}

function OnePassage({ reference }: { reference: string }) {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ["scripture", reference],
    queryFn: () => fetchScripturePassage(reference),
  });

  return (
    <div>
      <p className="text-[11px] font-mono text-[var(--color-primary)]/80 mb-2">
        {reference}
      </p>
      {isLoading && (
        <p className="text-sm text-[var(--color-warm)]/40">본문 로드 중...</p>
      )}
      {isError && (
        <p className="text-sm text-red-400">
          본문을 불러올 수 없습니다: {(error as Error).message}
        </p>
      )}
      {data && <PassageBody passage={data} showTags={false} />}
    </div>
  );
}
