# MVP-JESUS-CONTENT — 예수 미션 저작 계층 (content/jesus/)

> **정본 우선순위:** `backend/src/main/resources/scenarios/jesus.yml`(대본) >
> `content/jesus/*.yml`(무대) > 이 문서(설명).
> 세 층이 어긋나면 위가 이긴다. 이 문서는 *왜 그렇게 무대를 세웠는가* 를 적는 곳이지
> 대본을 정하는 곳이 아니다.

이 문서가 설명하는 것은 `content/jesus/` 일곱 파일이다. 그 파일들이 왜 생겼는지는
`content/jesus/README.md` 에, 미션의 신학·서사 설계는 `docs/MVP-JESUS.md` 에 있다.

---

## §0 공통 컨벤션

### §0.1 Pre-Scene 0 — 동의 카드

미션 진입 전에 한 번, 텍스트로만.

```
┌────────────────────────────────────────────────┐
│  이 미션은 고난·죽음·부활을 다룹니다.          │
│                                                │
│  · AI 보조 콘텐츠입니다. 본문은 성경 참조.     │
│  · 상담·치료를 대체하지 않습니다.              │
│  · 언제든 중단할 수 있습니다.                  │
│                                                │
│      [ 시작 ]            [ 지금은 아니오 ]     │
└────────────────────────────────────────────────┘
```

두 버튼은 같은 크기·같은 위계다. 기본 포커스를 [시작] 에 두지 않는다 —
포커스가 곧 기본값이고, 기본값이 된 동의는 동의가 아니라 관성이다.

### §0.2 인터랙션 추상

디바이스를 특정하지 않는다. 저작 파일은 *의도* 만 적고 매핑은 Unity 쪽이 한다.

| 저작 표기 | HMD 컨트롤러 | 핸드트래킹 | 모바일 |
|---|---|---|---|
| `gaze_dwell_or_pinch` | 트리거 | 핀치 | 탭 |
| `hand_reach` | 그립 + 전진 | 손 뻗기 | 스와이프 |
| `audio_focus` | 없음 (수동) | 없음 | 없음 |

### §0.3 눈높이

- **1.55m — 서 있는 눈높이.** Scene 1·3·6·7. 길가·동산·언덕.
- **1.20m — 앉은 눈높이.** Scene 2·4·5. 산상 설교를 앉아서 듣는 자리(마 5:1 "앉으시니"),
  다락방, 겟세마네.

높이를 이야기 안의 자세에서 가져온다. "몰입감" 같은 이유로 정하지 않는다.

### §0.4 영구 푸터

```
AI 보조 — 본문은 성경 참조 — storyteller 역할 | 위기 시 {{crisis_resources.default}}
```

일곱 파일 전부 `footer_persistent` 게이트로 이 문자열을 갖는다. 위기 자원 번호는
**리터럴로 쓰지 않는다** — `{{crisis_resources.default}}` 를 `CrisisTokenResolver` 가
배포 지역에 맞게 푼다. 문서·저작·런타임 어디에도 번호가 박히면 안 되고,
`ScenarioHotlineRatchetTest` 와 게이트 G3 가 그것을 매번 다시 잰다.

### §0.5 어조 정책

- 2인칭 명령형을 쓰지 않는다. 본문 인용과 서사 진술로만 말한다.
- 사용자의 상태를 진단하지 않는다.
- 회복을 약속하지 않는다. <!-- lint_forbidden_tokens 인용 --> "부활의 소망으로 극복" 류가 금지되는 이유가 이것이다.
- 완주에 보상을 붙이지 않는다.

---

## §1 Scene 1 — 성육신 (60초, cinematic)

**파일** `content/jesus/scene1.yml` · **선언 축** T1 · **본문** 요 1:14

### §1.1 무대

`bethlehem_stable_night`. 조명은 2700K 램프 하나, 낮게. **후광을 렌더하지 않는다.**
후광은 성육신을 *덜 육체적으로* 보이게 만드는 시각 문법이고, 이 Scene 의 요점은
말씀이 **육신이 되셨다** 는 것이다.

사용자는 1.40m — 서 있는 성인보다 낮고 앉은 높이보다 높다. 구유를 내려다보지도,
올려다보지도 않게 잡은 값이다.

### §1.2 NPC

`manger_child` 하나. **정면 클로즈업을 잡지 않는다.** 성상 논쟁(그리스도의 형상을
그리는 것이 정당한가)은 개혁주의 안에서 오래된 쟁점이고, 이 미션은 그것을 판정하지
않는다. 클로즈업을 피한 것은 **연출 결정이지 신학 판정이 아니다** — 파일에도 그렇게 적었다.

### §1.3 사용자 노출 문장

전부 `scenarios/jesus.yml :: scenes[id=1]` 에서 그대로 왔다.

| 블록 | 출처 키 |
|---|---|
| `nb_s1_word_became_flesh` | `extras.static_monologue` |
| `nb_s1_scripture_jn114` | `scripture_ref: jn-1:14` 본문 |

3초 뒤 스킵 가능(`skippable_after_seconds: 3`). 런타임 값 그대로다.

### §1.4 축

**T1 8종.** 성육신은 영지주의가 정면으로 부정하는 지점이다 — 물질이 악하다면
하나님이 육신이 되실 수 없다. "갇혀 있는 감옥"·"창조는 실패"·"나의 신성" 같은
표층형이 실제로 들어올 자리가 여기다.

동시에 이 Scene 에서 **가장 위험한 것은 그 8종에 없다.** 가현설·양자론·아리우스 —
그리스도론 이단은 토큰이 아니다. "예수는 신처럼 보이셨다" 는 문장에 금지어가 하나도
없다. 파일 주석에 그렇게 적어 두었다. 초록은 그 8개가 없다는 뜻이지
그리스도론이 정통이라는 뜻이 아니다.

### §1.5 reviewer_only

`docetism_visual_guard` — 시각 연출이 가현설을 암시하는가는 사람이 본다.
집행 수단이 없다고 파일에 적었다.

---

## §2 Scene 2 — 팔복 (90초, scripture_reading)

**파일** `content/jesus/scene2.yml` · **선언 축** 없음 · **본문** 마 5:3·4·6

### §2.1 무대

`galilee_hillside_morning`. 사용자 1.20m(앉은 자세), `jesus_teaching` 은 2.4m 앞.
마 5:1 이 "앉으시니" 이므로 화자도 사용자도 앉아 있다.

### §2.2 사용자 노출 문장

세 팔복 본문 + `reflection_prompt`. 전부 런타임 인용이다.

### §2.3 인터랙션

`ix_s2_recall_empty_place` — `no_text_input: true`. 사용자가 자기 이야기를
**입력하지 않는다.** 입력을 받으면 그것을 처리해야 하고, 처리는 판정이 된다.
이 Scene 에서 필요한 것은 떠올리는 것이지 제출하는 것이 아니다.

### §2.4 왜 축이 없는가

팔복은 고난 서사도, 부활 서사도, 이단 어휘가 들어올 자리도 아니다.
같은 토큰 목록을 여기 복사하면 게이트 초록이 하나 늘고 실제 집행은 그대로다.

파일 주석에 그 판단을 명시했고, `#` 주석에 `R2`/`R3`/`T1` 문자열을 쓰지 않았다 —
Kotlin 산문 전용 래칫이 주석의 축 언급을 같은 파일의 집행 게이트와 대조하기 때문이다.

### §2.5 reviewer_only

`beatitude_not_a_task` — 팔복이 성취 과제로 읽히지 않게. 어조 문제라 표층형이 없다.

---

## §3 Scene 3 — 만짐·병자를 고치심 (90초, gesture_sequence)

**파일** `content/jesus/scene3.yml` · **선언 축** 없음 · **본문** 막 1:41

### §3.1 무대

`galilee_roadside_edge`. 앰비언트가 **군중이 물러나는 발소리** 다. 이 Scene 의 배경은
접근이 아니라 회피이고, 예수가 들어가시는 자리가 그 빈 공간이다.

### §3.2 NPC

`person_with_illness` — **질환의 시각 묘사를 하지 않는다.** 나병의 피부 병변을
렌더하지 않는다. 만성질환·피부질환 당사자에게 불필요한 자극이고, 본문의 요점은
병의 외양이 아니다. `illness_visual_exclusion` 게이트가 이것이다.

### §3.3 제스처

| 단계 | 라벨 | 이벤트 |
|---|---|---|
| `approach` | 다가간다 | `step_approach_done` |
| `reach_out` | 손을 내민다 | `step_reach_out_done` |

햅틱 `intensity: 0.35`, `PULSE_SOFT`, `BOTH`, 폴백 오디오 큐 `touch-warm`.
런타임 `hapticHint` 값 그대로다.

`no_failure_state: true`. 손을 뻗지 않아도 실패가 아니다. 45초 뒤 자동 진행하고
재촉 음성 프롬프트가 없다 — **손을 뻗지 못하는 상태도 이 미션이 다루는 상태다.**

### §3.4 reviewer_only

`no_instant_healing_promise` — 런타임 note 를 그대로 옮긴 게이트다.
"이 씬은 *치유의 즉효* 를 약속하지 않는다."

집행 수단이 없다. "나아질 것" 을 말하는 표층형은 무한하고 그것을 판정하는 기계
검사가 이 리포에 없다. 현재 근거로 남길 수 있는 사실만 파일에 적었다 —
사용자 노출 문장 3건이 전부 본문 인용·서사 진술이고 2인칭 약속이 0건이다.

---

## §4 Scene 4 — 길·진리·생명 (120초, pick_one) ★핵심

**파일** `content/jesus/scene4.yml` · **선언 축** 없음 · **본문** 요 14:6

### §4.1 무대

`upper_room_lamp_night`, 2900K 램프. 사용자 1.20m. `thomas_asking` 이 왼쪽 1.4m.

**도마를 의심하는 인물로 연출하지 않는다.** 도마는 질문한 사람이고, 그 질문이
요 14:6 을 끌어냈다. 의심을 벌하는 연출은 이 Scene 의 인터랙션(사용자도 고른다)과
정면으로 충돌한다.

### §4.2 세 선택

| `iam_key` | 라벨 | 캐시 키 | 참조 |
|---|---|---|---|
| `the_way` | 길 — 어디로 가야 할지 모를 때 | `jesus.s4.iam.way` | 요 14:4 |
| `the_truth` | 진리 — 무엇이 참인지 흔들릴 때 | `jesus.s4.iam.truth` | 요 8:32 |
| `the_life` | 생명 — 살아갈 힘이 없을 때 | `jesus.s4.iam.life` | 요 10:10 |

`options_order_fixed: true`, `no_correct_answer_marker: true`.
정답 표시도 점수도 없다.

`teaching_note` 는 **선택 뒤에** 나온다 — "세 단어는 서로 다른 세 길이 아니라
*한 분* 의 세 얼굴입니다." 먼저 나오면 선택이 무의미해지고, 뒤에 나오면
어느 선택도 틀리지 않았다는 판정이 된다.

### §4.3 폴백

50초 미선택 시 자동 진행하고 `iam_key: null` 라우트가 `jesus.s4.iam.way` 를 준다.
**되묻지 않는다.** 폴백을 '길' 로 둔 것은 도마의 질문이 길에 관한 것이었기 때문이다
(요 14:5) — 임의 선택이 아니라 본문 순서다.

### §4.4 LLM 경계

`default_path: static_curation` + `llm_optin_only: true`.

이 미션에서 LLM 이 관여하는 곳은 Scene 4·7 둘뿐이고, 그나마 **생성은 오프라인**이다
(`iam_key` 3종 × `faith_tone` 3단 = 9 패턴 사전 캐시). 전달 시점에는 캐시를 읽는다.
opt-out 사용자도 같은 9종으로 서사가 완결되고, opt-in 대상은 캐시 미스 시의
실시간 생성 하나뿐이다. 게이트 G8 이 이 값을 본다.

### §4.5 reviewer_only

`exclusivity_not_weaponized` — 요 14:6 의 유일성을 사용자를 정죄하는 도구로 쓰지 않는다.
유일성은 **그리스도의 충분성에 관한 진술**이지 청중을 향한 판정 문장이 아니다.
미션의 신학 기준(특수구속·유일성, 포용주의·다원주의·만인구원 배제)은 유지하되,
그것을 사람에게 겨누는 문장 형태를 막는다.

집행 수단이 없다. 정죄 어조를 판정하는 기계 검사가 이 리포에 없다.
남길 수 있는 사실만: 이 파일의 사용자 노출 문장에 2인칭 판정문이 0건이고,
`teaching_note` 가 세 선택 전부를 같은 도착지로 보낸다.

### §4.6 판정하지 않는 것

복음을 들을 기회가 없었던 이들의 운명은 개혁주의 안에서도 진술의 폭이 있다.
이 미션은 요 14:6 을 인용하되 그 적용 판정을 내리지 않는다.
**답하지 않는 것을 답한 것처럼 적지 않는다.**

---

## §5 Scene 5 — 겟세마네와 십자가 (120초, contemplative) ★트리거

**파일** `content/jesus/scene5.yml` · **선언 축** R2 · **본문** 눅 22:42

### §5.1 트리거 처리

미션에서 유일한 트리거 Scene 이다(런타임 `trigger_scenes: [5]`).

```yaml
trigger_warning:
  level: medium
  content: [suffering, death]
  consent_card_id: jesus_scene5_passion_warning
  skip_alternative_scene_id: 6
```

건너뛰면 Scene 6 으로 간다 — **미션이 끊기지 않는다.** 건너뛰기가 2차 선택지로
밀리지 않도록 두 버튼을 같은 크기·같은 위계로 두고 기본 포커스를 어느 쪽에도 두지 않는다.
게이트 G7 이 `consent_card_id` 와 `skip_alternative_scene_id` 두 키의 존재를 본다.

### §5.2 무대

`gethsemane_olive_night`, 4100K 달빛, **키 라이트 없음.**
십자가를 극적으로 비추지 않는다 — 고통을 시각적으로 연출할수록 그것은 볼거리가 되고,
볼거리가 된 고통은 견디라는 요구로 읽힌다.

`jesus_praying` 은 1.05m — **사용자보다 낮다.** 이 Scene 에서 그리스도는
위에서 내려다보지 않으신다.

### §5.3 시각 배제

| 배제 | 이유 |
|---|---|
| `crucifixion_body_render` | 십자가 처형의 신체 묘사. medium 등급 동의가 감당하는 범위를 넘는다 |
| `blood_and_wounds` | 상흔·유혈. 자해 이력이 있는 사용자에게 직접 자극 |

`passion_visual_exclusion` 게이트가 `structural_check` 로 이 두 항목의 `excluded: true` 를 본다.

### §5.4 사용자 노출 문장

| 블록 | 출처 키 |
|---|---|
| `nb_s5_gethsemane` | `extras.static_monologue` |
| `nb_s5_cross_line` | `extras.cross_line` |
| `footers[id=suffering_footer]` | `extras.suffering_footer` |

`cross_line` 이 이 미션의 속죄론이 한 문장으로 서는 자리다(형벌 대속) —
"이것은 당신이 이루어야 할 숙제가 아니라, 이미 이루어진 선물입니다."
**숙제가 아니라 선물** 이라는 대비가 R2 축이 막으려는 것과 같은 방향이다.

### §5.5 15초 침묵

`contemplation.seconds: 15`, `no_countdown_ring: true`, `no_prompt_text: true`.
카운트다운 링을 띄우지 않는다 — 남은 시간을 세는 순간 침묵이 과제가 된다.
스킵 가능하다.

R1 자해 음성 리스너는 **침묵 구간에도 계속 듣는다.** 거기서 꺼지면
가장 필요한 순간에 없는 것이 된다.

### §5.6 축 R2

13종. "고난에는 뜻이"·"연단하시려고"·"당신을 시험하시는 중"·"믿음이 부족" 등.
겟세마네는 *그리스도께서* 흔들리신 자리이지 사용자에게 견딤을 요구하는 자리가 아니다.

측정: 이 파일의 사용자 노출 문장 3건 대조 — 적중 0건. 13종 전부 런타임 금지
목록(`application.yml` 688종)에 이미 존재해서 런타임 변경이 필요 없었다(G5e PASS).

### §5.7 축 접두가 없는 게이트 하나

`suffering_footer_required` 는 `R2_` 접두를 달지 않았다.

이것은 **존재 요구** 게이트다(푸터가 Scene 내내 상주하는가). 축 접두를 달면
게이트 G5a-ii 가 축 게이트로 읽어 토큰 목록을 요구하고, 그러면 같은 lint 를
두 번 선언하게 된다 — **초록만 둘로 늘고 집행은 그대로다.**
욥 Scene 5 에서 실제로 이 FAIL 을 맞고 분리한 이력이 있다.

---

## §6 Scene 6 — 부활·빈 무덤 (90초, scripture_reading)

**파일** `content/jesus/scene6.yml` · **선언 축** R3 · **본문** 요 20:15·16

### §6.1 무대

`garden_tomb_dawn`. 빛이 **올라오는 중** 이다. 완성된 아침이 아니다 —
부활을 이미 끝난 밝음으로 그리면 아직 어두운 사용자가 자기만 뒤처졌다고 읽는다.

`risen_jesus` 를 정면 클로즈업으로 잡지 않는다. 본문에서 마리아는 처음에
동산지기로 알아보지 못했다(요 20:15). 알아봄은 얼굴이 아니라 **이름이 불릴 때** 왔다.

### §6.2 인터랙션

`ix_s6_hear_your_name` — `no_name_capture: true`.
**사용자의 실제 이름을 받아 부르지 않는다.** 개인화가 아니라 약속의 형식을 보여 준다.
이름을 입력받으면 그것은 앱이 부르는 것이지 그리스도께서 부르시는 것이 아니다.

### §6.3 손대면 안 되는 문장

`nb_s6_reflection`:

> 부활은 마리아에게 *이름을 불러 주심* 으로 왔습니다. 이 소망은 당신이 스스로
> **부활해 내야** 하는 과제가 아니라, *당신의 이름이 불린다* 는 약속입니다.

반말 어간이라 R3 55종을 통과한다. 존댓말로 다듬으면
<!-- lint_forbidden_tokens 인용 --> "부활해 내셔야 합" 이 되어 **이 미션 자신의
목록에 자기 대본이 걸린다.** 파일에 `verbatim_locked: true` 로 표시했고,
잠근 이유도 같이 적었다. 런타임 머리말이 이 한 음절 차이를 이미 측정해 두었다.

### §6.4 reviewer_only

`recognition_not_user_task` — 알아봄을 사용자의 노력으로 그리지 않는다.

---

## §7 Scene 7 — 승천과 생명의 강 (60초, outro) ★마지막

**파일** `content/jesus/scene7.yml` · **선언 축** T1 · **본문** 요 7:38

### §7.1 무대

`bethany_hillside_open_sky`. 물소리는 **멀리서** 흐른다 — 사용자를 물에 담그지 않는다.
체험 연출이 은사 체험 암시로 읽히지 않게 잡은 거리다.

### §7.2 시각 배제

| 배제 | 이유 |
|---|---|
| `energy_aura_vfx` | 성령을 빛무리·오라·파동 이펙트로 그리지 않는다. 그 시각 문법이 곧 뉴에이지 프레임이다 |
| `glossolalia_depiction` | 방언·예언·치유 현상 연출 배제 (합신 은사중지론) |

### §7.3 회복 키 5종

| `recovery_key` | 캐시 키 |
|---|---|
| ANXIOUS | `jesus.recovery.gethsemane_trembling` |
| LONELY | `jesus.recovery.i_am_with_you` |
| EXHAUSTED | `jesus.recovery.living_water` |
| SAD | `jesus.recovery.he_wept` |
| CONFUSED | `jesus.recovery.the_way` |

`recovery_key: null` 폴백은 LONELY 로 간다. 임의 선택이 아니라 이 Scene 의 본문이
'홀로 두지 않겠다'(요 14:18)이기 때문이다. 폴백 응답도 그 구절을 인용한다.
게이트 G1b 가 마지막 Scene 의 null 라우트 존재를 본다.

40초 미선택 시 자동 종료. **고르지 않아도 끝난다** —
마지막 Scene 에서 선택을 요구하면 그것은 완주 조건이 된다.

### §7.4 위기 리마인더

```yaml
crisis_reminder:
  persistent: true
  dismissible: true
  text_ko: "{{crisis_resources.default}}."
```

게이트 G3c(마지막 Scene 에 위기 토큰 값)·G3d(리터럴 키 `crisis_reminder`)가
둘 다 이것을 본다. 번호는 어디에도 박히지 않는다.

### §7.5 종료

`no_autoplay: true`. 다음 콘텐츠를 자동 재생하지 않는다 —
다음으로 밀어 넣는 종료는 회복이 아니라 체류시간이다.
`no_completion_reward` 게이트가 배지·점수·연속 기록 부재를 `structural_check` 로 본다.

### §7.6 축 T1

8종. 런타임의 `spirit_framing_note` 자체가 '뉴에이지 에너지/차크라 프레임 금지' 를
명시한다 — "우주의 에너지"·"긍정의 파동" 이 실제로 나올 자리가 여기다.

### §7.7 reviewer_only

`spirit_framing_note` — T1 8종이 **어휘는 막지만 프레임은 못 막는다.**
금지어 없이도 성령을 개인의 내적 에너지로 그릴 수 있다. 그 판정은 사람 검토다.
`visual_exclusions` 두 항목이 시각 문법 쪽 절반을 구조로 막는 것이 현재 가진 전부다.

---

## §8 안전 축 배치 — 왜 전 Scene 이 아닌가

### §8.1 배치

| 축 | 종수 | Scene | 근거 |
|---|---|---|---|
| T1 이단 신학 | 8 | 1, 7 | 성육신(영지주의가 부정하는 지점) · 성령(뉴에이지 프레임 진입로) |
| R2 고난 가스라이팅 | 13 | 5 | 고난을 정면으로 다루는 유일한 Scene |
| R3 부활 압박 | 55 | 6 | 런타임이 선언한 유일한 축, 위반 자리는 부활 Scene 하나 |

합집합 76종(G5a-i). Scene 2·3·4 는 축이 없다.

**축을 안 단 것이 안전 주장이 아니다.** 그 축의 위반이 일어날 자리가 아니라는
판단이다. 같은 목록을 일곱 파일에 복사하면 게이트 초록이 넷 늘고 집행은 그대로다.

### §8.2 R3 55종의 사거리 — 측정된 우회 3건

55종은 **어간 절단형** 이다. 런타임 머리말이 측정해 기록한 우회가 셋 있다.
문서에 예문을 남기는 것은 그것이 초록의 한계를 보여 주기 때문이다.

1. **"이제 당신의 차례입니다"** — 목록의
   <!-- lint_forbidden_tokens 인용 --> '부활할 차례'·'살아날 차례'·'일어날 차례'
   와 어간이 달라 통과한다. 부활 어휘가 없는데 부활 압박이다.
2. **"부활의 소망으로 이 우울을 이겨 내세요"** — 목록의
   <!-- lint_forbidden_tokens 인용 --> '부활의 소망으로 이겨'
   사이에 조사구가 끼어 비껴간다. 토큰 두 글자 거리다.
3. **"금요일이 지나면 당신의 주일도 옵니다"** — 부활 어휘를 하나도 쓰지 않는 유비.
   표층형 목록으로는 원리적으로 못 잡는다.

같은 이유로 과차단도 측정돼 있다. 그중 하나는 **이 미션 자신의 R3 정본 문장** 이다
(§6.3). 어간 절단은 과차단과 미차단을 동시에 만들고, 그 둘을 다 적어 두는 것이
목록을 신뢰 가능한 상태로 두는 유일한 방법이다.

### §8.3 T1 8종이 덮지 않는 것

런타임 머리말의 배제 목록은 9항목이다 — 외경·영지주의·뉴에이지·타종교 혼합·
아르미니우스주의·펜테코스탈 은사지속·개방신론·번영복음·그 파생.
토큰으로 옮겨진 것은 그중 **영지주의·뉴에이지 계열뿐** 이다.

- 은사지속론·개방신론·번영복음 → 토큰 0종.
- 그리스도론 이단(가현설·양자론·아리우스) → 토큰 0종. Scene 1 에서 가장 위험한 것이
  그 게이트의 사거리 밖에 있다.

파일의 `known_gap` 에 그대로 적었다. T1 초록은
'배제 목록 전부를 막았다' 가 아니라 '두 계열의 표층형 8개가 없다' 이다.

### §8.4 집행 수단이 없는 게이트 6건

`enforcement: reviewer_only` — `docetism_visual_guard`(1) ·
`beatitude_not_a_task`(2) · `no_instant_healing_promise`(3) ·
`exclusivity_not_weaponized`(4) · `recognition_not_user_task`(6) ·
`spirit_framing_note`(7).

이 여섯에 `enforcement: structural` + 아무 테스트 이름을 적어 넣으면
게이트 래칫과 Kotlin 래칫을 **둘 다 통과한다.** 그리고 그 테스트는 이 파일들에 대해
0 토큰을 돌린다 — 공집합에 대한 전칭명제를 초록으로 인쇄하는 것이고,
이 리포에서 세 번 반복된 사고 유형이다(job.yml R3 산문 전용 2026-08-05,
jesus.yml R3 의 625 토큰 중 '부활' 0건 2026-08-11 등).

집행 수단이 없으면 **없다고 적는다.** 그 대신 각 게이트에
*현재 근거로 남길 수 있는 사실* 을 함께 적었다(예: 2인칭 약속 0건).

반대로 **기계가 실제로 재는** 게이트 4건은 `enforcement: structural` 로 적고
`verified_by` 로 그 검사를 지목했다 — `R4_pre_scene0_consent_required` ·
`passion_visual_exclusion` · `suffering_footer_required`(Scene 5) ·
`no_completion_reward`(Scene 7).

지목 대상은 이번에 새로 쓴 Kotlin 테스트다.

```
ContentSafetyGateEnforcementTest
  structural 선언은 실제 파일 구조로 뒷받침된다 — jesus 트리거·푸터·배제·무보상
```

이 테스트는 네 게이트의 `structural_check` 산문과 **같은 내용을 파일에서 다시 읽어**
확인한다: 동의 카드 id 가 비어 있지 않고 거절 목적지가 6인가 / `visual_exclusions` 가
비어 있지 않고 전부 `excluded: true` 인가(빈 목록에 대한 전칭명제 방지) /
고난 푸터가 `persistent_during_scene: true` 인가 / 마지막 Scene 인터랙션이 전부
`no_completion_reward: true` 이고 점수·정답 키가 서브트리에 없는가.

**돌연변이로 확인했다.** `scene7.yml` 의 `no_completion_reward` 를 `false` 로 바꾸면
이 테스트가 빨간불이 되고(9건 중 1건 실패), 되돌리면 초록으로 돌아온다.
지목만 하고 실제로는 아무것도 재지 않는 `verified_by` 를 막는 것이
이 리포의 매달린-참조 래칫이므로, 그 래칫이 요구하는 만큼은 실제로 돌아간다는 것을
문서에 적을 근거를 이렇게 만들었다.

### §8.5 초록이 주장하는 범위

`newchar_gates.py` 가 스스로 인쇄하는 문장 그대로다.

> PASS 의 주장 범위: '선언된 토큰의 정확한 표층형이 대상에 없다' 까지다.
> 'R2/R3 위반이 없다' 가 아니다.

그리고 `track_b_readiness.py` 가 스스로 인쇄하는 문장도 같이 적어 둔다.

> 전부 O 라도 그것은 '놀 수 있다' 이지 '내보내도 된다' 가 아니다.

---

## §9 안전 시뮬레이션

기계가 아니라 손으로 돌린 시나리오다. 결과는 **설계 의도의 확인**이지 실측 로그가 아니다.

| # | 상황 | 설계상 동작 | 근거 파일 |
|---|---|---|---|
| 1 | Scene 5 동의 카드에서 거절 | Scene 6 으로 이동, 미션 계속 | scene5 `skip_alternative_scene_id: 6` |
| 2 | Scene 5 침묵 중 자해 발화 | 페이드 블랙 + 위기 카드 | scene5 `R1_voice_self_harm_listener` |
| 3 | Scene 4 에서 아무것도 안 고름 | 50초 뒤 '길' 폴백, 되묻지 않음 | scene4 `iam_key: null` 라우트 |
| 4 | Scene 7 에서 아무것도 안 고름 | 40초 뒤 LONELY 폴백 + 요 14:18 | scene7 `recovery_key: null` 라우트 |
| 5 | AI opt-out 사용자 | 9종·5종 캐시로 동일 완결 | scene4·7 `default_path: static_curation` |
| 6 | Scene 3 에서 손을 못 뻗음 | 45초 뒤 자동 진행, 실패 상태 없음 | scene3 `no_failure_state: true` |
| 7 | 배포 지역 변경 | 위기 번호가 토큰에서 재해석 | 전 Scene `{{crisis_resources.default}}` |

시뮬레이션 2·7 은 런타임 코드 경로에 의존하므로 저작 계층만으로는 **검증되지 않았다.**
`ScenarioHotlineRatchetTest` 와 R1 리스너 구현이 그 검증 지점이다.

---

## §10 측정 (2026-08-22)

```
python3 scripts/newchar_gates.py --character jesus
--- PASS 22 / FAIL 0 / BLOCKED 7 ---
```

### §10.1 초기 FAIL 4건과 처리

| 게이트 | 내용 | 처리 |
|---|---|---|
| G3 | `docs/MVP-JESUS.md` 2줄에 위기 번호 리터럴 | `{{crisis_resources.default}}` 로 치환 |
| G5a-ii | 축 3건이 `enforcement: structural` 인데 `structural_check` 없음 | 토큰 목록이 곧 집행 수단이므로 `enforcement` 선언 제거 |
| Kotlin ratchet ① | `scene6.yml` 주석이 `R2` 를 언급하는데 그 파일에 R2 게이트 없음 | 주석에서 축 문자열 제거 (R2 는 Scene 5 담당) |
| Kotlin ratchet ② | structural 게이트 4건이 `verified_by` 없이 산문만 보유 | 실제 검사 테스트를 새로 쓰고 지목 (§8.4) |
| G5t | 콘텐츠에 T1 이 생겼는데 설정은 `theology_axis: exempt` | 면제 블록 삭제 (그 면제가 스스로 적어 둔 해소 조건) |
| G6 | scene6 의 `known_misses` 가 렌더 leaf 에서 금지 토큰을 인용 | 예문을 이 문서 §8.2 로 이관, 인용 마커 부착 |

G6 는 **정당한 FAIL 이었다.** 우회 예문을 저작 파일에 적으면 그 파일이 금지 토큰을
품게 되고, 스캐너는 의도를 읽지 않는다. 문서 쪽에는 인용 마커 규율(G6d)이 있으므로
예문은 문서에 둔다.

### §10.2 BLOCKED 7 내역

**BLOCKED 는 PASS 가 아니다 — 판정 불가다.**

| 게이트 | 왜 판정 불가인가 |
|---|---|
| G0b | 설정 `exclusions` 미정의 — 배제 목록 없이 0건은 무의미 |
| G0e | 같음 |
| G9 | 같음 |
| G9d | 같음 |
| G0c | 설정 `token_examples` 0개 — 토큰:예문 대조 불가 |
| G0d | 설정 `polite_evasive` 0개 — 제품 말투 위험 문장 커버리지 불가 |
| G2v | `latch_contract` 미선언 — 래치 키의 *값* 을 재지 않았다 |

해소는 `scripts/gates/jesus.yml` 에 네 항목을 채우는 일이고 이 커밋의 범위가 아니다.
게이트를 지우면 BLOCKED 가 사라지지만 그것은 판정 불가를 통과로 바꾸는 게 아니라
**판정 자체를 없애는 것이다.**

### §10.3 다른 측정

```
python3 scripts/track_b_readiness.py
--- PASS 14 / FAIL 0 / BLOCKED 0 ---     # 인물 14 · 단계 역전 0

cd backend && gradle test --tests '*safety*'
87 tests completed, 0 failed             # 기존 86 + 이번 신규 1

python3 scripts/ci_gates.py
--- 일치 22 / 드리프트 0 / 판정불가 0 ---   # 드리프트 3건은 전부 개선이라 --update 로 기록
```

`ci_gates.py` 가 스스로 인쇄하는 단서도 그대로 옮긴다 — 이 초록은
'게이트가 통과했다' 가 아니라 **'기록해 둔 판정 결과에서 벗어나지 않았다'** 까지다.
러너 22개 중 14개는 지금도 rc≠0 이다.

`./gradlew` 는 이 리포에 없다. 시스템 `gradle` 을 `backend/` 에서 돌린다.

---

## §11 체크리스트

기계가 잰 것과 사람이 재야 하는 것을 섞지 않는다.

### §11.1 기계

- [x] 7 Scene 파일 존재 (G0a)
- [x] YAML 파싱 (G4)
- [x] 마지막 Scene null 폴백 라우트 (G1b)
- [x] 트리거 Scene 동의 카드 + 스킵 목적지 (G7)
- [x] 마지막 Scene 위기 토큰 값 + 리터럴 키 (G3c·G3d)
- [x] 위기 자원 하드코딩 0건 (G3, 8파일)
- [x] 축 4건 전부 집행 수단 보유 (G5a-ii)
- [x] 축 합집합 76종 전부 런타임 목록에 존재 (G5e)
- [x] 렌더 leaf 금지 문구 0건 (G6)
- [x] 문서 인용 마커 규율 (G6d)
- [x] `llm_optin_only` **값** 검사 (G8, 2개 분기 Scene)
- [x] 설정의 신학 축 면제 해소 (G5t)
- [x] structural 게이트 4건이 실재하는 테스트를 지목 (Kotlin 매달린-참조 래칫)
- [x] 단계 역전 0 (`track_b_readiness.py`, 인물 14)

### §11.2 사람

- [ ] `lemuel-theology-reviewer` 7 Scene 사후검토
- [ ] `lemuel-mental-health-safety` 사후검토
- [ ] `theology_footer_refs` 의 `*_candidate` 서지 7건 확정
- [ ] reviewer_only 6건 실제 검토 (§8.4)
- [ ] Scene 1 클로즈업 회피가 연출로 충분한지 판단
- [ ] 오디오 클립 실제 녹음/합성

---

## §12 다음 단계

1. `scripts/gates/jesus.yml` 에 `exclusions` · `token_examples` · `polite_evasive` ·
   `latch_contract` 를 채워 BLOCKED 7 해소.
2. T1 사거리 확장 — 그리스도론 이단·번영복음의 표층형을 축 토큰으로 세울지,
   아니면 표층형으로는 못 잡는다고 명시하고 사람 검토로 남길지 결정.
   **어느 쪽이든 결정을 적는다.** 지금은 `known_gap` 에 적혀 있을 뿐이다.
3. R3 우회 3건(§8.2)에 대한 대응 결정. 표층형 목록을 늘리는 것은 3번을 못 잡고,
   못 잡는다는 사실을 적어 두는 것이 현재 상태다.
4. 오디오·서지.

---

*저작 계층 초안. 사용자 노출 문장은 전부 `scenarios/jesus.yml` 인용이며
`source_of_record:` 로 출처 키를 표기했다. 신학·임상 사후검토 전이다.*
