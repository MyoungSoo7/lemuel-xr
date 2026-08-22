import { readFileSync, existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";

/**
 * 인물 페이지가 참조하는 씬 배경 파일이 *실제로 디스크에 있는가* 를 잰다.
 *
 * 이건 렌더 테스트로는 절대 안 잡히는 종류의 고장이다. 배경은 CSS
 * `backgroundImage: url(...)` 이라 파일이 없어도 컴포넌트는 멀쩡히 렌더되고,
 * 예외도 없고, jsdom 은 애초에 이미지를 안 가져온다. 브라우저에서도 404 는
 * 네트워크 탭에만 남고 콘솔에는 거의 안 뜬다. 사용자에게 보이는 증상은
 * "배경이 그냥 검다" 뿐이고, 그건 그 씬까지 실제로 플레이해 봐야 안다.
 *
 * 실제로 걸릴 두 가지:
 *   1. 확장자 드리프트 — 2026-08-15 에 39장을 jpg → webp 로 바꿨다. 페이지의
 *      문자열과 디스크의 파일은 서로를 모른다. 한쪽만 바뀌면 전부 검게 나온다.
 *   2. 씬 추가 — 헤더의 `Scene n/N` 만 올리고 이미지를 안 넣으면 마지막 씬만 검다.
 *
 * 그래서 페이지 소스에서 *경로와 확장자를 직접 읽어* 파일 존재를 확인한다.
 * 여기 상수를 따로 두면 그 상수가 또 드리프트한다.
 */

const APP_DIR = join(__dirname);
const PUBLIC_DIR = join(__dirname, "..", "..", "public");

/*
  ruth 는 2026-08-22 에 들어왔다. 그전까지 여기 없었던 것은 의도된 제외였다 —
  `public/images/scenes/ruth/` 에 한 장도 없었고 `/ruth` 는 단색 그라디언트를 썼다.
  그 주석은 "배경이 저작되면 그때 여기에 ruth 를 넣는다" 로 끝났고, 이 줄이 그
  약속을 지킨 자리다. 다섯 장(1~5.webp)이 디스크에 있고 페이지가 그 경로를
  읽는다는 걸 확인한 뒤에 넣었다.
*/
/*
  아브라함도 같은 길을 밟았다. 바로 위 자리에는 2026-08-22 까지 "여기 없는 것은
  실수가 아니다 — 한 장도 없어서 다섯 씬 전부 검게 나온다" 는 주석이 있었고,
  같은 날 다섯 장(1~5.webp)을 뽑아 눈으로 확인한 뒤 이 줄로 바꿨다.
*/
/*
  라합도 같은 길을 밟았다 — 여섯 장(1~5.webp + 4-alt.webp)을 뽑아 눈으로 확인한 뒤 넣었다.
  다만 이 인물만 **변형 배경**이 하나 더 있다(아래 VARIANTS). 그것까지 여기서 재지 않으면
  `docs/RAHAB-RUNTIME-SIGNOFF.md` 의 「배경 이미지 — 사람 검수 대기」 절이 말하는
  「파일이 디스크에 있느냐까지는 기계가 잰다」가 그 인물에 대해서만 거짓이 된다.
*/
const CHARACTERS = [
  "abraham",
  "daniel",
  "david",
  "elijah",
  "esther",
  "jacob",
  "jesus",
  "job",
  "joseph",
  "moses",
  "peter",
  "rahab",
  "ruth",
  "solomon",
];

/*
  변형 배경 — 씬 번호 뒤에 접미사가 붙는 파일. 지금은 라합 Scene 4 하나뿐이다:
  §2-5 동의 카드가 「창과 줄을 보지 않는다」고 약속하는데 그 둘은 자막이 아니라
  **배경에** 있어서, 축약 블록이 `background_variant: no_window_no_cord` 를 덮고
  화면이 그 값으로 `4-alt.webp` 를 고른다(rahab/page.tsx).

  `scenes` 는 손으로 적는다 — 어느 씬이 변형을 갖는지는 시나리오 yml 에 있고 이 검사는
  프론트만 읽기 때문이다. 그래서 접미사 쪽만이라도 드리프트를 막는다: 아래 검사가
  **페이지 소스에 그 접미사가 실제로 있는지** 를 먼저 단정한다. 페이지에서 변형을
  없애면 이 표는 초록인 채로 남지 못한다.
*/
const VARIANTS: Record<string, { suffix: string; scenes: number[] }> = {
  rahab: { suffix: "-alt", scenes: [4] },
};

/**
 * `/images/scenes/david/${scene.currentScene}.webp` 에서 폴더와 확장자를 꺼낸다.
 *
 * 감싸는 `url(...)` 은 일부러 요구하지 않는다 — 라합은 변형 배경 때문에 경로를
 * 먼저 변수(`background`)에 담고 `url(${background})` 로 쓴다. `url(` 을 붙여 두면
 * 그 인물에서 이 정규식이 안 맞고, 위 「파싱 자체를 단정한다」 검사가 그걸 잡는다.
 * 접미사가 붙은 변형 경로(`...}-alt.webp`)는 `\.` 요구 때문에 여기 안 걸린다 —
 * 그쪽은 아래 VARIANTS 가 따로 잰다.
 */
const BACKGROUND =
  /\/images\/scenes\/([a-z]+)\/\$\{scene\.currentScene\}\.([a-z0-9]+)/;
/** `David — Scene {scene.currentScene}/6 · Mode: VR` 에서 총 씬 수를 꺼낸다. */
const SCENE_COUNT = /Scene \{scene\.currentScene\}\/(\d+)/;

describe.each(CHARACTERS)("%s 씬 배경", (character) => {
  const source = readFileSync(join(APP_DIR, character, "page.tsx"), "utf8");

  const bg = source.match(BACKGROUND);
  const count = source.match(SCENE_COUNT);

  it("배경 경로와 씬 수를 페이지에서 읽을 수 있다", () => {
    // 이 두 정규식이 안 맞으면 아래 검사는 조용히 0건이 된다 — 초록불인데
    // 아무것도 안 재는 상태다. 그래서 먼저 파싱 자체를 단정한다.
    expect(
      bg,
      `${character}/page.tsx 의 backgroundImage 패턴이 바뀌었다`,
    ).not.toBeNull();
    expect(
      count,
      `${character}/page.tsx 의 "Scene n/N" 헤더가 바뀌었다`,
    ).not.toBeNull();
  });

  it("참조하는 폴더 이름이 인물 이름과 같다", () => {
    expect(bg?.[1]).toBe(character);
  });

  it("선언한 씬 수만큼 배경 파일이 실제로 있다", () => {
    const ext = bg![2];
    const total = Number(count![1]);
    const missing = Array.from({ length: total }, (_, i) => i + 1)
      .map((n) => `images/scenes/${character}/${n}.${ext}`)
      .filter((rel) => !existsSync(join(PUBLIC_DIR, rel)));

    expect(missing, `없는 배경 파일: ${missing.join(", ")}`).toEqual([]);
  });

  const variant = VARIANTS[character];
  if (variant) {
    it("변형 배경 파일도 실제로 있다", () => {
      const ext = bg![2];
      // 먼저 접미사가 페이지에 실재하는지 본다. 이게 없으면 아래 존재 검사는
      // "아무도 안 읽는 파일이 디스크에 있다"만 재는 가짜 초록이 된다.
      expect(
        source.includes(`\${scene.currentScene}${variant.suffix}.${ext}`),
        `${character}/page.tsx 가 변형 접미사 '${variant.suffix}' 를 더 이상 쓰지 않는다 — VARIANTS 를 지워야 한다`,
      ).toBe(true);

      const missing = variant.scenes
        .map((n) => `images/scenes/${character}/${n}${variant.suffix}.${ext}`)
        .filter((rel) => !existsSync(join(PUBLIC_DIR, rel)));

      expect(missing, `없는 변형 배경 파일: ${missing.join(", ")}`).toEqual([]);
    });
  }
});
