# MVP-JOB — XR 실 콘텐츠 (Scene 별 대본·인터랙션·자원)

> **상위 문서**: `MVP-JOB.md` (구조·설계 — 5 Scene / 답 없는 고난과 비탄 허용 곡선)
> **본 문서**: 실제 사용자에게 노출되는 *대본·자산·인터랙션 트리거* — Unity 6 LTS + OpenXR 환경에서 구현 가능한 수준의 데이터.
> **타겟 디바이스**: Quest 3 (기준) / Quest Pro / Vision Pro / Galaxy XR — capabilities-min: 3DoF + spatial audio. hand tracking·haptic optional (욥 미션은 조작이 거의 없다).
> **세션 길이**: 6~7분. *침묵(Scene 1) → 외침(Scene 2) → 거짓 위로 인식(Scene 3) → 임재(Scene 4) → 답 없는 만남(Scene 5)*.
> **포지셔닝**: *영적 비상 대비 교육 콘텐츠* (큐티 + 비탄 정당화 metaphor). *임상/의료 도구 아님*. Scene 2(출생 저주·죽음 소원, **mid**) 안전 게이트가 콘텐츠보다 우선.
> **신학·안전 톤**: `lemuel-xr-theology-tone` 사전 가이드 적용 — disputed_points 명시, R1(위기 자원 `{{crisis_resources.default}}`) 최우선 + R2~R5 잔류. *AI 보조 — 본문은 성경 참조 — storyteller 역할* footer 자동.
> **근거 인용**: `THEOLOGY-REFERENCES.md` 에 **욥 전용 확정 서지 미확보(J1~J5 후보 드릴다운 필요)**. 본 문서는 확정 서지를 주장하지 않는다.

> ⚠️ **이 문서의 출처 — 지어낸 대본이 아니다.**
> 사용자 노출 문장은 전부 `backend/src/main/resources/scenarios/job.yml` 의
> `static_text` · `options[].response` · `reflection_prompt` · `value_prompt` ·
> `suffering_footer` · `crisis_reminder` **원문**이다. 이미 런타임으로 나가고 있는 것을
> 사람이 읽을 수 있게 정리한 문서다. XR 무대(환경·NPC·인터랙션·전환)만 새로 쓴 것이고,
> 그것은 `content/job/scene*.yml` 에 기계 판독 가능한 형태로 들어 있다.
> **정본 우선순위: `scenarios/job.yml`(대본) > `content/job/*.yml`(무대) > 이 문서(설명).**

> ⚠️ **초안 고지** — `lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필수.
> Scene 2 의 R4 mid 처리는 안전 검토자 최종 승인(**인간 사인오프 권고**) 전 프로덕션 반영 금지.
> 게이트 기록: `docs/CONTENT-EVALUATION-GATES.md` 1~2단.

---

## 0. 공통 XR 컨벤션 (Scene 전체에 적용)

### 0.1 진입·동의·안전 게이트 (Pre-Scene 0)

Scene 1 진입 *전* 반드시 노출. 건너뛰기 불가.

```
┌─────────────────────────────────────────────────────────┐
│  재 위에 앉은 사람 — 욥의 미션                             │
│                                                          │
│  · 길이: 약 6~7분                                        │
│  · 내용: 모든 것을 잃은 사람 곁의 침묵 · 태어난 날을        │
│    저주하는 외침 · 친구들의 위로 · 폭풍 가운데의 응답 ·     │
│    답을 얻지 못한 채 끝나는 만남                           │
│  · 죽음을 갈망하는 표현(욥 3장)이 포함된 장면이 있습니다.    │
│    그 장면 앞에서 다시 안내하고, 건너뛸 수 있습니다.        │
│  · 이 미션은 *답을 주지 않고* 끝납니다. 그것이 설계입니다.   │
│                                                          │
│  [지금 시작]  [건너뛰기 — 다른 미션 보기]                    │
│  ─────────────────────────────────────────────────────   │
│  지금 많이 지치거나 힘드시면, 게임보다 먼저 함께할 자원이     │
│  있어요.  위기 시: {{crisis_resources.default}}            │
│  (메뉴 → "안전 자원" 에서 언제든 다시 열 수 있어요)          │
└─────────────────────────────────────────────────────────┘
```

→ R4 충족(`content/job/scene1.yml :: R4_pre_scene0_consent_required`).
crisis 번호는 **하드코딩 금지** — `{{crisis_resources.default}}` 토큰을 catalog 에서
locale·연령 맞춤 렌더. 해석 주체는 `ScenePayloadAssembler → CrisisTokenResolver` 이고,
`ScenarioHotlineRatchetTest` 가 리터럴 번호 유입을 막는다.

### 0.2 인터랙션 추상화

욥 미션은 **조작이 거의 없다.** 이건 자산 부족이 아니라 설계다 — 이 미션에서 사용자가
해야 할 일은 *하는 것* 이 아니라 *있는 것* 이다.

| 인터랙션 의도 | Quest 3 (기준) | Vision Pro | Galaxy XR | Web 폴백 |
|---|---|---|---|---|
| 함께 앉아 있기 (Scene 1) | 조작 없음 — 체류 | 조작 없음 | 조작 없음 | 대기 (진행 바 없음) |
| 떠올리기 (Scene 2) | 조작 없음 — 입력 안 받음 | 동일 | 동일 | 동일 (입력창 없음) |
| 인식 카드 3택 (Scene 3) | ray + trigger / 손 터치 | gaze + pinch | ray + trigger | click |
| 올려다보기 (Scene 4) | head look | head look | head look | 스크롤 |
| 감정 키 선택 (Scene 5) | ray + trigger | gaze + pinch | ray + trigger | click |
| 다음 진행 | A button / pinch | pinch | A button | click "다음" |
| 메뉴/안전 자원 | menu button | 손바닥 위로 | menu | top-right ⋯ |

→ Scene 3·5 의 선택은 **전부 비강제**다. 40초(Scene 3) / 30초(Scene 5) 후 자동 진행하고,
미선택 경로가 별도 라우트로 존재한다(`when: {<key>: null}`).

### 0.3 시간·공간 안락성

- 카메라 이동 없음 — 전 Scene 정지 시점(`camera_locomotion: disabled`). 모션시크 리스크 최소.
- 환경 전환은 fade-to-black. Scene 4→5 만 fade-out 1.0초 / fade-in 1.4초로 길게 — 폭풍에서
  내려오는 시간.
- 사용자 시점 높이 `1.20m` — **앉은 눈높이**다. 전 Scene 동일. 서 있는 높이(1.65m)를 쓰면
  재 위에 앉은 사람을 *내려다보게* 된다.
- *3D 사운드 우선 + 텍스트 자막 동기* (청각 장애 / 무음 환경 대응).
- NPC 음성은 사전 캐시(오프라인·R5 opt-out 시에도 작동). voice_id:
  - `job_v1` — 욥. 절제된 저음. 울부짖음을 연기하지 않는다.
  - `friends_v1` — 세 친구. 톤은 악의가 아니라 **확신**이다. Scene 3 의 요점이 그것이다.
  - 나레이터·성경 자막 — 미션 공통 보이스.

### 0.4 결정 기록 (백엔드)

각 Scene 결정 → `POST /api/game/job/scene/{n}/decide` →
- `game_decisions` JSONB 기록
- `scene_views` entered/exited + `scene2_skipped`(동의 카드 skip 경로)
- `interaction_meta` 마이크로 신호: Scene 1 `time_before_skip_seconds`,
  Scene 3 `option_selected` · `time_to_select_seconds`, Scene 4 `upward_gaze_seconds`

**Scene 1·2·4 는 decide 없음** — 분기가 없다. Scene 3·5 만 분기 Scene 이며 둘 다
`default_path: static_curation` 이다.

### 0.5 항상 노출되는 footer

화면 좌하단(UI 안전 영역) 작은 글씨 *영구* 노출, 5/5 Scene:

```
AI 보조 — 본문은 성경 참조 — storyteller 역할 | 위기 시 {{crisis_resources.default}}
```

### 0.6 욥 미션 *전체* 톤 정책 (R2/R3/T1 사전 가드)

- **고난에 뜻을 붙이지 않는다.** 이 미션에서 그 말을 하는 것은 욥의 친구들이고, 본문은
  그들을 책망한다(욥 42:7). 나레이션·시스템 문구가 친구들의 편에 서지 않는다.
- **회복을 약속하지 않는다.** 욥 42:10 의 회복 단락을 결말로 쓰지 않는다. lint 로 막는
  표층형에는 "갑절로 회복"·"반드시 회복됩"·"믿음의 크기만큼" 류가 있다. <!-- lint_forbidden_tokens 인용 -->
- **비탄을 낭만화하지 않는다.** 고통을 미학으로 연출하지 않는다(카메라 워크·음악 상승 금지).
  단 이 항목은 **집행 수단이 없다** — 아래 6.3 참조.
- **고난을 당사자의 실패로 귀책하지 않는다.** T1 축이 이 방향(번영신학 역방향)을 본다.
- 답 없음은 *결함* 이 아니라 이 미션의 문법이다. "그래도 결국엔" 으로 봉합하지 않는다.

---

## 1. Scene 1 — 침묵의 7일 (60초)

### 1.1 배경·환경

| 항목 | 값 |
|---|---|
| `space_id` | `uz_ash_heap_dusk` |
| 조명 | `overcast_dusk_after_storm` / 4200K |
| 앰비언트 | `low_wind_open_ground` (-24dB, spatial) + `distant_livestock_absent_silence` |
| 사용자 위치 | `[0.0, 1.20, 0.0]` — 앉은 눈높이 |
| NPC | `job_self`(사용자 시점 근처) · `three_friends_silhouette`(실루엣, 음성 없음) |

두 번째 앰비언트는 *있던 소리가 사라진 자리* 다. 가축·사람 소리가 있어야 할 대역을
비워 둔다 — 상실을 자막으로 설명하지 않고 음향으로 남긴다.

**시각 배제**: 욥의 신체 질환(종기)은 그리지 않는다
(`content/job/scene1.yml :: body_affliction_visual_exclusion`). 만성질환·피부질환 당사자에게
불필요한 자극이고, 서사가 그 묘사에 기대지 않는다.

### 1.2 대본

| 블록 | 화자 | 시작 | 길이 | 내용 |
|---|---|---|---|---|
| `nb_s1_seven_days` | 나레이터 | 4.0s | 12.0s | "친구 세 명이 욥의 슬픔이 너무 큰 것을 보고 *말없이* 7일 밤낮 함께 앉았습니다. 말이 필요 없는 자리가 있습니다." |
| `nb_s1_silence_hold` | — | 20.0s | 25.0s | (무음 — 텍스트도 음성도 없음) |

두 번째 블록은 **의도된 25초 침묵**이다. 자막도 음성도 없고 진행 표시도 없다. 이 시간이
비어 있다고 판단해 무언가로 채우는 변경은 이 Scene 의 요점을 지운다.

출처: `scenarios/job.yml :: scenes[id=1].extras.static_text` (욥 2:13).

### 1.3 인터랙션

`ix_s1_sit_with` — `dwell_presence`. 목표 자산 없음, 제스처 없음.
`no_progress_indicator: true`, `on_timeout: treat_as_complete`.
5초 후 skip 가능(`skippable_after_seconds: 5`, 런타임 값과 동일).
기록: `time_before_skip_seconds` (기록만 — 빨리 넘긴 것이 실패가 아니다).

### 1.4 안전·신학 footer

| 게이트 | 성격 |
|---|---|
| `R4_pre_scene0_consent_required` | 진입 전 동의 (0.1) |
| `R1_voice_self_harm_listener` | 상시 · `pause_fade_black_show_crisis_card` |
| `R5_ai_optout_respected` | LLM 경로 없음 |
| `body_affliction_visual_exclusion` | 종기 묘사 배제 |
| `footer_persistent` | 0.5 문구 |

신학 각주: `J1_candidate` — 욥 2:13 의 7일 침묵. disputed_point 으로 **천상 회의(욥 1~2장)
장면을 무대에 올리지 않는다**고 적어 두었다. 사탄과 하나님의 대화를 시각화하면 고난의
원인을 사용자가 *알게* 되는데, 욥 본인은 끝까지 모른다. 그 비대칭이 이 이야기의 구조다.

---

## 2. Scene 2 — 태어난 날을 저주하다 (90초) ★ R4 mid 민감

### 2.1 배경·환경

| 항목 | 값 |
|---|---|
| `space_id` | `uz_ash_heap_night` |
| 조명 | `moonless_night_low_key` |
| 앰비언트 | `low_wind_open_ground` + `distant_dog_single_bark`(1회) |
| NPC | `job_self` 만 |

친구들은 무대에서 사라진다. 침묵의 7일이 끝나고 욥이 먼저 입을 여는 자리이며,
아직 친구들은 대답하지 않았다.

### 2.2 R4 추가 동의 게이트 — Scene 2 진입 직전 (필수 · **mid 강도**)

```
┌─────────────────────────────────────────────────────────┐
│  잠깐 안내드릴게요                                        │
│                                                          │
│  이어지는 장면에는 욥이 자기 태어난 날을 저주하는          │
│  본문(욥 3장)이 나옵니다. 죽음을 갈망하는 말에 가까운       │
│  표현이 포함됩니다. 지금 보기 버거우시면 건너뛰셔도         │
│  됩니다 — 미션은 그대로 이어집니다.                        │
│                                                          │
│  위기 시 {{crisis_resources.default}}                     │
│                                                          │
│  [계속 보기]        [건너뛰기 — 다음 장면으로]              │
└─────────────────────────────────────────────────────────┘
```

- `consent_card_id`: `job_scene2_lament_warning`
- `skip_alternative_scene_id`: `3`
- skip 시 요약 자막: "욥은 침묵을 깨고 자기 태어난 날을 저주했습니다. 이 장면을 지나
  친구들이 입을 여는 자리로 갑니다."

건너뛴 사용자도 서사가 끊기지 않는다. 요약 자막이 Scene 3 의 전제를 채운다.

### 2.3 대본

| 블록 | 화자 | 시작 | 길이 | 내용 |
|---|---|---|---|---|
| `nb_s2_break_silence` | 나레이터 | 4.0s | 14.0s | "욥은 *침묵을 깨고* 자기 태어난 날을 저주합니다. 신은 욥의 이 외침을 책망하지 않으셨습니다. *비탄을 말할 자유* 가 신앙 안에 있습니다." |
| `nb_s2_scripture_primary` | 성경 자막 | 20.0s | 12.0s | 욥 3:3 |
| `nb_s2_scripture_additional` | 성경 자막 | 34.0s | 12.0s | 욥 3:11 · 욥 6:8 |
| `nb_s2_reflection` | 시스템 | 50.0s | 16.0s | "당신이 지금 *말할 수 없었던* 외침이 있다면, 한 단어라도 떠올려 보세요. 화면에 적을 필요 없습니다…" |
| `nb_s2_priority_notice` | 시스템 | 70.0s | 12.0s | "지금 실제로 죽음 생각이 크다면, 본문보다 먼저 연결되셔야 할 곳이 있습니다 — {{crisis_resources.default}}" |

마지막 블록이 이 Scene 의 안전 설계다. 런타임 `job.yml` 의 `language_note` 를
**사용자에게 보이는 문구로 승격**한 것이다. 문서에만 있는 주의사항은 사용자를 보호하지
않는다. 위기 신호가 있을 때 본문보다 자원이 먼저라는 것을 화면에서 말한다.

### 2.4 인터랙션

`ix_s2_recall_only` — `silent_recall`. **`no_text_input: true`**.

사용자의 비탄을 텍스트로 입력받지 않는다. 받는 순간 그것을 저장·전송·보관하는 책임이
생기고, 이 미션은 그 책임을 질 설계가 아니다. "떠올리는 것만으로 충분하다"는 대본의
문장이 구현에서도 사실이어야 한다.

### 2.5 안전·신학 footer

| 게이트 | 성격 |
|---|---|
| `R4_trigger_consent_required` | 2.2 동의 카드 (`consent_card_ref`) |
| `R1_voice_self_harm_listener` | 상시 |
| `R1_crisis_routing_precedence` | 위기 신호 시 본문보다 자원 우선 |
| `lament_not_romanticized` | **집행 수단 없음** — `enforcement: reviewer_only` (6.3 참조) |
| `R5_ai_optout_respected` | LLM 경로 없음 |
| `footer_persistent` | 0.5 문구 |

---

## 3. Scene 3 — 친구들의 위로, 정답이 아닌 것 (90초) ★ 핵심 인터랙션

### 3.1 배경·환경

| 항목 | 값 |
|---|---|
| `space_id` | `uz_ash_heap_morning` |
| 조명 | `flat_morning_overcast` / 5600K, `flat_shadowless_emphasis: true` |
| NPC | `three_friends`(얼굴·음성 획득) · `job_self` |

그림자 없는 평평한 아침 빛. 극적 조명을 쓰지 않는다 — 친구들의 말은 악당의 대사가
아니라 *선의의 확신* 이고, 조명이 그들을 악역으로 만들면 사용자가 자기 안의 같은 말을
알아보지 못한다.

### 3.2 대본

| 블록 | 화자 | 시작 | 길이 | 내용 |
|---|---|---|---|---|
| `nb_s3_friends_speak` | 나레이터 | 4.0s | 16.0s | "친구들은 *답을 가지고* 왔습니다. '네가 죄가 있어서다', '곧 회복될 거다'. 신은 마지막에 친구들을 책망하셨습니다 (욥 42:7). *거짓 위로* 가 *침묵* 보다 해롭다는 것." |
| `nb_s3_scripture_rebuke` | 성경 자막 | 24.0s | 14.0s | 욥 42:7 (엘리바스 책망) |

친구들의 말은 **인용으로만** 등장한다. 확장하거나 다듬지 않는다 — 다듬을수록 설득력이
생기고, 그것이 이 Scene 이 막으려는 것이다. 본문 인용(42:7)을 앞당겨 배치해, 친구들의
말이 틀렸다는 판정을 사용자에게 맡기지 않고 본문이 먼저 말하게 한다.

### 3.3 인식 카드 3택 (사전 큐레이션 4종)

`ix_s3_pick_one` — `pick_one`, `gaze_dwell_or_pinch`.
`options_order_fixed: true`(무작위 배열 금지 — "잘 모르겠다"가 항상 마지막),
`no_correct_answer_marker: true`(정답 표시·점수·성취 뱃지 없음),
40초 후 자동 진행, 재촉 음성 프롬프트 없음.

| `recognition_key` | 라벨 | 응답 |
|---|---|---|
| `heard_it_before` | 나도 이런 말 들어본 적 있다 | 당신만이 아닙니다. 그 말을 들으셨을 때 느꼈던 외로움은 정당한 감정입니다. |
| `said_it_before` | 내가 다른 사람에게 이런 말 했을지도 모른다 | 그 자각이 욥의 친구들이 끝까지 못 한 것입니다. 다음에 누군가 곁에 있을 때, *침묵* 의 선택지가 있다는 것을 기억해주세요. |
| `not_sure` | 잘 모르겠다 | 괜찮습니다. 욥기는 답이 아닌 *질문* 의 책입니다. |
| `null` (미선택·타임아웃) | — | 괜찮습니다. 욥기는 답이 아닌 *질문* 의 책입니다. |

네 응답 전부 `scenarios/job.yml :: scenes[id=3].extras.options[].response` 원문이다.
미선택 경로는 "잘 모르겠다"와 같은 문구로 동일하게 완결한다 — **선택하지 않은 것이
손해가 되지 않는다.**

`said_it_before` 응답이 사용자를 책망하지 않는다는 점이 중요하다. 자각을 *욥의 친구들이
끝까지 못 한 것* 으로 놓아, 자책이 아니라 앞으로의 선택지로 전환한다.

### 3.4 결정 기록

`decision_key: recognition_key`, `default_path: static_curation`,
`llm_optin_only: false` + `llm_disabled_reason`.

욥 미션은 **LLM 분기 자체가 없다.** opt-in 스위치조차 두지 않는다 — 신학·임상 양쪽 검토
전이라는 것이 런타임 `job.yml` 헤더의 결정이고, 저작층은 그것을 그대로 따른다.

### 3.5 안전·신학 footer

| 게이트 | 성격 |
|---|---|
| `R2_no_suffering_gaslighting` | 표층형 **13종** lint |
| `T1_no_heterodox_theology` | 표층형 **8종** lint |
| `retribution_theology_excluded` | 인과응보 서술 금지 (욥 42:7) |
| `R1_voice_self_harm_listener` | 상시 |
| `R5_ai_optout_respected` | LLM 경로 없음 |
| `footer_persistent` | 0.5 문구 |

축 두 개가 왜 여기 있는지는 6.1 참조.

신학 각주 disputed_point: **엘리후(욥 32~37)의 위치** — 친구 셋과 함께 책망받았는지에
해석 차가 있다. 본 미션은 엘리후를 무대에 올리지 않으므로 판정하지 않는다. Scene 3 의
"친구들"은 엘리바스·빌닷·소발 셋이다.

---

## 4. Scene 4 — 폭풍 가운데서 하나님의 응답 (90초)

### 4.1 배경·환경

| 항목 | 값 |
|---|---|
| `space_id` | `uz_open_sky_whirlwind` |
| 조명 | `storm_break_shafts` / 6500K, `volumetric_shafts: true` |
| 앰비언트 | `whirlwind_low_roll`(강도 토글 연동) + `wide_air_openness` |
| 사용자 시선 | `[0.0, 0.3, 1.0]` — 약간 위로 |
| NPC | **없음** — 응답은 얼굴이 아니라 규모로 온다 |

### 4.2 폭풍 강도 토글 (안전)

`whirlwind_intensity_toggle` — `off` / `low` / `high`, **기본값 `low`**.

큰 소리·저주파는 트라우마 반응과 전정기관 불편 양쪽에 걸린다. 강한 연출을 기본으로 두면
그것을 끄는 일이 사용자 부담이 된다. 기본을 약하게 두고 원하는 사용자가 올리게 한다.

### 4.3 대본

| 블록 | 화자 | 시작 | 길이 | 내용 |
|---|---|---|---|---|
| `nb_s4_no_explanation` | 나레이터 | 8.0s | 14.0s | "신은 욥의 *질문에 답하지 않으셨습니다*. 대신 *우주의 광활함* 을 보여주셨습니다. 답이 *설명* 이 아니라 *함께 있음* 이라는 것." |
| `nb_s4_scripture_whirlwind` | 성경 자막 | 26.0s | 10.0s | 욥 38:1 |
| `nb_s4_reflection` | 시스템 | 58.0s | 18.0s | "…하지만 *함께 있을* 수 있는 사람이 한 명 있다면 — 그 자리를 부탁해 보는 것도 회복의 한 걸음입니다." |

마지막 문장의 "부탁해 보는 것도"는 **권유이지 과제가 아니다.** 이 어미를 강하게 고치면
(예: "부탁하세요") 사회적 연결이 없는 사용자에게 실패 항목이 하나 늘어난다.

### 4.4 인터랙션

`ix_s4_look_up` — `gaze_open`, `head_look`.
`no_target_reticle: true`(조준점 없음 — 찾아야 할 대상이 없다), `no_progress_indicator: true`,
40초 후 `treat_as_complete`. 햅틱 `SLOW_SWELL` 0.15 / 400ms.
기록: `upward_gaze_seconds`.

올려다보지 않아도 진행된다. 고개를 들 수 없는 상태가 이 미션이 다루는 상태다.

### 4.5 안전·신학 footer

| 게이트 | 성격 |
|---|---|
| `whirlwind_intensity_toggle` | 4.2 |
| `no_explanation_promised` | 폭풍 응답을 "결국 답을 주셨다"로 재해석 금지 |
| `R1_voice_self_harm_listener` | 상시 |
| `R5_ai_optout_respected` | 전부 정적 본문 |
| `footer_persistent` | 0.5 문구 |

신학 각주 disputed_point: **베헤못·리워야단(욥 40~41)의 정체** — 실재 동물 / 신화적 혼돈
세력 / 은유 사이에 해석 차가 있다. 본 미션은 이 본문을 무대에 올리지 않는다. 광활함의
체험만 남기고 특정 해석을 택하지 않는다 — 택하는 순간 그 해석이 미션의 신학 주장이 된다.

---

## 5. Scene 5 — 회복, 그러나 답 없이 (60초, outro)

### 5.1 배경·환경

| 항목 | 값 |
|---|---|
| `space_id` | `uz_ash_heap_late_light` |
| 조명 | `low_warm_late_afternoon` / 3000K |
| 앰비언트 | `settling_air_quiet` (-30dB) |
| 특수 | `restored_wealth_visuals: excluded` |

밝아지되 **새 아침이 아니다.** 하루가 끝나가는 빛이다 — 되돌려받은 것이 아니라 견뎌낸 것.
되찾은 재산·자녀·가축을 그리지 않는다. 그리는 순간 결말이 보상 서사가 된다.

### 5.2 대본

| 블록 | 화자 | 시작 | 길이 | 내용 |
|---|---|---|---|---|
| `nb_s5_meeting_not_explanation` | 나레이터 | 3.0s | 9.0s | "욥은 결국 *설명* 을 듣지 못했습니다. 그러나 *만남* 을 얻었습니다." |
| `nb_s5_scripture_seeing` | 성경 자막 | 14.0s | 10.0s | 욥 42:5 |
| `nb_s5_value_prompt` | 시스템 | 28.0s | 12.0s | "욥처럼 *답이 아닌 자리* 도 있습니다. 오늘 *솔직한 한 줄* 을 일기에 적어보세요. 부끄러운 것이 아닙니다." |
| `nb_s5_suffering_footer` | 시스템 | 42.0s | 14.0s | R2 필수 footer (5.4) |
| `nb_s5_next_suggestion` | 시스템 | 52.0s | 7.0s | 다음 이야기 — 엘리야 |

연결 가치: `[5, 4, 1]` — 욥(고통과 진리) · 시편(비탄) · 일기(솔직함).

### 5.3 감정 키 분기 (사전 큐레이션 6종)

`br_s5_recovery_key`, `decision_key: recovery_key`, `default_path: static_curation`.

| `recovery_key` | 메시지 키 |
|---|---|
| `SAD` | `job.recovery.lament_permitted` |
| `CONFUSED` | `job.recovery.question_book` |
| `LONELY` | `job.recovery.silent_presence` |
| `ANGRY` | `job.recovery.honest_cry` |
| `EXHAUSTED` | `job.recovery.seven_days_silence` |
| `null` (미선택) | `job.recovery.silent_presence` |

저작층은 **키만 싣는다.** 문구 실체는 런타임 메시지 번들이 갖는다 — 저작층이 별도 문구를
가지면 둘이 갈라지고, 어느 쪽이 사용자에게 나가는지 알 수 없게 된다.

미선택 폴백을 "함께 있음"(`silent_presence`) 쪽으로 둔 것은 의도적이다. 감정을 고르지
않은 사용자에게 고르라고 되묻지 않는다 — **고르지 못하는 상태가 이 미션이 다루는 상태다.**

### 5.4 필수 suffering footer (축약·삭제 불가)

> 이 묵상은 자발적인 고난의 의미를 다룹니다. 가정폭력·종교적 학대·정신적 학대 같은 피해
> 상황을 견디라는 강요가 아닙니다. 안전하지 않은 상황에 있다면 가까운 상담 자원에
> 연결되세요.

학대 상황 예시가 빠지면 이 미션 전체가 "견디라"로 읽힐 수 있다. 의역·축약 금지.
출처: `scenarios/job.yml :: scenes[id=5].extras.suffering_footer`.

### 5.5 지속 노출 위기 안내

```yaml
crisis_reminder:
  persistent: true
  dismissible: true
  text_ko: "지금 이 순간이 무겁다면, {{crisis_resources.default}}."
```

런타임이 이미 내보내는 값 그대로다. 저작 파일이 새 문구를 발명하지 않는다.

### 5.6 완주 처리

`no_completion_badge: true`. 완주 뱃지·점수·축하 연출 없음
(`no_completion_reward` 게이트). **끝냈다는 것이 회복했다는 뜻이 되지 않게 한다.**

### 5.7 안전·신학 footer

| 게이트 | 성격 |
|---|---|
| `R3_no_restoration_guarantee` | 표층형 **35종** lint |
| `restoration_epilogue_excluded` | 욥 42:10~17 범위 밖 |
| `suffering_footer_required` | **집행 수단 없음** — `enforcement: reviewer_only` |
| `R2_no_suffering_gaslighting` | 표층형 13종 lint (Scene 3 과 동일 목록, 대상이 다름) |
| `no_completion_reward` | 뱃지·축하 금지 |
| `R1_voice_self_harm_listener` | 상시 |
| `R5_ai_optout_respected` | LLM 경로 없음 |
| `footer_persistent` | 0.5 문구 |

---

## 6. 안전 축 배치 — 왜 전 Scene 이 아닌가

### 6.1 축은 위반이 일어나는 자리에 둔다

| 축 | 선언 위치 | 표층형 수 | 근거 |
|---|---|---|---|
| R2 (고난 가스라이팅) | `scene3.yml` · `scene5.yml` | 13 | 욥의 친구들이 하는 말이 이 축의 교과서적 표본이고, 결말부 footer 도 같은 위험을 진다 |
| T1 (이단 신학) | `scene3.yml` | 8 | 고난을 당사자에게 귀책하는 번영신학 역방향이 친구들의 논리와 같은 자리에 있다 |
| R3 (회복 보장 금지) | `scene5.yml` | 35 | 욥 42:10 을 결말로 쓰지 않는다는 결정이 여기서 지켜진다 |

전 Scene 에 같은 목록을 복사하면 **초록만 늘고 집행은 늘지 않는다.** 합집합 56종이
`newchar_gates.py G5a-i` 하한(6종)을 크게 넘는다.

### 6.2 토큰은 저작층에서 발명하지 않았다

56종 전부가 이미 런타임 전역 목록
(`backend/src/main/resources/application.yml :: safety.forbidden-tokens.list`, 688종)에 있는
표층형이다. 저작에만 있고 런타임 방어는 0인 상태를
`ContentSafetyGateEnforcementTest` 가 막는다(`G5e` / `G5b` 도 같은 것을 본다).

### 6.3 집행 수단이 없는 게이트는 그렇게 적는다

`scene2.yml :: lament_not_romanticized` 와 `scene5.yml :: suffering_footer_required` 는
`enforcement: reviewer_only` 다.

연출(카메라 워크·음악 상승)과 문장 존재 요구는 토큰으로 표현되지 않고, 이 리포에 그것을
확인하는 기계 검사도 없다. `enforcement: structural` 로 적고 기존 테스트 이름을 갖다
붙이면 그 테스트는 이 파일들에 대해 공회전한다(토큰 0종). 이 리포에서 이미 3번 난
vacuous green 이 정확히 그 형태다. 사람 검토가 볼 자리로 남겨 두고, 지금 상태가
**선언뿐**이라는 것을 파일 안에 적었다.

`suffering_footer_required` 의 id 에 축 접두(`R2_`)를 붙이지 않은 것도 같은 이유다.
축 접두를 붙이면 게이트 검사가 토큰 목록을 요구하는데, 거기에 R2 토큰을 한 번 더 복사해
넣으면 *같은 lint 를 두 번 선언해 초록 두 개* 가 된다.

### 6.4 초록이 주장하는 범위

**표층형 lint 다.** 게이트 PASS 는 「선언된 정확한 표층형이 대상 텍스트에 없다」까지이고
「R2/R3/T1 위반 0건」이 아니다.

- 유의어 재작성은 막지 못한다 — "이 시간이 당신을 빚고 있습니다"(R2 방향),
  "결국 다 제자리로 돌아옵니다"(R3 방향)는 어느 토큰에도 걸리지 않는다.
- 그리스도론 이단(양자론·가현설 등)은 T1 8종의 범위 밖이다. 이 8종은 영지주의 이원론·
  뉴에이지·번영신학 역방향의 표층형이다.
- 한국어 활용·높임형이 표층형을 바꾼다. 토큰은 어간까지만 끊어 변이를 일부 흡수하지만
  전부를 흡수하지는 않는다.

R3 의 구조적 방어는 lint 와 **별도**다 — `restored_wealth_visuals: excluded` 와
본문 인용을 42:5 에서 끊는 결말 설계가 그것이다. 토큰이 뚫려도 무대와 본문이 회복 서사를
만들지 않는다.

---

## 7. 안전 게이트 — 실 동작 예

### 7.1 R1 시뮬레이션 — Scene 2 죽음 관련 발화 (최고 우선)

Scene 2 는 죽음 소원 본문이 나오는 유일한 Scene 이다. 음성 리스너가 자해 신호를 잡으면
`pause_fade_black_show_crisis_card` — **본문 재생을 멈추고** 위기 카드를 띄운다.
`R1_crisis_routing_precedence` 가 "본문보다 자원이 먼저"를 명시하고,
`nb_s2_priority_notice` 가 그 원칙을 사용자에게 보이는 문구로 이미 말한다.

번호는 리터럴로 나가지 않는다 — `{{crisis_resources.default}}` 만 저장되고
`CrisisTokenResolver` 가 렌더 시점에 해석한다.

### 7.2 R2 시뮬레이션 — 친구들의 말이 서사의 편이 되지 않음

Scene 3 의 친구 대사는 인용 블록 하나뿐이고, 바로 뒤에 욥 42:7 책망이 온다.
사용자 응답 4종 중 어느 것도 "네 탓" 프레임을 강화하지 않는다.
`retribution_theology_excluded` 가 이 규율을 게이트로 적어 두었다.

### 7.3 R3 시뮬레이션 — 결말이 회복을 약속하지 않음

Scene 5 본문 인용은 42:5 에서 끊긴다. 42:10~17(재산·자녀 회복)은 무대에 없고, 환경도
되찾은 재산을 그리지 않는다. 35종 lint 는 그 위에 얹은 표층형 방어다.

### 7.4 R4 시뮬레이션 — Scene 2 진입 게이트

동의 카드 미확인 상태로 Scene 2 진입 불가. skip 선택 시 Scene 3 로 직행하고 요약 자막이
전제를 채운다. Pre-Scene 0 은 건너뛰기 불가.

### 7.5 R5 시뮬레이션 — AI 응답

욥 미션은 **LLM 분기가 없다.** opt-out 여부와 무관하게 같은 서사가 완결된다.
`llm_optin_only: false` + `llm_disabled_reason` 이 그 상태를 기록한다 —
"opt-in 을 안 켠 것"이 아니라 "경로가 없는 것"이다.

---

## 8. 게이트 실측 (2026-08-22)

```
python3 scripts/newchar_gates.py --character job
→ PASS 22 / FAIL 0 / BLOCKED 7
```

**BLOCKED 7 은 통과가 아니라 판정 불가다.**

| 게이트 | 왜 못 재나 |
|---|---|
| `G0b` · `G0e` · `G9` · `G9d` | `scripts/gates/job.yml` 에 `exclusions` 미정의 — 배제 목록이 비어 순회 0회 |
| `G0c` | `token_examples` 0개 — 토큰:예문 대조 불가 |
| `G0d` | `polite_evasive` 0개 — 제품 말투 위험 문장 커버리지 불가 |
| `G2v` | `latch_contract` 미선언 — 래치 키의 값을 재지 않았다 |

전부 **설정에 선언이 없어서** 못 재는 것이고, 콘텐츠가 통과한 것이 아니다.
엘리야·솔로몬도 같은 이유로 같은 게이트가 BLOCKED 다.

백엔드: `gradle test --tests '*safety*'` BUILD SUCCESSFUL
(`ContentSafetyGateEnforcementTest` · `ForbiddenTokenConfigTest` · `ScenarioHotlineRatchetTest` 포함).

---

## 9. 콘텐츠 검수 체크리스트

기계가 이미 보는 것 (`newchar_gates.py` · Kotlin 테스트):

- [x] Scene 파일 5개 (`G0a`)
- [x] 마지막 Scene 에 null 폴백 라우트 (`G1b`)
- [x] 5/5 Scene 에 `R1_voice_self_harm_listener` (`G2`)
- [x] 하드코딩 crisis 번호 0건 (`G3`)
- [x] `{{crisis_resources.default}}` 5/5 Scene + 마지막 Scene (`G3b` · `G3c`)
- [x] `crisis_reminder` 블록 (`G3d`)
- [x] 축 4건 전부 집행 수단 보유 (`G5a-ii`)
- [x] 저작 합집합 56종 ⊆ 런타임 목록 688종 (`G5e`)
- [x] 트리거 Scene 에 `consent_card_id` + `skip_alternative_scene_id` (`G7`)
- [x] 분기 Scene 2개 전부 `default_path: static_curation` (`G8`)
- [x] 5/5 Scene 에 "AI 보조" 노출 (`G11`)

기계가 못 보는 것 — **사람이 봐야 한다**:

- [ ] `lemuel-theology-reviewer` 사인오프 (특히 Scene 3 엘리후 배제, Scene 5 42:6 배제)
- [ ] `lemuel-mental-health-safety` 사인오프 (**Scene 2 인간 사인오프 권고**)
- [ ] `lament_not_romanticized` — 실제 연출물(카메라·음악)이 비탄을 미학화하지 않는지
- [ ] `suffering_footer_required` — 최종 빌드에서 footer 가 축약되지 않았는지
- [ ] 25초 침묵(Scene 1)이 QA 에서 "버그"로 리포트되어 채워지지 않았는지
- [ ] `job.recovery.*` 메시지 번들 6종의 실 문구 검수 — 이 문서 범위 밖(런타임 번들)

---

## 10. 다음 단계

1. **BLOCKED 7 해소** — `scripts/gates/job.yml` 에 `exclusions` · `token_examples` ·
   `polite_evasive` · `latch_contract` 선언. 선언 없이는 계속 판정 불가다.
2. **인간 사인오프 2건** — 신학·정신건강. Scene 2 는 권고가 아니라 사실상 필수.
3. **오디오 자산** — `aud_s*_*_ko` 클립 실물 미제작. 현재는 id 만 존재한다.
4. **`job.recovery.*` 번들 검수** — 저작층이 키만 들고 있으므로 문구 품질은 별도 확인.
5. **J1~J5 서지 확정** — `theology_footer_refs` 의 `*_candidate` 를 실제 서지로 교체.
   현재는 전부 "확정 서지 미확보(드릴다운 필요)"다.

*AI 보조 — 본문은 성경 참조.*
