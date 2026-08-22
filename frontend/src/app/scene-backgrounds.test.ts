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
const CHARACTERS = [
  "daniel",
  "david",
  "elijah",
  "esther",
  "jesus",
  "job",
  "joseph",
  "moses",
  "peter",
  "ruth",
  "solomon",
];

/** `url(/images/scenes/david/${scene.currentScene}.webp)` 에서 폴더와 확장자를 꺼낸다. */
const BACKGROUND =
  /url\(\/images\/scenes\/([a-z]+)\/\$\{scene\.currentScene\}\.([a-z0-9]+)\)/;
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
});
