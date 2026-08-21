"use client";

import { useMemo } from "react";
import { useQueries } from "@tanstack/react-query";
import { fetchScripturePassage } from "@/lib/api/content";
import {
  resolveMonologue,
  type Monologue,
  type PassageTexts,
} from "@/lib/content/scripture-quote";

/**
 * 모놀로그가 인용하는 절을 `/api/scripture` 에서 받아 두고, `Monologue` 를 화면
 * 문자열로 바꿔 주는 훅.
 *
 * `ScenePassage` 와 **같은 queryKey** (`["scripture", ref]`) 를 쓴다. 미션 화면은
 * 씬 본문과 모놀로그 인용이 같은 절을 겹쳐 여는 일이 잦은데(시 23:1 · 요 14:6 …),
 * 키가 같으면 React Query 가 한 번만 받아 둘 다 먹인다. 키를 다르게 두면 같은 절을
 * 두 번 받고, 더 나쁘게는 캐시가 갈려 *한 화면에서 두 자구* 가 뜰 수 있다 — 이
 * 리포가 #84 에서 겪은 실패 그대로다.
 *
 * `refs` 는 모듈 상수(`refsOf(...)` 결과)로 넘긴다. 렌더마다 새 배열을 만들면
 * `useQueries` 가 매번 새 구독을 세운다.
 */
export interface MonologueTexts {
  /** 아직 한 건이라도 받는 중. */
  isLoading: boolean;
  /** 한 건이라도 실패. 화면은 이걸 **보이게** 알려야 한다. */
  isError: boolean;
  error: Error | null;
  /** 참조 -> 자구. 아직 안 온 참조는 없는 키다. */
  texts: PassageTexts;
  /**
   * 모놀로그 하나를 화면 문자열로. 인용 자구가 아직/영영 없으면 `null`.
   *
   * `null` 을 빈 문자열로 눕히지 않는 게 핵심이다 — 산문만 남은 모놀로그는 인용이
   * 빠진 티가 나지 않은 채로 뜻이 바뀐다("…라고 하셨다" 만 남는다).
   */
  resolve: (monologue: Monologue | null | undefined) => string | null;
}

export function useMonologueTexts(refs: readonly string[]): MonologueTexts {
  const results = useQueries({
    queries: refs.map((reference) => ({
      queryKey: ["scripture", reference],
      queryFn: () => fetchScripturePassage(reference),
    })),
  });

  /*
    `results` 는 렌더마다 새 배열이라 의존성으로 못 쓴다. 대신 받아 온 자구를 한
    문자열로 눌러 지문을 만든다 — 절 자구에 나올 수 없는 NUL 로 이어 붙여 절 경계가
    옮겨 가는 경우까지 지문이 달라지게 하고, 아직 안 온 자리는 빈 칸으로 둔다.
  */
  const fingerprint = results.map((r) => r.data?.text ?? "").join("\u0000");

  const texts = useMemo(() => {
    const map: Record<string, string> = {};
    refs.forEach((reference, i) => {
      const data = results[i]?.data;
      if (data) map[reference] = data.text;
    });
    return map;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refs, fingerprint]);

  const isLoading = results.some((r) => r.isLoading);
  const failed = results.find((r) => r.isError);

  return {
    isLoading,
    isError: !!failed,
    error: (failed?.error as Error) ?? null,
    texts,
    resolve: (monologue) =>
      monologue ? resolveMonologue(monologue, texts) : null,
  };
}
