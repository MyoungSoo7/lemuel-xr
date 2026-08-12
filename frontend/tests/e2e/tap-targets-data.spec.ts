import { test, expect, Page } from "@playwright/test";

/**
 * 44px 터치 타깃 검사 — **데이터가 있어야 나타나는 화면**용. 2026-08-12 신설.
 *
 * 왜 파일이 따로 있는가. `tap-targets.spec.ts` 는 백엔드 없이 돌고, CI 도 그렇게 돈다.
 * 그런데 이 앱의 누르는 것들 상당수는 **백엔드가 응답해야만 그려진다.** `/topics/journal`
 * 의 감정 칩은 `emotions = catalog?.catalog ?? []` 라서 백엔드가 없으면 빈 배열이고,
 * 버튼이 0 개면 「44px 미만이 없다」가 참이 되어 검사는 초록이다.
 *
 * 즉 **잴 것이 없어서 통과**했다. 백엔드를 띄우고 같은 검사를 돌리니 그 자리에서
 * 12 개가 빨개졌다(2026-08-12: 감정 칩 50×34, /values 정의하기 68×30,
 * 북마크 빈 상태 링크 202×20). 앞선 boundingBox 결함과 같은 부류다 — 초록이
 * 초록의 이유를 보증하지 않는다.
 *
 * 그래서 여기서는 응답을 **가로채서 채워 넣고** 잰다. 백엔드 없이도 「목록이 있는
 * 상태」를 CI 에서 잴 수 있다.
 *
 * 한계는 정직하게 적어 둔다 — 픽스처는 **모양을 고정한 사본**이라 서버 계약이 바뀌면
 * 같이 낡는다. 아래 두 픽스처는 2026-08-12 에 실제로 뜬 백엔드 응답을 받아 적어
 * 만든 것이고(지어낸 모양이 아니다), 그래도 계약 변경을 자동으로 따라가지는 않는다.
 * 이 파일은 「응답이 오면 무엇이 그려지는가」를 재는 것이지 계약을 검증하지 않는다.
 */

const MIN = 44;

// 실제 응답에서 받아 적음(모양 보존, 길이만 줄임).
const JOURNAL_CATALOG = {
  catalog: [
    {
      emotion: "ANXIOUS",
      emotionLabel: "불안",
      validation: "불안은 믿음 없음의 증거가 아닙니다.",
      verses: [
        { ref: "시편 34:4", text: "내가 여호와께 구하매 내게 응답하시고" },
      ],
      reflectionQuestions: ["지금 가장 두려운 것은 무엇입니까?"],
    },
    {
      emotion: "LONELY",
      emotionLabel: "외로움",
      validation: "혼자라고 느끼는 마음도 성경 안에 있습니다.",
      verses: [{ ref: "시편 25:16", text: "주여 나는 외롭고 괴로우니" }],
      reflectionQuestions: ["오늘 누구를 떠올리셨습니까?"],
    },
  ],
  safetyFooter: "본 서비스는 영적 단련 교육 콘텐츠입니다.",
  aiFooter: "AI 보조 — 본문은 성경 참조.",
};

// `BookmarkedCard` (src/lib/api/content.ts) 모양. 카드가 그려져야 ♥·📖 가 나온다.
const BOOKMARKS = [
  {
    topicContentId: 1,
    topicId: 3,
    title: "두려움 앞에서",
    scriptureRef: "시편 23:4",
    body: "내가 사망의 음침한 골짜기로 다닐지라도",
    anchorCharacter: "david",
    bookmarkedAt: "2026-08-01T00:00:00Z",
  },
  {
    topicContentId: 2,
    topicId: null,
    title: "기다림",
    scriptureRef: null,
    body: "때가 이르면 거두리라",
    anchorCharacter: null,
    bookmarkedAt: "2026-08-02T00:00:00Z",
  },
];

// `ProfileResponse` (src/lib/api/values.ts). 하나만 정의해 두면 그 카드는 "수정" +
// "오늘 실천 +1" 두 개가 되고, 나머지는 "정의하기" 가 된다 — 두 분기를 다 잰다.
const VALUES_PROFILE = {
  values: {
    "1": {
      title: "정직",
      anchor_character: "joseph",
      anchor_scripture: "창세기 39:9",
      note: "손해를 보더라도",
    },
  },
  stats: {
    totalPractices7d: 3,
    countByValue: { "1": 3 },
    cdrIndex: 42,
    tier: "성장",
  },
};

async function mockBackend(page: Page) {
  const json = (body: unknown) => ({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify(body),
  });
  await page.route("**/api/content/journal/guidance*", (r) =>
    r.fulfill(json(JOURNAL_CATALOG)),
  );
  await page.route("**/api/content/bookmarks", (r) =>
    r.fulfill(json(BOOKMARKS)),
  );
  await page.route("**/api/values/me", (r) => r.fulfill(json(VALUES_PROFILE)));
}

type Small = { tag: string; text: string; w: number; h: number; cls: string };

/**
 * 재는 방식은 `tap-targets.spec.ts` 와 같되, **분모(전체 개수)를 같이 돌려준다.**
 * 분모가 없으면 「작은 것 0 개」와 「그려진 것이 0 개」가 구별되지 않는다 — 이 파일이
 * 존재하는 이유가 바로 그 구별이므로, 여기서는 절대 생략하지 않는다.
 */
async function measure(page: Page) {
  return page.evaluate((min) => {
    const small: Small[] = [];
    let total = 0;
    const nodes = document.querySelectorAll<HTMLElement>(
      'a[href], button, [role="button"], input:not([type="hidden"]), select, textarea',
    );
    for (const el of Array.from(nodes)) {
      // 자리지기 사본(CrisisFooter) 은 보이지도 눌리지도 않는다 — 세면 안 된다.
      if (el.closest('[aria-hidden="true"]')) continue;
      const r = el.getBoundingClientRect();
      if (r.width === 0 && r.height === 0) continue;
      total++;
      if (r.width >= min && r.height >= min) continue;
      small.push({
        tag: el.tagName.toLowerCase(),
        text: (el.textContent ?? "").trim().slice(0, 30),
        w: Math.round(r.width),
        h: Math.round(r.height),
        cls: el.className.toString().slice(0, 60),
      });
    }
    return { total, small };
  }, MIN);
}

/**
 * `anchor` — 픽스처가 실제로 화면에 닿았는지 보는 못. 이게 없으면 라우트 가로채기가
 * 조용히 빗나가도(경로 패턴 오타, 쿼리스트링 변경 등) 검사는 다시 「잴 것이 없어서」
 * 초록이 된다. 이 파일이 고치려는 결함을 이 파일이 다시 저지르지 않게 한다.
 */
const CASES = [
  { route: "/topics/journal", anchor: "불안" },
  { route: "/topics/bookmarks", anchor: "두려움 앞에서" },
  { route: "/values", anchor: "오늘 실천 +1" },
] as const;

for (const { route, anchor } of CASES) {
  test(`${route} (데이터 있음) — 모든 터치 타깃이 ${MIN}px 이상이다`, async ({
    page,
  }) => {
    await mockBackend(page);
    await page.goto(route);
    await page.waitForLoadState("networkidle");

    // ① 픽스처가 닿았는가. 안 닿았으면 아래 측정은 무의미하므로 여기서 멈춘다.
    await expect(
      page.getByText(anchor, { exact: false }).first(),
      `픽스처가 화면에 닿지 않았다 — 가로채기가 빗나갔거나 응답 모양이 바뀌었다. ` +
        `이 상태의 통과는 무의미하다.`,
    ).toBeVisible();

    // ② 그제서야 잰다.
    const { total, small } = await measure(page);
    expect(total, "잴 타깃이 하나도 없다").toBeGreaterThan(0);
    expect(
      small,
      `${MIN}px 미만 타깃 (전체 ${total} 개 중 ${small.length} 개):\n` +
        small
          .map((s) => `  ${s.tag} "${s.text}" ${s.w}×${s.h} — ${s.cls}`)
          .join("\n"),
    ).toEqual([]);
  });
}

/**
 * **빈 목록도 화면이다.** 2026-08-13 추가.
 *
 * 위 세 경우는 전부 「데이터가 있는」 화면이고, 빈 상태는 어느 검사에도 안 잡힌다.
 * `tap-targets.spec.ts` 는 백엔드가 없어 응답이 영영 안 오고, 그동안 `isLoading` 이
 * 참이라 **빈 상태 UI 는 렌더되지도 않는다.** 위 `CASES` 는 반대로 목록을 채워 넣으니
 * 역시 안 그려진다. 즉 빈 상태는 구조적으로 사각이었고, 실제로 백엔드가 `[]` 를
 * 돌려주자 여기 링크가 **202×20** 으로 드러났다(2026-08-12 실측).
 *
 * 「응답이 없다」와 「응답이 비었다」는 다른 화면이다. 후자를 재는 자리가 여기다.
 */
test(`/topics/bookmarks (빈 목록) — 모든 터치 타깃이 ${MIN}px 이상이다`, async ({
  page,
}) => {
  await page.route("**/api/content/bookmarks", (r) =>
    r.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );
  await page.goto("/topics/bookmarks");
  await page.waitForLoadState("networkidle");

  // 빈 상태가 실제로 그려졌는지부터. 안 그려졌으면 아래 측정은 무의미하다.
  await expect(
    page.getByRole("link", { name: /카드를 ♡ 로 담아보세요/ }),
    `빈 상태 UI 가 안 나왔다 — 아직 로딩 중이거나 가로채기가 빗나갔다. ` +
      `이 상태의 통과는 무의미하다.`,
  ).toBeVisible();

  const { total, small } = await measure(page);
  expect(total, "잴 타깃이 하나도 없다").toBeGreaterThan(0);
  expect(
    small,
    `${MIN}px 미만 타깃 (전체 ${total} 개 중 ${small.length} 개):\n` +
      small
        .map((s) => `  ${s.tag} "${s.text}" ${s.w}×${s.h} — ${s.cls}`)
        .join("\n"),
  ).toEqual([]);
});
