# jacob — MVP XR 콘텐츠 (Unity 가 직접 로드) — 초안

> ⚠️ **초안 고지** — 이 콘텐츠는 ouroboros interview 도구 대체로 **수기(手記) 크리스탈라이즈된 seed(seed-JACOB v5-b, 독립 채점 완료본) 기반**의 ≤70% 초안이다. `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 에이전트의 **사후 검토 + 인간 안전검토자 사인오프 전 프로덕션 반영 금지**. 특히 Scene 1·5 의 R4 동의 카드, Scene 2·4·5 의 R1 음성 리스너, 마무리 문구 10종(R3)은 안전 검토자 최종 승인 게이트를 거쳐야 한다.

`docs/MVP-JACOB.md` 의 5개 Scene 을 _Scene yml_ 로 구현. 디바이스 무관 게임 로직(yml) — manifest(자산) 는 아직 생성되지 않았다.

## 구원 카테고리 — 신규: 내가 가해자인 관계의 회복

기존 유형(요셉=경제 / 모세=정치 / 다윗=외세 / 엘리야=정서적 소진 회복 / 예수=영적)에 없는 **가해자 자리에서의 회복** 서사. PLAN.md Track B 표에 **Theme 18** 로 추가되는 성격.

**축 배타 할당** — "이미 저지른 뒤의 자기정죄에서 회복"은 **베드로(14)** 의 축(하나님과의 관계 회복)이고, "지연된 약속"은 **아브라함(17)** 의 축이다. 야곱의 축은 오직 **"내가 준 상처 앞에 다시 서는 것"**(사람과의 관계 회복, 용서를 비는 쪽)이다.

가해의 자리(Scene 1) → 목격(Scene 2) → 두려운 결단(Scene 3) → 정체성 대면(Scene 4) → 비대칭 회복(Scene 5) 의 선형 곡선. 상승-하강이 아니라 *"자리에 다시 서는 것 자체가 완결"*을 서사 구조로 체감시킨다.

## 디렉토리 구조

```
content/jacob/
├── README.md                              ← 본 문서
├── scene1.yml ~ scene5.yml                ← Unity ScriptableObject deserialize
└── manifests/                             ← (미생성 — 자산 빌드 후 추가)
    ├── quest3/scene{1..5}-v1.0.0.json     ← 기준
    ├── visionpro/scene{1..5}-v1.0.0.json  ← PBR 업스케일
    ├── galaxyxr/scene{1..5}-v1.0.0.json   ← LOD reduce
    └── web/scene{1..5}-v1.0.0.json        ← 360 image fallback + click/드래그
```

## Unity 가 어떻게 로드하는가

기존 인물과 동일. `MissionLoader.LoadScene(missionId="jacob", sceneId="jacob_scene1")` → yml `SceneDefinition` deserialize. `SceneRunner` 가 `narration_blocks`/`interactions`/`branches`/`safety_gates`/`alt_routes` 를 timeline 실행. 분기는 `POST /api/game/jacob/scene/3/decide` (Scene 3 유일) 또는 `POST /api/game/jacob/complete`(Scene 5 자동 라우팅) 로 전송. `next_scene_id: null` 이면 메인 복귀(또는 트랙 A 시편 32편 bridge).

**`alt_routes[]` — 신규 키(elijah/solomon 은 미사용)**: Scene 1 의 `1_alt`, Scene 5 의 `5_alt` 는 별도 파일이 아니라 각 Scene 파일 내부의 라우트다(`scene1_alt.yml` 같은 6번째 파일을 만들면 안 된다 — Scene 파일 수 검증이 깨진다). 동의 카드에서 "연출 없이 요약으로 본다"를 선택하면 이 라우트로 진입한다.

## Jacob 의 차별점 — 가해자 자리 고지 + 보상 비대칭 금지

- **scene1.yml (진입 게이트)**: `cc_jacob_family_deception` 동의 카드 — `role_disclosure_ko` 로 사용자가 **가해자 자리**에 선다는 것을 명시 고지(리터럴 키, 산문 서술이 아님). 3택: 들어간다 / 요약으로만 본다(`1_alt`) / **`cc_jacob_not_my_seat` 이탈로**(피해자 쪽 사용자용, 5 Scene 전부 상시 이용 가능).
- **scene2.yml (목격, R1)**: `interactions: []` — "남의 고통은 선택지가 아니다." 27:34 절규는 `dwell_limit: 3초`로 고착 방지, 27:41(살해 의도)은 인용 자막으로만, 위협 연출 완전 배제. `crisis_reminder` 리터럴 배치(2026-08-05 이전 표기 `R1_crisis_reminder` — 문구 변경 없이 이름만 다른 인물·런타임과 통일).
- **scene3.yml (유일한 사용자 결정)**: `return_label` 3택(`send_ahead`/`go_afraid`/`stay_and_pray`) — 전부 정당, 서열화 금지. `stay_and_pray` 는 **종결 상태**(유예 아님) — "아직"·"언젠가" 등 어휘 전면 금지. `carry_to_scene5` 로 값이 그대로 전달된다.
- **scene4.yml (XR 핵심 #1)**: 비버튼 지속 그립(`ix_s4_hold_and_wrestle`) — 놓아도 실패가 아니라 재시작, 횟수 카운트 없음. 정체성 응답은 사용자 실명을 묻지 않고 "야곱" 고정. 씨름 상대는 형상·성별 미렌더(disputed #1 신현 논쟁 회피).
- **scene5.yml (XR 핵심 #2 + R3 최우선)**: `cc_jacob_confrontation` 동의 카드에 `r3_reunion_safety_notice_ko`(재회 위험 고지, 고정 키) 리터럴. 예물 인터랙션(`ix_jacob_gift`) 두 경로(`rt_offer_again`/`rt_withdraw`) 가 **완전히 동등**(`entry_mode_after: full` 통일, `parity_assert`) — 물러서는 선택이 콘텐츠로 벌받지 않는다. 마무리는 `br_s5_closing_message` 자동 라우팅(`return_label` × `faith_tone` = 9종 + null 폴백 1종, 전부 동일한 해제 문장으로 종료).

## 배제 — 전면 금지 구간

창 29:21-25(신부 바꿔치기) / 창 34 전체(디나, 성폭력+학살) / 창 35:22(르우벤) / 창 38 전체(유다·다말) — 전부 완전 배제. 배제 문자열 목록·커버리지 확인은 `docs/MVP-JACOB.md` §0.2 참조. **`content/jacob/*.yml` 어디에도 배제 문자열을 쓰지 않는다**(YAML 주석 포함).

## 백엔드 (asset_manifests) 와의 sync

기존 인물과 동일 — manifest JSON 을 `asset_manifests.manifest` JSONB 에 INSERT. `scripts/sync_asset_manifests.py` 의 mission_id 만 `jacob` 으로 호출. (manifests 미생성 — 자산 빌드 후 진행)

## voice_id

- `jacob_v1` — 사용자 1인칭 내면. 두려움이 기본 톤, 교활함 연기 금지.
- `esau_v1` — 굵고 따뜻함. **절대 악역 연기 금지** — 27:34 절규와 33:4 포옹이 같은 목소리다.
- `isaac_v1` — 노쇠. 27:35 대사만.
- `wrestler_v1` — 형상·성별 불명. 음성만(신현 논쟁 disputed #1 회피).
- `narrator_v1` — 본문 인용 내레이션.

## 작성·검수 가이드

- yml 텍스트는 `lemuel-xr-theology-tone` + `lemuel-xr-mental-health-safety` 사전 가이드 적용(R1~R5, disputed_points 명시, _AI 보조 — 본문은 성경 참조_ footer 영구, 위기 시 `{{crisis_resources.default}}` — **하드코딩 번호 절대 금지**).
- 출판 전 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` agent 사후 검수 **필수**(특히 scene5 의 R3 재회 위험 고지, 마무리 문구 10종).
- 야곱 전용 확정 신학 서지 미확보 — `docs/THEOLOGY-REFERENCES.md:34 [J2]` 우선 인용 후 KCI 드릴다운 필요(MVP-JACOB.md §12.7 참조).
- 가짜 URL·size 는 추정값. 실제 자산 빌드 후 업로드 시 갱신.
- **공유 파일(다른 인물·backend·PLAN.md·루트 README)은 이 디렉토리 밖에서 별도 PR로 처리한다** — 이 디렉토리의 초안 작업은 공유 파일을 변경하지 않는다.
