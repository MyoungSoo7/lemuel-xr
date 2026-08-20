"use client";

import type { ScripturePassage } from "@/lib/api/content";

/**
 * `/api/scripture/{ref}` 응답 한 건을 그리는 공용 본문 블록.
 *
 * 원래 `PassageModal` 안의 비공개 함수였다. 미션 화면(`ScenePassage`)이 같은 응답을
 * 그리게 되면서 밖으로 꺼냈다 — 두 벌로 두면 한쪽만 고쳐지고, 그 드리프트를 재는
 * 검사기가 없다.
 *
 * ⚠️ `translation` 을 화면에 그대로 찍는다. 시드 라벨이 자구와 어긋나면 사용자에게
 * 곧장 보인다는 뜻이다 — 그 대조는 `scripts/scripture_text_check.py` 가 92행 전수로
 * 잰다. 여기서 라벨을 예쁘게 바꿔 적지 않는 이유이기도 하다.
 */

const CHARACTER_LABEL: Record<string, string> = {
  joseph: "요셉",
  moses: "모세",
  david: "다윗",
  jesus: "예수",
  job: "욥",
  elijah: "엘리야",
  // 2026-08-20 추가. 이 둘이 빠져 있어서 `character_tags` 에 ruth·solomon 이 실린
  // 행은 칩에 영문 슬러그가 그대로 노출됐다(`👤 ruth`). 시드에는 처음부터 있었다.
  ruth: "룻",
  solomon: "솔로몬",
};

export function PassageBody({
  passage,
  showTags = true,
}: {
  passage: ScripturePassage;
  /** 미션 씬 안에서는 태그 칩이 장면의 주의를 흩는다 — 그쪽에서만 끈다. */
  showTags?: boolean;
}) {
  return (
    <div>
      <p className="text-xs text-[var(--color-warm)]/40 mb-2">
        {passage.book} {passage.chapter}장 {passage.verseStart}
        {passage.verseEnd && passage.verseEnd !== passage.verseStart
          ? `–${passage.verseEnd}`
          : ""}
        절 · {passage.translation}
      </p>
      <blockquote className="text-base text-[var(--color-warm)]/90 italic leading-relaxed border-l-2 border-[var(--color-primary)]/40 pl-4">
        {passage.text}
      </blockquote>
      {showTags &&
        (passage.themeTags?.length || passage.characterTags?.length) && (
          <div className="flex flex-wrap gap-2 mt-4">
            {passage.themeTags?.map((tag) => (
              <span
                key={tag}
                className="text-[10px] px-2 py-1 rounded bg-[var(--color-primary)]/10 text-[var(--color-primary)]"
              >
                #{tag}
              </span>
            ))}
            {passage.characterTags?.map((tag) => (
              <span
                key={`c-${tag}`}
                className="text-[10px] px-2 py-1 rounded bg-[var(--color-warm)]/10 text-[var(--color-warm)]/70"
              >
                👤 {CHARACTER_LABEL[tag] || tag}
              </span>
            ))}
          </div>
        )}
    </div>
  );
}
