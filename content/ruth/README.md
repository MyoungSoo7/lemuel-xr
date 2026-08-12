# content/ruth — 룻 미션 Scene 정의 (Theme 19 · 무자격자가 속하게 되는 것)

> ⚠️ **초안 고지** — 동결 seed(rev.6.4, 1415줄) 전문 기반 초안이다. 완성도 ≤70%.
> **`lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필요** — 특히 Scene 2(탄식층·사별 정면)·Scene 4(성적 취약 정황, 등급 C)는 인간 사인오프 필수.
> 검토 게이트 기록: `docs/CONTENT-EVALUATION-GATES.md` 통과 기록 필요.
> **게이트 27종 중 G5b·G5c·G5e 는 통과하지 못한다** — 백엔드 안전 파일 2종(`application.yml` 토큰 목록 · `ForbiddenTokenConfigTest.kt`)이 이 PR 범위 밖(기존 파일 편집 금지)이기 때문이다. 초록이 아닌 것을 초록으로 읽지 마라.
> ↳ **2026-08-05 해소.** 별건 편집으로 백엔드 2종을 반영해 현재 `PASS 27 / FAIL 0 / BLOCKED 0`, `gradle test` 7/0 이다. 위 문장은 이 PR 시점의 기록으로 남긴다.
> **§8-1 AC 14건의 실행기(`scripts/check_ruth_captions.py`·`scripts/check_ruth_biometrics.py`)가 아직 없다.** 해당 AC 는 구조상 만족하도록 저작했을 뿐 기계로 재지 않았다.
> ↳ **2026-08-05 해소.** 실행기 2종을 신설했고(자막 12검사 / 생체 1검사) **뮤테이션 20건**으로 오검출 아님을 확인했다(SEED §16-n). 다만 **AC 16 은 여전히 공허한 초록**이다 — §8-1 인라인 명령이 `subtitles` 키를 보는데 실 키는 `captions` 다. 같은 대상을 AC 14 가 `captions` 로 실측하므로 실질 구멍은 없고, 키 이름은 라합 seed 에서 고친다.

## 파일

| 파일         | Scene                            | 본문                | 민감도                                                                                               |
| ------------ | -------------------------------- | ------------------- | ---------------------------------------------------------------------------------------------------- |
| `scene1.yml` | 갈림길 — 두 며느리와 한 사람     | 룻 1:8, 1:9, 1:16   | **mid** — 진입 동의 카드 1(`consent_covers_scenes: [1, 2]`) + Scene 3 직행 skip + 브리지 나레이션    |
| `scene2.yml` | 빈 손의 귀향 — 탄식층            | 룻 1:19, 1:20, 1:21 | **mid** — 카드 1 로 커버(별도 카드 없음). ★ 핵심 인터랙션 ①(애도의 거리) · 미션 유일 분기            |
| `scene3.yml` | 이방 여인 ★ 축의 중심            | 룻 2:2, 2:10-2:12   | **mid** — 동의 카드 2(`[3, 5]`) + Scene 4 직행 skip. ★ 핵심 인터랙션 ②(이삭 줍기)                    |
| `scene4.yml` | 타작 마당의 밤                   | 룻 3:9, 3:11        | **등급 C · high** — 동의 카드 3(재확인) + Scene 5 직행 skip + 헌장 §2.1-b 다섯 항 전건. `branches` 0 |
| `scene5.yml` | 성문에서 — 이탈 지점 + 종결 화면 | 룻 4:11, 4:12       | **mid** — 카드 2 로 커버. 마감 세 줄 + `closing_screen` 블록(SR-1 종결 자막)                         |

본 미션은 성문에서의 축복까지 렌더한다. 오벳의 출생은 **사실로 존재하되 렌더하지 않는다** — 출생 자막 0건 · 아기 에셋 0건 · 나레이션 0건. 이유는 신학이 아니라 매체 판단이다: 난임·자녀 상실 이력 사용자에게 **출산이 회복의 증거**가 되는 순간 이 미션은 실패한다(헌장 §3-c · seed DP3·DP5).

## 스키마 정본

`content/peter/scene*.yml` · `content/daniel/scene*.yml` 컨벤션 미러 — `consent_card_id` / `consent_cards[]` / `crisis_reminder` / `lint_forbidden_tokens`(`safety_gates[]` 내부 중첩, **최상위 금지**) / `R1_voice_self_harm_listener`(action: `pause_fade_black_show_crisis_card`, enforced_at: always, 발화 원문 미저장) 구조 그대로.

⚠️ **`enforcement: structural` 을 쓰지 않는다.** 이 리포에서 그 키는 "강하게 집행한다"가 아니라 "토큰 목록 대신 구조 단언으로 집행한다"는 뜻이고, `structural_check` 가 비면 그 자리에서 FAIL 이다. 룻은 토큰으로 집행한다.

룻 미션 고유 확장 키(top-level, 문서 루트):

- `exposure_grade` / `trigger_categories` / `staging_constraints` — 헌장 3키. **5개 Scene 전건**에 있다(§2.1 · §2.1-a · §2.1-b).
- `post_crisis_render_policy` / `post_crisis_latch_scope` / `crisis_card_position` — SR-3 latch 3키. 5개 Scene 전건에서 **값까지 동일**하다. 키 존재가 아니라 값이 계약이다.
- `consent_card_defined_in`(Scene 2·5) — 카드를 정의하지 않고 참조만 하는 Scene 이 어느 파일의 카드에 덮이는지 밝힌다.
- `conditional_blocks[]`(Scene 5) — 문자열 `skip_alternative_scene_id` 의 착지 블록. 정수 자기참조를 쓰지 않는 이유는 아래 표 참조.
- `closing_screen`(Scene 5 최상위) — 종결 화면. **6번째 파일을 만들지 않는다**(아래 별도 절).
- `F66_entry_state_gate`(Scene 1 `safety_gates[]`) — 취약 상태 이력 사용자에 대한 **톤 자동 완화**. 임상 판정이 아니다.
- `belonging_label`(Scene 2 → Scene 5) — 미션 유일 분기 키. 서사·자막·에셋은 갈라지지 않고 **마감 한 줄만** 갈린다.

## 종결 화면을 별도 파일로 만들지 않은 이유

`newchar_gates.py` 의 Scene 수집기는 `glob("scene*.yml")` 이라 `closing.yml` 같은 이름을 **아예 보지 않는다.** `content/*/` 에 비-Scene yml 은 리포 전체에서 0건이고, `content/esther/scene1.yml` 이 6번째 파일 금지를 명시해 두었다. 그래서 종결 화면을 `scene5.yml` 최상위 `closing_screen:` 블록으로 싣는다.

실집행 키는 야곱 선례를 따른다 — `content/jacob/scene5.yml` 의 `entry_mode: closing_only` · `environment_disabled: true` · `npc_disabled: true` · `closing_route_ref`. **주석이 아니라 키다.** 안전층(면책 4줄 · `crisis_reminder`)은 `closing_screen.ui_overlays[]` 아래에 둔다 — Scene 과 달리 부모가 `environment` 가 **아니다.** `closing_only` 진입에서 `environment` 는 렌더되지 않으므로, 안전층을 그 아래 두면 렌더 안 되는 블록에 안전장치를 매다는 조용한 실패가 된다.

**안전층 부착 지점은 서로 다른 6개 블록**이다 — `scene1~5` 각 파일의 `environment.ui_overlays` 5건 + `scene5.yml` 의 `closing_screen.ui_overlays` 1건. Scene 5 를 두 번 세지 않는다. 계수 기준은 파일이 아니라 **블록 경로**다.

## 안전 제약축 (R1~R5 요약)

- **R1** — 음성 자해 감지 리스너 `R1_voice_self_harm_listener` 5/5 Scene 상시(발화 원문 미저장). 발동 시 latch 는 **미션 범위**이며, latch 이후에는 서사 자막이 전부 억제되고 종결 자막도 렌더되지 않는다. 묵상 제안(opt-in)도 제시하지 않는다.
  - ~~⚠️ **부채** — 사별 특이형 동반사망 표현이 `docs/EMOTION-CLASSIFIER.md` 3단 사전 어디에도 없다.~~ **2026-08-06 해소** — 4종이 `docs/EMOTION-CLASSIFIER.md` §3 사전과 `application.yml` 런타임 regex 양쪽에 들어갔고, 둘이 어긋나면 `CrisisKeywordDocContractTest` 가 양방향으로 깨진다. `scripts/gates/ruth.yml` 의 `release_blockers` 는 `resolved: true` · `resolved_at: 2026-08-06` 이다. (이 줄은 2026-08-11 까지 `resolved: false` · 「출시를 막는 항목」이라고 적혀 있었다 — **닷새간 낡은 상태였다.**)
    - ⚠️ **다만 완전 해소는 아니다.** `마지막으로 정리` 는 일상어 오탐이 너무 흔해 **의도적으로 재현율을 포기했다** — 그 표현만 쓰는 사용자는 medium 에 걸리지 않는다. 실패 방향이 오탐에서 미탐으로 바뀐 것이지 없어진 것이 아니다(`scripts/gates/ruth.yml` `release_blockers[0].caveat`).
- **R2** — 고난을 신의 징벌·연단으로 귀속하는 문장 0건. Scene 2 는 나오미의 탄식을 **서사 내에서 반박하지도 교정하지도 않고**, 대신 화자 표기를 상시 노출해 사용자 자신의 문장으로 읽히지 않게 한다(SR-8). 비서사 안전층(면책 오버레이)은 예외이며 필수다.
- **R3** — 회복 압박 금지. 마감 세 줄에는 `faith_tone` 3단을 적용하지 않는다 — 3줄이 그대로 있는 채 9줄로 불어나는 것이 정확히 이 미션이 피하는 형태다. 인터랙션 어디에도 점수·판정·타이머가 없고, 미선택은 실패가 아니라 `null` 이다.
- **R4** — 동의 카드 **3장** 2단 구조. 카드1(진입, 사별) → 카드2(중간, 이방·빈곤) → 카드3(Scene 4 직전 재확인). 스킵 목적지는 `consent_card_id` → `skip_alternative_scene_id` → (문자열이면) `conditional_blocks[].id` 의 **쌍**으로 계약한다.

  | Scene | `skip_alternative_scene_id` | 타입 | 착지                             |
  | ----- | --------------------------- | ---- | -------------------------------- |
  | 1     | `3`                         | int  | `scene3.yml`                     |
  | 3     | `4`                         | int  | `scene4.yml`                     |
  | 5     | `ruth_scene5_alt_short`     | str  | 같은 파일 `conditional_blocks[]` |

  ⚠️ **정수 자기참조를 쓰지 않는다.** `skip_alternative_scene_id: 5` 는 타입 검사를 통과하면서 축약 블록을 지목하지 못해 **도달 불가**로 만든다 — 검사는 초록, 사용자는 전문(全文)을 본다. 이 형태가 seed 재검토에서 실제로 한 번 발생했다.

- **R5** — 5개 Scene 전건 `default_path: static_curation` + `llm_optin_only: true`. 마감 세 줄·저널 문구 전부 사전 저작 정적 텍스트다. **LLM 없이 완주 가능하며, 그것이 기본 경로다.** LLM 은 종결 후 묵상 제안에서 opt-in 시에만 호출된다.
- **crisis 자원** — 하드코딩 금지, `{{crisis_resources.default}}` 토큰만. 본 디렉터리 yml 내 실측 **17곳**: Scene 1 3(R1 카드 문안 1 · `crisis_reminder` 1 · footer 1 · 면책 1 = 4) 등 Scene 별 3~4곳 + `closing_screen` 2곳.

## 트리거 · 노출 등급 (헌장 §2.1 · §2.1-a)

| Scene | `exposure_grade` | `trigger_categories`                                                   |
| ----- | ---------------- | ---------------------------------------------------------------------- |
| 1     | B                | `bereavement_spouse` · `bereavement_child`                             |
| 2     | B                | `bereavement_spouse` · `bereavement_child` · `poverty_food_insecurity` |
| 3     | B                | `bereavement_spouse` · `ethnic_labeling` · `poverty_food_insecurity`   |
| 4     | **C**            | `sexual_vulnerability_context`                                         |
| 5     | B                | `pregnancy_childbirth`                                                 |

트리거 6종. `scripts/gates/ruth.yml` 의 `trigger_scenes` 는 `[1, 2, 3, 4, 5]` 이며, 이는 위 표의 Scene 집합과 **양방향으로 같아야 한다**(§2.1-a 불변식). 한쪽만 고치면 기본거부(default-deny)가 조용히 무너진다.

Scene 4 는 등급 C 이므로 화자값이 `scripture_caption` 이고, 성경 본문 자막만 띄운다 — 각색·부연·1인칭 재연이 없다. 헌장 §2.1-b 다섯 항(시점 위치 / 눕힘 금지 / 근접 공간 오디오 금지 / 신체 프레이밍 금지 / 어둠은 조명 지시로만)은 **동의 카드로 해제되지 않는다.**

## belonging_label — 미션 유일 분기 (Scene 2 → Scene 5)

| 값            | Scene 2 선택지   | Scene 5 마감 줄                          |
| ------------- | ---------------- | ---------------------------------------- |
| `stay_beside` | 곁에 남는다      | "곁에 남은 자리에도 그늘이 닿았다."      |
| `step_back`   | 한 걸음 물러선다 | "한 걸음 물러선 자리에도 그늘이 닿았다." |
| `null`        | 미선택으로 진행  | "이 자리에도 그늘이 닿았다."             |

**집합 크기는 정확히 3이다.** 세 줄은 주어가 신 또는 장소이고 같은 술어를 쓴다 — 사용자의 선택을 평가하는 어휘가 한 개도 없다. 죄의 선택이 아니라 **애도의 거리**이며, 둘 다 정답이고 서사는 갈라지지 않는다.

취약 상태 이력 사용자에게는 `F66_entry_state_gate` 가 마감 줄을 `null` 변이로 강제한다 — 자기 선택이 자기에게 되읽히지 않게 하는 장치다.

## 분기를 두지 않은 Scene 과 그 이유

Scene 1·3·4 는 `branches: []` 다. 비어 있는 것이 아니라 **의도**다. 인간의 죄·무자격·성적 취약 정황이 서술되는 자리에 사용자 선택지를 두지 않는다(`docs/SERIES-GRACE.md` §2.0). 죄는 선언하고 지나가며, 사용자가 그것을 _고르게_ 만들지 않는다. 베드로 Scene 3 이 같은 판례다.

주연은 삼위일체 하나님이지 등장하는 여인들이 아니다. 이 미션의 축은 "무자격자가 속하게 되는 것"이고, 속하게 하는 주체는 사용자의 결단이 아니다.

## 관련 문서

- 설계: `docs/MVP-RUTH.md`
- 원문 정본: `docs/VERSES-RUTH-GAE.md` (대한성서공회 개역개정 실측) · 대조표: `docs/verses-ruth.txt`
- 상위 규약: `docs/SERIES-GRACE.md` (§2.0 · §2.1 · §2.1-a · §2.1-b · §3-a · §3-b · §3-c · §5-c · §5-g)
- 게이트 설정: `scripts/gates/ruth.yml`
- 신학 참고문헌: `docs/THEOLOGY-REFERENCES.md` **§11 룻**(별건 편집으로 신설, 2026-08-05) — R1~~R7 · 시리즈 공통은 §10 G1~~G7. ⚠️ **전건 본문 fetch 미완(서지·초록 수준)** 이므로 인용 시 그 등급을 그대로 옮겨 적는다

_AI 보조 — 본문은 성경 참조 — storyteller 역할. 위기 시 {{crisis_resources.default}}._
