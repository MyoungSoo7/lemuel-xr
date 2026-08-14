import coreWebVitals from "eslint-config-next/core-web-vitals";
import typescript from "eslint-config-next/typescript";

/*
  ESLint flat config.

  왜 이 파일이 이제야 생겼나 —
  Next 16 이 `next lint` 를 제거했다. 그전까지 이 리포는 설정 파일 없이
  `next lint` 한 줄에 기대고 있었고, 그 명령이 내부적으로 규칙 세트를 끼워
  넣어줬다. 명령이 사라지자 남은 건 `"lint": "next lint"` 뿐이었고,
  Next 16 은 `lint` 를 **프로젝트 디렉터리 인자**로 읽어서
  `no such directory: .../frontend/lint` 라는 엉뚱한 말을 하고 죽었다.
  "린트가 없다" 가 아니라 "린트가 있는 척하며 깨져 있다" 였다.

  이제 규칙 세트를 여기 명시하고 `eslint` 를 직접 부른다. 설정이 코드로
  보이므로, 규칙이 바뀌면 diff 에 남는다.

  `core-web-vitals` 는 내부에서 base 설정(`eslint-config-next`)을 이미
  포함한다 — 둘 다 적으면 중복이라 하나만 쓴다.
*/
const config = [
  {
    ignores: [
      ".next/**",
      "out/**",
      "node_modules/**",
      "playwright-report/**",
      "test-results/**",
      // vitest 커버리지 HTML 리포트. istanbul 이 만든 벤더 JS 가 들어 있어서
      // 빼두지 않으면 `--max-warnings=0` 이 남의 코드 때문에 빨개진다.
      // (`.gitignore` 는 eslint flat config 에 영향을 주지 않는다 — 별도로 적어야 한다.)
      "coverage/**",
      "next-env.d.ts",
      "tsconfig.tsbuildinfo",
    ],
  },
  ...coreWebVitals,
  ...typescript,
];

export default config;
