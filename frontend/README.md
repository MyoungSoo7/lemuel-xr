# lemuel-xr — Frontend (Web MVP, Unity 전 단계)

> **위치**: Track B (서사 게임) MVP 의 _웹 사전 검증_ 단계. Unity 6 XR 빌드 (Quest 3 / Vision Pro / Galaxy XR) 들어가기 전에 _브라우저 + R3F_ 로 Scene 인터랙션을 빠르게 prototyping.
> **상위 문서**: [`../CLAUDE.md`](../CLAUDE.md) · [`../docs/MVP-JOSEPH.md`](../docs/MVP-JOSEPH.md) · [`../docs/FUNCTIONAL-SPEC.md`](../docs/FUNCTIONAL-SPEC.md)
> **백엔드 API 명세**: [`../docs/SEQUENCE-DIAGRAMS.md`](../docs/SEQUENCE-DIAGRAMS.md) §3 (요셉 미션 한 세션 흐름)

---

## 0. 위치 — Unity _전_ 단계인 이유

| 단계                      | 매체                              | 목표                                                       |
| ------------------------- | --------------------------------- | ---------------------------------------------------------- |
| **현 단계 (이 디렉토리)** | Web (Next.js 16 + React 19 + R3F) | Scene 구조·인터랙션·LLM 캐시 패턴 _브라우저에서 빠른 검증_ |
| Phase 2                   | Unity 6 + OpenXR                  | Quest 3 / Vision Pro / Galaxy XR 빌드. 본격 XR             |
| Phase 3                   | 같은 Unity + 추가 인물            | 모세 / 다윗 / 예수 미션                                    |

웹 단계의 가치:

- **빠른 iteration** — Unity 빌드 사이클 (5~30분) 대비 Next.js HMR 즉시
- **데모 가능** — Quest 없는 사용자 / 인터뷰 대상자에게 _브라우저 링크 한 줄_
- **API 검증** — 백엔드 (Spring Boot) 의 `/api/game/joseph/*` endpoints 가 _실제로 잘 동작_ 하는지 사전 통합 테스트
- **에셋 검증** — Imagen 4.0 fast 로 생성한 Scene 1~5 배경 이미지 (commit `fac9e0f`) 를 _실제 R3F overlay 위_ 에서 검증

---

## 1. 기술 스택

| 영역         | 기술                                              | 버전                             |
| ------------ | ------------------------------------------------- | -------------------------------- |
| Framework    | Next.js                                           | ^16.2.1                          |
| UI           | React + React DOM                                 | ^19.0.0                          |
| 3D / XR      | three.js + @react-three/fiber + @react-three/drei | three ^0.171 / r3f ^9 / drei ^10 |
| 상태         | Zustand                                           | ^5.0.2                           |
| 데이터 fetch | TanStack Query + Axios                            | rq ^5.62 / axios ^1.7            |
| 스타일       | Tailwind CSS                                      | ^4.0.0                           |
| 타입         | TypeScript                                        | ^5.7                             |

---

## 2. 디렉토리 구조

```
frontend/
├── public/
│   └── images/            # Scene 1~5 배경 이미지 (Imagen 4.0 fast 생성)
└── src/
    ├── app/                       # Next.js App Router
    │   ├── layout.tsx              # 전역 layout
    │   ├── providers.tsx           # React Query / Zustand provider 묶음
    │   ├── globals.css             # Tailwind v4 entry
    │   ├── page.tsx                # 홈 — 사용자 진입 화면 (감정 입력 → Theme 추천)
    │   ├── joseph/
    │   │   └── page.tsx            # 요셉 미션 Scene 1~5 R3F 인터랙션
    │   └── api/
    │       └── [...path]/route.ts  # Next.js → Spring Backend 프록시 rewrite
    └── lib/
        └── api/
            ├── client.ts            # axios instance (baseURL = same-origin → next rewrites)
            ├── emotion.ts           # /api/emotion/* 호출
            └── game.ts              # /api/game/joseph/* 호출
```

> ⚠️ **`src/lib/api/client.ts` 의 baseURL** — `sparta-next-js-build-args` skill 의 핵심 규칙 적용: 브라우저는 _same-origin_, SSR 만 K8s 내부 호스트. `NEXT_PUBLIC_*` 절대 URL 박지 말 것 (2026-05-17 sparta-msa /products 사고 패턴).

---

## 3. 로컬 실행

### 사전 조건

- Node.js 20 이상
- npm (또는 pnpm / yarn — 본 README 는 npm 기준)
- 백엔드 Spring Boot 가 동시 실행 중 (포트 8080 / 또는 환경변수 `API_URL_INTERNAL` 지정)

### 명령

```bash
cd frontend
npm install
npm run dev           # localhost:3000
```

추가 명령:

```bash
npm run build         # 프로덕션 빌드 (Next.js standalone)
npm run start         # 빌드 결과 실행 (0.0.0.0:3000)
npm run lint          # ESLint
npm run type-check    # tsc --noEmit
```

---

## 4. 백엔드 프록시 — `src/app/api/[...path]/route.ts`

브라우저의 `/api/*` 호출은 Next.js 의 catch-all route 가 받아서 백엔드 (`API_URL_INTERNAL`) 로 _서버 사이드 프록시_. 이유:

- 브라우저는 _same-origin_ `/api/*` 만 호출 — 빌드 ARG 에 절대 URL 박지 않음
- CORS 회피
- K8s 내부 호스트 (`http://sparta-gateway:8080` 같은) 가 _클라이언트 번들에 노출 안 됨_

환경변수:

```bash
# .env.local (gitignored)
API_URL_INTERNAL=http://localhost:8080      # 로컬 백엔드 직접
# 또는 K8s 환경
API_URL_INTERNAL=http://lemuel-xr-backend:8080
```

`NEXT_PUBLIC_API_URL` 은 **사용하지 말 것** — 클라이언트 번들에 박힘 (sparta-msa 사고 패턴).

---

## 5. R3F 인터랙션 패턴 — Scene 1~5

`src/app/joseph/page.tsx` 의 구조:

```tsx
<Canvas>
  {/* 배경 — Imagen 4.0 생성 이미지를 plane 으로 */}
  <SceneBackground sceneId={1} />

  {/* 인터랙션 객체 — Scene 별 분기 */}
  <SceneObjects sceneId={1} onDecision={handleDecision} />

  {/* 카메라 + 라이팅 */}
  <PerspectiveCamera ... />
  <ambientLight intensity={0.8} />
</Canvas>
```

Scene 분기 결정은 `useGameStore` (Zustand) 로 추적, 백엔드에 `POST /api/game/joseph/decisions` 로 적재. LLM 캐시 hit 도 같이 받아옴 (사전 9패턴 캐싱 — `MVP-JOSEPH.md` §2 Scene 2/3).

---

## 6. Scene 에셋 — Imagen 4.0 fast (commit `fac9e0f`)

`public/images/` 의 Scene 1~5 배경:

| Scene | 파일                                 | 묘사                                  |
| ----- | ------------------------------------ | ------------------------------------- |
| 1     | `scene1_paraoh_dream.{jpg,webp}`     | 어두운 궁전 + 마른/살찐 소            |
| 2     | `scene2_harvest_field.{jpg,webp}`    | 황금빛 보리밭 + 7 창고                |
| 3     | `scene3_famine_lines.{jpg,webp}`     | 누런 사막 + 3개 줄 (농민/이주민/상인) |
| 4     | `scene4_brothers_reunion.{jpg,webp}` | 궁전 안 형제 재회                     |
| 5     | `scene5_recovery.{jpg,webp}`         | 황혼 시간 평온                        |

⚠️ Imagen 4.0 fast 생성 결과 → 신학·문화 자문 검토 후 채택 결정 (CLINICAL-REVIEW.md / 신학 자문 워크플로). 현재는 _prototype 시각화_ 목적.

---

## 7. 백엔드 API 호출 흐름

`src/lib/api/` 의 세 파일이 백엔드 endpoints 와 매핑:

| 파일         | 호출                                                         | 백엔드 endpoint                                    |
| ------------ | ------------------------------------------------------------ | -------------------------------------------------- |
| `emotion.ts` | `classifyEmotion(text)`                                      | `POST /api/emotion/classify`                       |
| `game.ts`    | `startJoseph()` / `decide(sceneId, decision)` / `complete()` | `POST /api/game/joseph/{start,decisions,complete}` |
| `client.ts`  | axios instance                                               | (모든 호출의 base)                                 |

전체 흐름은 `../docs/SEQUENCE-DIAGRAMS.md` §3 참고.

---

## 8. 빌드 / 배포 (Phase 2 예정)

- Dockerfile 은 Next.js _standalone_ 출력 기반 (`next.config.ts` 의 `output: "standalone"`)
- 빌드 ARG 에 **절대 API URL 박지 말 것** — `Dockerfile` 의 `NEXT_PUBLIC_API_URL` 기본값 비움 (sparta-msa 패턴 적용)
- K8s 배포는 helm-deploy 의 `charts/lemuel-xr/` 에 frontend chart 추가 예정 (현재 백엔드만 있음)

---

## 9. 향후 Unity 마이그레이션 시점

이 웹 prototype 의 종결 조건 (Unity 진입 트리거):

- [ ] 5명 이상 사용자 테스트 — _Scene 인터랙션이 의미 전달이 되는가_
- [ ] LLM 캐시 9패턴 모두 검증 — _서로 다른 결말 톤_ 이 사용자에게 _구별되어_ 다가오는가
- [ ] 백엔드 API 6개 endpoint _p95 latency < 800ms_ (LLM 비포함)
- [ ] 신학 / 임상 자문 1차 검토 통과 — Scene 5 결말 메시지 톤
- [ ] Imagen 배경 이미지의 _시각적 일관성_ 검증

이 5가지 통과하면 Unity 6 진입. 그 전엔 R3F 단계 유지.

---

## 10. 알려진 한계 (웹 prototype 단계)

- **VR 몰입 부재** — Canvas 평면 인터랙션. _손 잡기·공간감_ 은 Unity 진입 후 검증
- **R3F 의 햅틱 제어 부재** — 떨림·진동 effect 는 시각/오디오 cue 로만 시뮬레이션
- **모바일은 레이아웃만 잰다** — 2026-08-12 부터 Playwright 에 모바일 프로젝트 3개
  (iPhone SE 320px · iPhone 15 · Pixel 7) 가 붙었고 `tests/e2e/mobile-hybrid.spec.ts` 가
  「위기 푸터가 본문을 덮지 않는다」·「가로 스크롤이 없다」·「PWA 계약」을 검사한다.
  다만 재는 것은 **레이아웃**이지 성능이 아니다 — 프레임률·로드 시간·R3F 씬의 모바일
  GPU 부하는 여전히 **한 번도 재지 않았다.**
- **Safari 엔진 미검증** — 위 모바일 프로젝트의 엔진은 셋 다 **Chromium** 이다.
  `iPhone` 이라는 이름은 뷰포트·DPR·터치·UA 를 뜻한다. Playwright 1.60 이 받는 WebKit
  빌드가 `webkit-mac-15` 하나뿐이라 이 개발기(macOS 26.6)에서는 `newPage()` 가 걸려
  돌지 않는다. `PW_WEBKIT=1` 로 `safari-iphone-15` 프로젝트를 붙일 수 있고, WebKit 이
  도는 환경에서만 의미가 있다. **WebKit 고유 결함은 아직 아무도 안 봤다.**
- **실기기 안전영역 미검증** — 노치·홈 인디케이터 대응(`viewport-fit=cover` +
  `env(safe-area-inset-*)`)은 코드에 있지만, 에뮬레이션은 그 값을 전부 0 으로 준다.
  홈화면 설치 상태의 실제 iPhone 에서는 **확인된 바 없다.**
- **다국어 미적용** — 한국어 only. i18n 은 V3 (글로벌 확장) 에서

---

## 참고

- 본 frontend 의 첫 commit 은 `714c012` "feat(frontend): Next.js 16 + React 19 + R3F MVP 웹 프론트엔드 (Unity 전 단계)" — 2026-05-21 새벽 (KST)
- Scene 배경 이미지는 `fac9e0f` "feat(frontend): Scene 1~5 배경 이미지 (Imagen 4.0 fast) + R3F overlay"
- 이 README 는 위 두 commit 의 _사후 정리_ 로 작성됨 (commit history 가 작성 시점 진실)
