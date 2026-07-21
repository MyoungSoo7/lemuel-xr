# elijah — MVP XR 콘텐츠 (Unity 가 직접 로드) — 초안

> ⚠️ **초안 고지** — 이 콘텐츠는 ouroboros interview 도구가 이 세션에서 동작하지 않아 **수기(手記) 크리스탈라이즈된 seed 기반**의 ≤70% 초안이다. `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 에이전트의 **사후 검토 전 프로덕션 반영 금지**. 특히 `scene2.yml` 의 R1(자살사고 본문) 처리는 안전 검토자 최종 승인 게이트를 거쳐야 한다.

`docs/MVP-ELIJAH-CONTENT.md` 의 5개 Scene 을 *Scene yml + AssetManifest JSON* 으로 분해. 디바이스 무관 게임 로직(yml) + 디바이스별 자산(manifests/{device}/) 분리.

## 구원 카테고리 — 5번째: 정서적 소진(번아웃) 회복

기존 4 유형(요셉=경제 / 모세=정치 / 다윗=외세 / 예수=영적)에 없는 **번아웃 회복** 서사. PLAN.md Track B 표에 **Theme 12** 로 추가되는 성격. 상승(Scene 1) → 붕괴(Scene 2) → 돌봄(Scene 3) → 고요(Scene 4) → 다시 걸음(Scene 5) 의 곡선으로 *"성취 직후의 무너짐"* 을 서사 구조 자체로 체감시킨다.

## 디렉토리 구조

```
content/elijah/
├── README.md                              ← 본 문서
├── scene1.yml ~ scene5.yml                ← Unity ScriptableObject deserialize
└── manifests/                             ← (미생성 — 자산 빌드 후 추가)
    ├── quest3/scene{1..5}-v1.0.0.json     ← 기준 (PBR + LOD 3단 + 불 VFX)
    ├── visionpro/scene{1..5}-v1.0.0.json  ← PBR 업스케일 + eye tracking(세미한 소리 볼륨 연동)
    ├── galaxyxr/scene{1..5}-v1.0.0.json   ← LOD reduce
    └── web/scene{1..5}-v1.0.0.json        ← 360 image fallback + click/드래그
```

## Unity 가 어떻게 로드하는가

joseph/david 와 동일. `MissionLoader.LoadScene(missionId="elijah", sceneId="elijah_scene1")` → yml `SceneDefinition` + manifest `AssetManifest` deserialize. `SceneRunner` 가 `narration_blocks`/`interactions`/`branches`/`safety_gates` 를 timeline 실행. 분기는 `POST /api/game/elijah/scene/{n}/decide|complete` 로 전송. `next_scene_id: null` 이면 메인 복귀(또는 트랙 A 시편 42 bridge).

## Elijah 의 차별점 — 안전선이 콘텐츠보다 우선

- **scene2.yml (최고 민감)**: 자살사고 본문(왕상 19:4)을 다루므로 *결정 분기 없음*(무너짐은 선택 아님, R1), 진입 전 R4 동의 게이트 + 건너뛰기 대체 Scene(3) + 위기 자원 109, 본문 직후 3초 정적 후 즉시 Scene 3 전환(절망 고착 방지).
- **scene3.yml (게임의 심장)**: 하나님이 *책망 대신 떡·잠을 먼저* 주신 순서(왕상 19:5~7, R2). 질책·평가·요구 0건. `lint_forbidden_tokens` 로 책망 어휘 빌드 차단.
- **scene4.yml (XR 핵심)**: lemuel-xr 유일하게 *무동작(stillness)* 을 인터랙션으로 삼음(과각성 → 안정). 정직한 토로 3 카드(외로움/소진/두려움) — *어느 카드에도 "죽고 싶다" 없음*(Affect Labeling).
- **scene5.yml**: 재사명을 *"함께 걸을 엘리사"* 로(R3, 회복 압박 차단). 겉옷 던지기(사명 수락) 강제 아님.

## 폭력 배제

바알 선지자 처형(왕상 18:40)은 **완전 배제**. scene1.yml 은 *불의 응답까지만*. 천사·하나님은 실사 형상 배제(추상 광휘·소리로만).

## 백엔드 (asset_manifests) 와의 sync

joseph/david 와 동일 — manifest JSON 을 `asset_manifests.manifest` JSONB 에 INSERT. `scripts/sync_asset_manifests.py` 의 mission_id 만 `elijah` 로 호출.

## voice_id

- `elijah_v1` — 지친 중년 남성. Scene 1 승리 톤 / Scene 2 탈진 톤 대비.
- `messenger_v1` — 전령(이세벨 위협 전달). 냉정·짧게.
- `angel_v1` — 성별 중립·부드러움. 재촉이 아닌 다독임.
- `whisper_v1` — **세미한 소리**. 극저볼륨 + 근접감. *큰 소리가 아니라 가까운 소리*.

## 작성·검수 가이드

- yml 텍스트는 `lemuel-xr-theology-tone` + `lemuel-xr-mental-health-safety` 사전 가이드 적용(R1~R5, disputed_points 명시, *AI 보조 — 본문은 성경 참조* footer 영구, 위기 시 109).
- 출판 전 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` agent 사후 검수 **필수** (특히 scene2 R1).
- 엘리야 전용 확정 신학 서지(E1·E2) 미확보 — THEOLOGY-REFERENCES.md 드릴다운 필요.
- 가짜 URL·size 는 추정값. 실제 자산 빌드 후 R2 업로드 시 갱신.
