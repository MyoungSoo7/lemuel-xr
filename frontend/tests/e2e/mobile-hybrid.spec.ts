import { test, expect } from "@playwright/test";

/**
 * 하이브리드(웹 + 모바일) 회귀 검사 — 2026-08-12 신설.
 *
 * 이 파일이 있는 이유. 이 프론트는 데스크탑 Chrome 하나로만 검증돼 왔고
 * (`playwright.config.ts` 의 projects 가 1개였다), 그래서 모바일에서 **위기 상담
 * 푸터가 본문을 덮고 있었는데 아무도 몰랐다.** 펼친 푸터는 205px 인데 본문 하단
 * 여백은 `pb-12`(48px) 고정이었다. 화면은 멀쩡해 보인다 — 덮인 쪽이 아래에 있으니까.
 *
 * 그래서 여기서 재는 것은 「예쁘게 나오는가」가 아니라 **가려지는가**다. 이 앱에서
 * 가려지면 안 되는 것은 정해져 있다(`docs/safety-guidelines.md` §1 — 위기 자원은
 * 어떤 페이지에서도 사라지지 않는다).
 *
 * 이 파일은 데스크탑 프로젝트에서도 돈다. 덮임 결함은 모바일에서 먼저 눈에 띄었을
 * 뿐 폭과 무관하게 있던 것이라, 폭을 가려 가며 재지 않는다.
 */

const ROUTES = ["/", "/david", "/topics", "/values"];
const FOOTER = { role: "region" as const, name: "위기 상담 자원 안내" };

test.describe("하이브리드 — 위기 푸터가 본문을 덮지 않는다", () => {
  for (const route of ROUTES) {
    test(`${route} — 접힘·펼침 모두에서 본문 끝이 푸터에 깔리지 않는다`, async ({
      page,
    }) => {
      await page.goto(route);
      const footer = page.getByRole(FOOTER.role, { name: FOOTER.name });
      await expect(footer).toBeVisible();

      /*
        재는 것은 「여백이 몇 px 인가」가 아니라 **본문 마지막 픽셀이 푸터 밑에 들어가
        있는가**다. 여백으로 재면 자리를 잡는 방식(고정 padding → JS 변수 → 흐름 사본)을
        바꿀 때마다 검사가 같이 흔들리고, 정작 물어야 할 것을 안 묻게 된다.

        끝까지 스크롤한 뒤에 본다 — 덮임은 문서 맨 아래에서만 드러난다.

        스크롤과 두 상자 읽기를 **한 번의 evaluate 안에서** 끝내는 것이 중요하다.
        전에는 `locator.boundingBox()` 로 따로 읽었는데, 그 API 는 대상 요소를 필요하면
        **화면 안으로 스크롤한다**. 즉 바로 앞줄에서 맞춰 놓은 「맨 아래」 상태를 측정
        행위 자체가 되돌린다. /david 는 `main` 이 `min-h-screen`(정확히 한 화면)이라
        main 을 보이게 하는 순간 스크롤이 0 으로 돌아갔고, 그래서 **깔리지 않았는데
        48px 깔렸다고** 나왔다(2026-08-12, 프로덕션 빌드에서 3/3 재현. 같은 상태를
        evaluate 안에서 재면 3/3 이 0). 개발 서버에서는 초록이라 안 보이던 결함이다.
        재는 행위가 재려는 상태를 바꾸면 그 초록도 빨강도 믿을 수 없다.
      */
      const occlusion = () =>
        page.evaluate(() => {
          window.scrollTo(0, document.body.scrollHeight);
          const main = document.querySelector("main")?.getBoundingClientRect();
          const bar = document
            .querySelector('[aria-label="위기 상담 자원 안내"]')
            ?.getBoundingClientRect();
          if (!main || !bar) throw new Error("main/footer 를 못 찾았다");
          return main.y + main.height - bar.y; // > 0 이면 그만큼 깔렸다
        });

      // ① 접힌 상태. iPhone SE 폭(320px)에서는 이 줄이 두 줄로 접혀 49px 가 된다.
      expect(
        await occlusion(),
        "접힌 상태에서 본문이 깔렸다",
      ).toBeLessThanOrEqual(0);

      // ② 펼친 상태. 여기가 진짜다 — 푸터 205px 대 예전 여백 48px 였다.
      await footer.getByRole("button").first().click();
      // 자리지기 사본에도 같은 문구가 있으므로 반드시 푸터 안에서 찾는다.
      await expect(footer.getByText(/혼자 견디지 마세요/)).toBeVisible();
      expect(
        await occlusion(),
        "펼친 상태에서 본문이 깔렸다",
      ).toBeLessThanOrEqual(0);
    });
  }

  test("펼친 목록은 화면을 넘기지 않는다 — 넘치면 목록 안에서 스크롤된다", async ({
    page,
  }) => {
    await page.goto("/");
    const footer = page.getByRole(FOOTER.role, { name: FOOTER.name });
    await footer.getByRole("button").first().click();

    const vh = page.viewportSize()!.height;
    const h = (await footer.boundingBox())!.height;
    // 자원 목록을 잘라내는 것은 금지다(safety-guidelines §1). 대신 이 블록이 화면
    // 전체를 먹지 않는지만 본다 — 먹으면 본문으로 돌아갈 길이 없다.
    expect(h).toBeLessThan(vh);
  });

  test("가로 스크롤이 생기지 않는다", async ({ page }) => {
    for (const route of ROUTES) {
      await page.goto(route);
      const overflow = await page.evaluate(
        () =>
          document.documentElement.scrollWidth -
          document.documentElement.clientWidth,
      );
      expect(overflow, `${route} 에서 가로 스크롤`).toBeLessThanOrEqual(0);
    }
  });
});

test.describe("하이브리드 — 설치 가능한 웹앱(PWA) 계약", () => {
  test("manifest·theme-color·apple-touch-icon 이 실제로 응답한다", async ({
    page,
    request,
  }) => {
    await page.goto("/");

    const href = await page
      .locator('link[rel="manifest"]')
      .first()
      .getAttribute("href");
    expect(href, "manifest link 가 문서에 없다").toBeTruthy();

    // 링크가 있는 것과 파일이 나오는 것은 다르다 — 받아서 내용을 본다.
    const res = await request.get(href!);
    expect(res.status()).toBe(200);
    const m = await res.json();
    expect(m.display).toBe("standalone");
    expect(m.start_url).toBeTruthy();
    // 아이콘은 선언만 있고 404 인 경우가 흔하다. 실제로 받아 본다.
    expect(Array.isArray(m.icons) && m.icons.length).toBeTruthy();
    for (const icon of m.icons) {
      const r = await request.get(icon.src);
      expect(r.status(), `${icon.src} 가 안 나온다`).toBe(200);
    }
    // maskable 이 하나는 있어야 안드로이드에서 흰 원 안에 박히지 않는다.
    expect(
      m.icons.some((i: { purpose?: string }) => i.purpose === "maskable"),
    ).toBe(true);

    await expect(page.locator('meta[name="theme-color"]')).toHaveAttribute(
      "content",
      /#/,
    );
    // iOS 는 manifest 의 icons 를 보지 않는다. 이 링크가 없으면 홈화면에 스크린샷이 박힌다.
    const apple = await page
      .locator('link[rel="apple-touch-icon"]')
      .first()
      .getAttribute("href");
    expect(apple, "apple-touch-icon 이 없다").toBeTruthy();
    expect((await request.get(apple!)).status()).toBe(200);
  });

  test("viewport 가 viewport-fit=cover 를 선언한다", async ({ page }) => {
    await page.goto("/");
    const content = await page
      .locator('meta[name="viewport"]')
      .first()
      .getAttribute("content");
    // 이게 없으면 iOS 에서 env(safe-area-inset-*) 이 전부 0 이 되고, 홈화면 설치
    // 상태에서 위기 푸터가 홈 인디케이터에 깔린다. CSS 쪽은 조용히 무효가 된다.
    expect(content).toContain("viewport-fit=cover");
  });
});
