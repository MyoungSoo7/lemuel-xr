# lemuel-xr

> **성경 기반 감정 회복 + 서사 게임 플랫폼** — AI 전방위 활용

---

## 한 줄 컨셉

성경의 인물·지혜를 매개로 **감정 회복(passive)** 과 **자기 서사화(active)** 를 양방향 제공하는 AI 기반 영적 웰빙 앱.

## 문서

| 파일 | 내용 |
|---|---|
| [`docs/PLAN.md`](docs/PLAN.md) | 전체 기획서 — 컨셉·아키텍처·경쟁분석·로드맵 |
| [`docs/BUILD-PLAN.md`](docs/BUILD-PLAN.md) | XR 빌드 플랜 — 시스템 아키텍처·sprint plan·위험·비용 |
| [`docs/MVP-JOSEPH.md`](docs/MVP-JOSEPH.md) | **MVP 요셉 XR 미션 상세 설계** (2026-05-20 결정) |

## MVP 결정 (2026-05-20)

기존 PLAN.md 의 V1.0(트랙 A 정적 콘텐츠) → V2.0(요셉 게임) 순서를 **뒤집어** 요셉 XR 게임을 **V1.0 MVP** 로 시작.

이유: XR 의 *손·공간* 메타포가 곡식 분배 결정 인터랙션과 가장 잘 맞고, 단일 미션이 6주 안에 완성 가능. 트랙 A 정적 콘텐츠는 Phase 2 로 이동.

자세한 시나리오·인터랙션·일정은 [`docs/MVP-JOSEPH.md`](docs/MVP-JOSEPH.md) 참조.

## 듀얼 트랙 구조

```
사용자 진입
    │
    ▼
감정/의도 입력
    │
    ├──[감정/위로]──► 트랙 A: 정적 회복 콘텐츠 (1~7)
    │                일기·잠언·시편·고통·마음 등
    │
    └──[탐색/몰입]──► 트랙 B: 서사 게임 (8~11)
                     요셉·모세·다윗·예수
```

## 인물별 담당

| Theme | 인물 | 구원 유형 | 담당 |
|---|---|---|---|
| 8 | 요셉 | 경제 | [@MyoungSoo7](https://github.com/MyoungSoo7) |
| 9 | 모세 | 정치 | [@MyoungSoo7](https://github.com/MyoungSoo7) |
| 10 | 다윗 | 외세 | [@MyoungSoo7](https://github.com/MyoungSoo7) |
| 11 | 예수 | 영적 | [@godjinho](https://github.com/godjinho) |

각 담당자가 *설계 문서·시나리오·신학 검토* 1차 책임. 코드 구현은 별도 분담.

## 핵심 차별화

- **개신교 + 한국 시장** — Hallow(가톨릭)·Pray.com(미국) 빈자리
- **게임 듀얼 트랙** — 정적 명상 앱들 (Calm/Headspace/Abide) 없는 능동 몰입
- **AI 전방위** — 묵상 생성·서사 분기·대화·TTS·이미지·번역

## 현재 단계

📌 **Phase 0 — 시장 검증** (계획)
- 타겟 인터뷰 5명
- 신학 자문 영입
- Figma wireframe

자세한 다음 액션은 [PLAN.md §8](docs/PLAN.md#8-다음-액션-시간-순) 참조.

## 디렉토리 (예정)

```
lemuel-xr/
├── README.md              ← 이 파일
├── docs/
│   ├── PLAN.md            ← 전체 기획서
│   ├── interviews/        ← 인터뷰 노트 (예정)
│   ├── wireframes/        ← Figma 링크/스크린샷 (예정)
│   └── theology-review/   ← 신학 자문 피드백 (예정)
├── backend/               ← Spring Boot 4 + Kotlin (헥사고날, 100% Kotlin)
├── ai/, tts/              ← Python 사이드카 (WebClient proxy)
├── game/                  ← Unity (예정, V2.0)
└── frontend/              ← React Native (예정)
```
