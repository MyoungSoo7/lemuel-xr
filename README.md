# lemuel-xr

> **성경 기반 감정 회복 + 서사 게임 플랫폼** — AI 전방위 활용

---

## 한 줄 컨셉

성경의 인물·지혜를 매개로 **감정 회복(passive)** 과 **자기 서사화(active)** 를 양방향 제공하는 AI 기반 영적 웰빙 앱.

## 문서

| 파일                                       | 내용                                                 |
| ------------------------------------------ | ---------------------------------------------------- |
| [`docs/PLAN.md`](docs/PLAN.md)             | 전체 기획서 — 컨셉·아키텍처·경쟁분석·로드맵          |
| [`docs/BUILD-PLAN.md`](docs/BUILD-PLAN.md) | XR 빌드 플랜 — 시스템 아키텍처·sprint plan·위험·비용 |
| [`docs/MVP-JOSEPH.md`](docs/MVP-JOSEPH.md) | **MVP 요셉 XR 미션 상세 설계** (2026-05-20 결정)     |

## MVP 결정 (2026-05-20)

기존 PLAN.md 의 V1.0(트랙 A 정적 콘텐츠) → V2.0(요셉 게임) 순서를 **뒤집어** 요셉 XR 게임을 **V1.0 MVP** 로 시작.

이유: XR 의 _손·공간_ 메타포가 곡식 분배 결정 인터랙션과 가장 잘 맞고, 단일 미션이 6주 안에 완성 가능. 트랙 A 정적 콘텐츠는 Phase 2 로 이동.

자세한 시나리오·인터랙션·일정은 [`docs/MVP-JOSEPH.md`](docs/MVP-JOSEPH.md) 참조.

📌 **실제로 어떻게 됐나 (2026-08-22 시점)** — 요셉은 V1.0 으로 나갔지만 _XR 로는
아니다_. 나간 것은 웹이고, 인물도 요셉 하나가 아니라 8명이다. 트랙 A 도 "Phase 2 로
이동" 한 채 멈춘 게 아니라 `/topics` 로 함께 서비스되고 있다. 위 세 문단은 **당시의
결정 기록**으로 남긴다 — 지금 상태는 아래 "현재 단계" 를 볼 것.

## 듀얼 트랙 구조

```
사용자 진입
    │
    ▼
감정/의도 입력
    │
    ├──[감정/위로]──► 트랙 A: 정적 회복 콘텐츠
    │                일기·잠언·전도서·실천·북마크
    │
    └──[탐색/몰입]──► 트랙 B: 서사 게임
                     욥·엘리야·모세·다윗·요셉·예수·솔로몬·룻
```

트랙 B 의 인물 목록은 아래 표가 아니라 **`backend/.../game/domain/Character.kt` 의
`Character` enum 이 정본**이다. 설계표(`docs/PLAN.md` §2.2)에는 행이 더 있고, 행이
있다는 것과 앱에서 돌아간다는 것은 다른 사실이다. 두 집합의 어긋남은
`scripts/track_b_readiness.py` 가 잰다.

## 인물별 담당

| Theme | 인물 | 구원 유형 | 담당                                         |
| ----- | ---- | --------- | -------------------------------------------- |
| 8     | 요셉 | 경제      | [@MyoungSoo7](https://github.com/MyoungSoo7) |
| 9     | 모세 | 정치      | [@MyoungSoo7](https://github.com/MyoungSoo7) |
| 10    | 다윗 | 외세      | [@MyoungSoo7](https://github.com/MyoungSoo7) |
| 11    | 예수 | 영적      | [@godjinho](https://github.com/godjinho)     |

각 담당자가 _설계 문서·시나리오·신학 검토_ 1차 책임. 코드 구현은 별도 분담.

이 표는 **초기 4인물의 배정 기록**이다. 그 뒤 늘어난 인물(욥·엘리야·솔로몬·룻)의
담당은 여기 적힌 적이 없다 — 비어 있는 것이지 @MyoungSoo7 으로 기본값이 잡힌 게
아니다. 현재 런타임 인물 전체는 `Character` enum 을 볼 것.

## 핵심 차별화

- **개신교 + 한국 시장** — Hallow(가톨릭)·Pray.com(미국) 빈자리
- **게임 듀얼 트랙** — 정적 명상 앱들 (Calm/Headspace/Abide) 없는 능동 몰입
- **AI 전방위** — 묵상 생성·서사 분기·대화·TTS·이미지·번역

## 현재 단계

🚀 **웹으로 배포되어 돌아가고 있다** — `https://xr.lemuel.co.kr` (K3s + ArgoCD, 차트는
`helm-deploy/charts/lemuel-xr`).

이 문단은 오래 "Phase 0 — 시장 검증(계획)" 이라고 적혀 있었다. 배포되어 서비스 중인
제품을 리포 첫 화면이 미착수라고 말하고 있었던 것이라 2026-08-22 에 갚는다.
**아래 수치는 그날 실측이고 이 문단은 다시 낡는다** — 판단이 필요하면 문단이 아니라
오른쪽 칸의 명령을 돌릴 것.

| 층            | 상태                        | 실측 (2026-08-22)                                            | 재는 법                                       |
| ------------- | --------------------------- | ------------------------------------------------------------ | --------------------------------------------- |
| 백엔드        | 서비스 중                   | Kotlin 287파일 · `@Test` 652 · 컨트롤러 21 · 마이그레이션 37 | `find backend/src/main -name '*.kt' \| wc -l` |
| 웹 프론트     | 서비스 중                   | 미션 화면 8 + `/topics` 5 + `/values`                        | `ls frontend/src/app`                         |
| 트랙 B 콘텐츠 | 8인물 런타임 · 6인물 저작만 | `놀 수 있음 8 / 저작은 끝났으나 못 놂 6`                     | `python3 scripts/track_b_readiness.py`        |
| 게이트        | 초록 아님 — 부채가 장부에   | PASS 540 · FAIL 8 · **BLOCKED 117**                          | `python3 scripts/ci_gates.py`                 |
| XR (Unity)    | **미착수**                  | `unity/` 는 셋업 README 1개, `.cs` 0개                       | `find unity -name '*.cs' \| wc -l`            |

⚠️ **리포 이름이 `xr` 이지만 지금 사용자가 쓰는 것은 웹이다.** 백엔드에는 4기기 asset
manifest 가 있는데(`AssetManifestController`) 그것을 소비할 헤드셋 클라이언트가 아직
없다. `unity/README.md` 는 *만드는 법*이지 만든 것이 아니다.

⚠️ **BLOCKED 117 을 통과로 읽지 말 것** — "판정 불가" 다. 가장 큰 덩어리는
`david`·`joseph`·`moses` 의 `ruleset: legacy-baseline` 25건씩으로, 신규 인물 규약을
소급 적용하지 않기로 한 **등재된 부채**다. 사정은
[`scripts/gates/README.md`](scripts/gates/README.md).

## 디렉토리

```
lemuel-xr/
├── README.md              ← 이 파일
├── docs/                  ← 기획·인물별 MVP 설계·안전/신학 규약 (50+ 문서)
├── backend/               ← Spring Boot 4.0 + Kotlin 2.2 (헥사고날, 100% Kotlin)
│   └── src/main/resources/scenarios/  ← 런타임 시나리오 yml (= Character enum 과 1:1)
├── frontend/              ← Next.js 16 + React 19 (App Router). React Native 아님
├── ai/, tts/              ← Python 사이드카 (WebClient proxy)
├── content/               ← 인물별 저작 산출물 (scene*.yml)
├── scripts/               ← 게이트 러너. `ci_gates.py` 가 전부를 돌린다
├── eval/                  ← 콘텐츠 평가
├── unity/                 ← **비어 있음** — Unity 6 셋업 안내만 있다
└── unity-stub/            ← API 클라이언트 스텁 5파일 (헤드셋 앱 아님)
```
