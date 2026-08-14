/**
 * 씬 payload 의 `crisis_reminder` 를 렌더한다.
 *
 * 왜 컴포넌트로 빼는가
 * ────────────────────
 * yml 의 `crisis_reminder:` 는 저작자가 "이 씬은 위기 신호가 나올 수 있다" 고 표시한
 * 자리이고, 백엔드 `CrisisTokenResolver` 가 `{{crisis_resources.default}}` 를 DB 정본
 * 번호로 치환해 내려보낸다. 즉 **번호가 화면까지 도달하는 마지막 한 칸**이 이 렌더다.
 *
 * 2026-08-14 실측: 7개 시나리오가 이 키를 선언하는데 화면은 3개(elijah·job·solomon)만
 * 읽고 있었다. david·joseph·moses 는 백엔드가 치환까지 끝낸 문구를 받아 놓고 그대로
 * 버렸다 — outro 에 위기 자원이 **한 줄도 뜨지 않았다.** jesus 는 payload 를 무시하고
 * 로컬 상수를 렌더해서, yml 을 고쳐도 화면이 안 따라오는 상태였다.
 *
 * 아무도 못 잡은 이유가 중요하다. `check_frontend_hotline.py` 는 "번호가 하드코딩됐나"
 * 를 보지 "번호가 뜨나" 는 보지 않는다. 없는 것은 하드코딩이 아니므로 초록이다.
 * 화면 3벌을 손으로 베껴 쓰는 한 이 갈라짐은 또 생기므로, 렌더를 한 벌로 모은다.
 *
 * 실패 모양이 조용하다는 점도 같다 — 화면은 멀쩡히 돌고, 위기 상태의 사용자만
 * 연결을 못 받는다.
 */
export function CrisisReminder({ text }: { text?: string | null }) {
  // 백엔드가 안 내려보냈거나 공백만 있으면 빈 상자를 만들지 않는다.
  if (!text?.trim()) return null;

  return (
    <section
      // `role="note"` — 이 줄은 본문이 아니라 보조 안내다. 스크린리더가 본문 흐름과
      // 섞어 읽으면 사용자는 이게 이야기의 일부인지 안내인지 구분하지 못한다.
      role="note"
      className="max-w-3xl mx-auto w-full mb-4 px-4 py-3 rounded-lg border border-amber-500/40 bg-amber-500/10 text-sm text-[var(--color-warm)]/90"
    >
      {text}
    </section>
  );
}
