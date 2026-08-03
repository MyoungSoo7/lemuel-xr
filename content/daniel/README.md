# daniel — MVP XR 콘텐츠 (Unity 가 직접 로드) — 초안

> ⚠️ **초안 고지** — 이 콘텐츠는 `seed-DANIEL.md`(v4) 기반의 수기(手記) 크리스탈라이즈 ≤70% 초안이다. `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 에이전트의 **사후 검토 + 인간 안전검토자 사인오프 전 프로덕션 반영 금지**. 특히 `scene4.yml`·`scene5.yml`(R4 이중 트리거)의 동의 게이트 처리는 안전 검토자 최종 승인 게이트를 거쳐야 한다.

`docs/MVP-DANIEL.md` 의 5개 Scene 을 _Scene yml_ 로 분해. 디바이스 무관 게임 로직(yml) + 디바이스별 자산(manifests/{device}/, 아직 미생성) 분리.

## 구원 카테고리 — 신규: 동화 압력 속 정체성 보전

다수 문화가 "그냥 맞춰 살라"고 요구할 때, 신앙 정체성을 유지하면서도 그 사회 안에서 유능하게 기능하는 법. 이민자·소수자·직장 내 신앙 은폐 압력을 겪는 사용자가 대상. 이 미션의 축은 **경계 설정**이다 — 어디까지 협력하고 어디서 멈추는가. 이름을 잃다(Scene 1) → 뜻을 정하다(Scene 2, 유일한 사용자 선택) → 열흘(Scene 3) → 금령(Scene 4, R4 ①) → 창문→사자굴→보전(Scene 5, R4 ② + 12종 마무리 문구)의 곡선.

## 디렉토리 구조

```
content/daniel/
├── README.md                              ← 본 문서
├── scene1.yml ~ scene5.yml                ← Unity ScriptableObject deserialize
└── manifests/                             ← (미생성 — 자산 빌드 후 추가)
    ├── quest3/scene{1..5}-v1.0.0.json     ← 기준
    ├── visionpro/scene{1..5}-v1.0.0.json
    ├── galaxyxr/scene{1..5}-v1.0.0.json
    └── web/scene{1..5}-v1.0.0.json        ← 360 image fallback + click/드래그
```

## Unity 가 어떻게 로드하는가

기존 인물과 동일. `MissionLoader.LoadScene(missionId="daniel", sceneId="daniel_scene1")` → yml `SceneDefinition` deserialize. `SceneRunner` 가 `narration_blocks`/`interactions`/`branches`/`safety_gates`/`conditional_blocks` 를 timeline 실행. Scene 2 의 유일한 선택은 `POST /api/game/daniel/decision` 로 전송(`request_style`). Scene 4·5 의 동의 응답은 각각 `POST /api/game/daniel/scene/{4,5}/consent` 로 독립 전송 — **한 Scene 의 동의가 다른 Scene 의 동의를 대신하지 않는다.** `next_scene_id: null`(scene5)이면 미션 종료.

## Daniel 의 차별점 — R4 트리거가 2개, 독립 동의

- **scene4.yml (R4 트리거 ①)**: 종교적 박해·처형 위협의 **고지**(단 6:7). 진입 전 동의 카드 + 거절 시 25초 요약 대체 경로(`daniel_scene4_alt_decree_brief`). `crisis_reminder` 필수.
- **scene5.yml (R4 트리거 ② · 최종 Scene)**: 위협의 **실행**(단 6:16-17 투척·봉인) + 보전(6:22-23). Scene 4 의 동의를 상속하지 않고 별도로 다시 묻는다. 거절 시 사자굴 에셋(`model_lion`·`env_den_interior` 포함 5종) 전면 미로드(`daniel_scene5_alt_quiet_window`), 창문 정렬 인터랙션은 유지. `dwell_limit_seconds: 3` 으로 6:16-17 체류를 제한하고 즉시 6:22 로 전환. `crisis_reminder` 필수(G3c).
- **scene2.yml (유일한 선택)**: `request_style` 3 카드(direct_refusal/verifiable_proposal/seek_ally) — 서사 전개를 바꾸지 않고 Scene 5 마무리 문구 라우팅에만 반영. `R3_all_request_styles_valid` — 셋 다 정당한 반응(틀린 카드 없음).
- **scene3.yml**: `R3_no_achievement_framing` — "버텨냈다"류 성취 프레이밍 금지, 1:17 을 인간 의지가 아니라 하나님이 주신 것으로 서술.

## 폭력 배제

단 6:24 및 6:25-28(아동을 포함한 집단 처형 서사)은 **완전 배제**. `content/daniel/*.yml` 5개 파일 전부 최상위 `lint_forbidden_tokens`(36종, R2/R3/에스더-배타 어휘 차단)를 갖는다. scene5.yml 은 6:23(미션 종료점)에서 끝난다.

## 백엔드 (asset_manifests) 와의 sync

기존 인물과 동일 — manifest JSON 을 `asset_manifests.manifest` JSONB 에 INSERT 예정(아직 manifests/ 미생성). `scripts/sync_asset_manifests.py` 의 mission_id 만 `daniel` 로 호출.

## voice_id

- `daniel_v1` — 20대 초반, 절제된 확신(비장하지 않게).
- `official_v1` — 환관장·감독관 공용. 관료적 조심스러움 — 악역 톤 금지.
- `darius_v1` — 권위 뒤의 후회. 단 6:19-20 의 다급함.
- `narrator_v1` — 평서·저자극.

실사 인물 형상 배제 — `abstract_silhouette_low_detail`(환관장·감독관·다리오 왕 전원 적용, 엘리야 규칙 승계).

## 위기 자원 토큰 규칙

`{{crisis_resources.default}}` **토큰만** 사용한다. 번호(예: 자살예방상담전화, 정신건강 위기상담전화 등) 하드코딩은 이 인물의 모든 산출물에서 **0건**이어야 한다. `content/elijah/README.md`·`docs/MVP-ELIJAH.md` 의 기존 하드코딩은 **알려진 baseline 위반이며 미러링 대상에서 제외**한다 — 본 인물 산출물에서 그 패턴을 복제하지 않는다.

## 작성·검수 가이드

- yml 텍스트는 `lemuel-xr-theology-tone` + `lemuel-xr-mental-health-safety` 사전 가이드 적용(R1~R5, disputed_points 명시 — `docs/MVP-DANIEL.md` §12.6, _AI 보조 — 본문은 성경 참조 — storyteller 역할_ footer 영구).
- **정적 경로는 토큰 게이트 밖이다** — `default_path: static_curation` 이 기본 경로이므로, Scene 2 카드 3종·Scene 5 마무리 문구 12종·R1 위기 카드·R4 동의 카드 문구는 기계 게이트가 아니라 **사람 검토**가 유일한 방어선이다(`docs/MVP-DANIEL.md` §12.4 참조).
- 출판 전 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` agent 사후 검수 + **인간 안전검토자 사인오프** 필수(신규 구원 카테고리 도입).
- 다니엘 전용 확정 신학 서지 미확보 — `docs/MVP-DANIEL.md` §12.7 (`[D-1]`~`[D-3]` 후보, 본문 fetch 미완) 드릴다운 필요.
- 가짜 URL·size 는 추정값. 실제 자산 빌드 후 R2 업로드 시 갱신.
