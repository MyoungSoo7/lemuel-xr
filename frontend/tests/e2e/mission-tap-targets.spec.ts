import { test, expect, Page } from "@playwright/test";
import { MIN_TAP_PX, scanTapTargets } from "./tap-target-probe";

/**
 * 44px 터치 타깃 검사 — **인물 미션 씬 안쪽**. 2026-08-12 신설.
 *
 * `tap-targets.spec.ts` 는 백엔드가 필요 없는 7개 화면만 본다. 미션 씬은 `/api/game/*`
 * 로 씬을 받아야 그려지므로 거기 들어가지 못했고, 그래서 README §10 과 CI 주석에
 * 「미션 씬은 한 번도 안 쟀다」고 적어 두었다. 이 파일이 그 자리를 메운다.
 *
 * 처음 재고 나온 것(2026-08-12, 320px):
 *   · /elijah 위기 확인 씬의 상담번호 링크 **26×17** — 앱이 「이 사용자가 위험할 수
 *     있다」고 판단한 화면에서 전화 거는 링크가 앱 전체에서 제일 작았다.
 *   · /moses 변명 배정 버튼 68×30 · 81×30 (열 개가 촘촘히 붙어 있다)
 *   · /solomon 음성/자막 강도 버튼 36×26 — 자극을 낮추는 손잡이다.
 *   요셉·다윗·욥·예수는 깨끗했다.
 *
 * **씬 흐름을 인물마다 적지 않는다.** 인물별 정답 경로를 손으로 적으면 시나리오가
 * 바뀔 때마다 같이 썩고, 무엇보다 「그 경로에서만」 재게 된다. 대신 눌러지는 첫 버튼을
 * 계속 눌러 씬을 흘려보내며 매 단계 잰다. 어디로 흘러가든 **그 화면에 있는 것은 다
 * 잰다**는 쪽이 이 검사의 목적에 맞다.
 *
 * 한계 —
 *   · 분기마다 다 들어가지 않는다. 첫 버튼을 따라간 한 갈래만 본다.
 *   · **백엔드가 있어야 돈다.** 2026-08-13 에 백엔드 딸린 전용 잡(`mission-e2e`)으로
 *     CI 에 들어갔다.
 *
 * ⚠️ 2026-08-13 — 이 파일이 스스로 경계했던 「조용한 초록」에 이 파일이 걸려 있었다.
 *
 * 원래 방어는 분모(`measured > 0`) 하나였다. 백엔드를 내리고 돌려 보니 **7개 인물이
 * 전부 초록이었다.** 백엔드가 없으면 `SceneBootState` 가 「세션을 시작하지 못했습니다」
 * 화면과 `다시 시도` 버튼(min-h-11, 44px 준수)을 그리고, 위기 푸터·내비도 같이 그려진다.
 * 즉 분모는 0 이 아니다 — 씬은 한 줄도 안 열렸는데 분모는 채워진다. 2026-08-06 에
 * 먹통 화면을 고치려고 넣은 그 에러 UI 가, 공교롭게 이 검사의 유일한 방어를 무력화했다.
 *
 * 그래서 분모 대신 **씬이 실제로 왔는지** 를 본다: `/api/game/{인물}/start` 가 200 으로
 * 응답했는지 네트워크에서 직접 확인한다. 백엔드가 안 떴거나 프록시가 어긋나면 그
 * 자리에서 빨개진다. 분모 검사는 그대로 두되(다른 것을 잡는다) 그 앞에 이 관문을 둔다.
 *
 * ⚠️ 2026-08-13(2차) — 관문을 통과하고도 「조용한 초록」이 한 겹 더 남아 있었다.
 *
 * start 200 을 확인하고 분모도 채워지면, 이제 남은 가정은 「눌러서 씬이 끝까지 넘어간다」
 * 이다. 그 가정이 안 지켜진다. 눌러지는 **첫** 버튼만 계속 누르면 선택을 요구하는 씬에서
 * 걸린다 — `/moses` 3 번째 씬은 변명 카드 다섯 장을 **모두** 배정해야 제출이 열리는데,
 * 첫 버튼만 누르면 같은 자리를 반복해서 누를 뿐 배정이 안 끝난다.
 *
 * 어디까지 갔었는지 실측했다(2026-08-13, chromium, 걸음 24):
 *   · /elijah  SCENE 3/5 에서 정지 — 4·5 를 못 봄
 *   · /moses   SCENE 3/6 에서 정지 — 4·5·6 을 못 봄
 *   · /solomon SCENE 3/5 에서 정지 — 4·5 를 못 봄
 *   요셉·다윗·욥·예수는 끝까지 갔다.
 *
 * 즉 **일곱 중 셋은 미션의 뒷부분을 한 번도 안 잰 채로 초록이었다.** 첫 씬에서 맴돈
 * 것이 아니라 중간에서 멈춘 것이라, 「씬이 바뀌긴 했는가」 정도의 검사로는 안 잡힌다.
 *
 * 두 가지를 더한다.
 *   · 같은 씬에서 이미 누른 자리는 다시 고르지 않는다(`clicked`). 그래야 다섯 장을
 *     차례로 배정하고 제출까지 간다. 이것으로 일곱 인물 모두 마지막 씬까지 간다.
 *   · **마지막 씬까지 갔는지** 를 판정에 넣는다. 헤더의 `Scene n/m` 을 걸음마다 읽어
 *     `n === m` 을 한 번도 못 밟았으면 빨개진다. 위 셋을 실제로 빨갛게 만드는 것을
 *     확인하고 넣었다 — 안 무는 검사를 넣지 않기 위해서다.
 *     걸어간 경로는 `walk` 주석으로 리포트에 남겨, 초록일 때도 무엇을 재고 초록인지
 *     눈으로 확인할 수 있게 한다.
 *
 * `STEPS` 를 12 → 24 로 올린 것도 같은 이유다. 배정형 씬은 한 씬을 넘기는 데만 열 번
 * 가까이 눌러야 해서, 12 걸음으로는 뒤쪽 씬에 닿기 전에 예산이 끊긴다.
 *
 * 이 판정의 대가는 정직하게 적어 둔다 — **중간에서 정당하게 끝나는 분기가 생기면
 * 빨개진다.** 그때는 이 검사를 무르지 말고 그 분기를 어떻게 끝까지 걷게 할지를 정할 것.
 * 절반만 걷고 초록인 상태로 돌아가는 것이 이 파일이 막으려는 바로 그것이다.
 */

const MIN = MIN_TAP_PX;
const STEPS = 24;

const CHARACTERS = [
  "/joseph",
  "/david",
  "/elijah",
  "/job",
  "/jesus",
  "/moses",
  "/solomon",
  // 2026-08-20 추가. 룻은 백엔드가 열린 뒤에도 화면이 없어 이 검사의 사정권 밖이었다 —
  // 목록에 없는 인물은 「깨끗함」이 아니라 **한 번도 안 잼** 이다.
  "/ruth",
];

/**
 * 헤더("Moses — Scene 3/6 · Mode: VR") 에서 씬 번호만 뽑는다.
 *
 * ⚠️ 정규식의 `i` 를 지우지 말 것. 헤더에 `uppercase` 가 걸려 있어 `innerText` 는
 *    **"SCENE 3/6"** 으로 온다. 대소문자를 가리면 전 인물이 `null` 이 되어, 아래
 *    `clicked` 키는 매 걸음 달라져 무력해지고 진행 판정도 같이 죽는다. 그러고도
 *    검사는 초록이다 — 이 파일이 두 번 걸렸던 바로 그 모양이다.
 */
async function sceneLabel(page: Page): Promise<string | null> {
  const header = await page
    .locator("header")
    .first()
    .innerText()
    .catch(() => "");
  const m = header.match(/Scene\s*\d+\s*\/\s*\d+/i);
  return m ? m[0].replace(/\s+/g, " ").toUpperCase() : null;
}

for (const route of CHARACTERS) {
  test(`${route} — 씬을 넘겨 가며 모든 터치 타깃이 ${MIN}px 이상이다`, async ({
    page,
  }) => {
    // 이동 *전* 에 건다 — start 는 마운트 직후 나가므로 goto 뒤에 걸면 놓친다.
    const character = route.slice(1);
    const started = page
      .waitForResponse(
        (r) => r.url().includes(`/api/game/${character}/start`) && r.ok(),
        { timeout: 20_000 },
      )
      .catch(() => null);

    await page.goto(route);

    expect(
      await started,
      `${route} — /api/game/${character}/start 가 200 으로 오지 않았다. ` +
        `씬이 안 열렸다는 뜻이고, 이 스펙이 재는 것은 씬 안쪽이므로 이대로 통과하면 ` +
        `그 초록은 거짓이다. 백엔드가 떠 있는지, 프록시(BACKEND_INTERNAL_URL)가 ` +
        `맞는지 확인할 것.`,
    ).not.toBeNull();

    await page.waitForLoadState("networkidle");

    // 같은 씬을 여러 걸음 재게 되므로 위반은 집합으로 모은다 — 같은 버튼이 열 줄로
    // 불어나면 정작 무엇이 몇 개인지 안 보인다.
    const violations = new Set<string>();
    const visited: string[] = [];
    const clicked = new Set<string>();
    let measured = 0;

    for (let step = 0; step < STEPS; step++) {
      const scene = (await sceneLabel(page)) ?? `step ${step}`;
      visited.push(scene);

      const { total, small } = await scanTapTargets(page);
      // 분모도 함께 본다. 위 관문(start 200)이 백엔드 부재를 막고, 이 줄은 그 다음 —
      // 응답은 왔는데 화면이 아무것도 안 그린 경우를 막는다. 아무것도 없으면
      // 「작은 것 0 개」가 참이 되어 검사가 조용히 통과한다.
      if (total > 0) measured++;
      // 여러 걸음에 걸쳐 모으므로 `tapReport` 대신 씬 이름을 붙인 자체 형식을 쓴다.
      for (const s of small) {
        violations.add(
          `  [${scene}] ${s.tag} "${s.name}" ${s.w}×${s.h} — ${s.cls}`,
        );
      }

      const candidates = page.locator(
        'main button:not([disabled]), main a[href^="/"]',
      );
      // 첫 버튼만 계속 누르면 배정형 씬(/moses 3)에서 제자리다. **그 씬에서 아직 안
      // 누른 자리** 를 고른다. 키에 글자를 함께 넣는 것은, 배정이 끝난 버튼이
      // `disabled` 로 빠지면서 위 목록의 인덱스가 밀리기 때문이다.
      const texts = await candidates.allInnerTexts();
      const idx = texts.findIndex(
        (t, i) => !clicked.has(`${scene}::${i}::${t.trim()}`),
      );
      if (idx < 0) break;
      clicked.add(`${scene}::${idx}::${texts[idx].trim()}`);
      try {
        await candidates.nth(idx).click({ timeout: 3000 });
        await page.waitForTimeout(800);
      } catch {
        break; // 더 못 넘긴다 — 여기까지 잰 것으로 판정한다.
      }
    }

    // 초록일 때도 무엇을 재고 초록인지 보이게 남긴다.
    test.info().annotations.push({
      type: "walk",
      description: visited.join(" → "),
    });

    expect(
      measured,
      `${route} 에서 잴 타깃이 한 단계도 없었다 — start 는 200 이었는데 화면이 ` +
        `아무것도 안 그렸다는 뜻이고, 이 통과는 무의미하다.`,
    ).toBeGreaterThan(0);

    // 마지막 씬(n === m)을 밟았는가. 중간에서 멈춘 채 나온 초록은 미션의 뒷부분을
    // 한 번도 안 잰 것이다.
    const seen = visited
      .map((v) => v.match(/SCENE (\d+)\/(\d+)/))
      .filter((m): m is RegExpMatchArray => m !== null);
    const reachedEnd = seen.some((m) => m[1] === m[2]);
    const furthest = seen.length ? seen[seen.length - 1][1] : "?";
    const total = seen.length ? seen[0][2] : "?";
    expect(
      reachedEnd,
      `${route} — 마지막 씬까지 못 갔다 (${furthest}/${total} 에서 멈춤).\n` +
        `걸어간 자리: ${visited.join(" → ")}\n` +
        `이 스펙이 재려는 것은 미션 씬 **안쪽** 이므로, 뒷부분을 안 밟고 나온 초록은 ` +
        `거짓이다. 씬을 넘기는 조건이 바뀌었는지(배정형 씬의 제출 조건 등), 헤더의 ` +
        `'Scene n/m' 표기가 바뀌었는지, 걸음 예산(STEPS=${STEPS})이 모자란지 확인할 것.`,
    ).toBe(true);

    const bad = [...violations];
    expect(bad, `${MIN}px 미만 타깃:\n${bad.join("\n")}`).toEqual([]);
  });
}
