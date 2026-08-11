# RUTH-LOCKED-STRINGS — 룻(Theme 19) 고정 문자열 정본

> **이 파일이 정본이다.** `scripts/check_ruth_captions.py` 는 아래 데이터 블록을 _읽어서_
> `content/ruth/*.yml` 과 대조한다. 검사기 안에는 이 값들의 사본이 없다.

## 왜 이 파일이 생겼나 (2026-08-06)

`check_ruth_captions.py` 는 원래 이 값들을 자기 안에 하드코딩해 두고, 상류가 `SEED-RUTH.md`
라고 적어 두었다. 그런데 **그 파일은 저장소에 존재한 적이 없다** — 작업 트리에도, git 전체
이력에도 없다(`git log --all --diff-filter=A`). 즉 검사기가 자기 docstring 에서 경고하던
_자기참조 검사_ 가 실제 상태였다: 자막을 검사기와 콘텐츠 양쪽에서 같이 고치면 아무도 못 잡는다.

사용자 결정(2026-08-06)은 "상류 문서를 실제로 만들어" 였고, 이 파일이 그 상류다.

**이 파일은 없던 내용을 지어내지 않았다.** 값은 전부 `check_ruth_captions.py` 의 상수에서
기계적으로 덤프한 것이고, 그 상수들은 그 시점에 이미 `content/ruth/*.yml` 과 일치함이
게이트 초록(검사 12 / FAIL 0)으로 확인돼 있었다. 한 일은 _내용을 만든 것_ 이 아니라
**주인을 지정한 것** 이다. 사라진 `SEED-RUTH.md` 를 복원한 것도 아니다 — 이건 새 정본이다.

## 이 계약이 잡는 것과 못 잡는 것

- **잡는다** — 콘텐츠만 바뀐 경우. `content/ruth/*.yml` 의 자막·면책·카드가 이 파일과
  달라지면 검사기가 빨개진다.
- **잡는다** — 검사기만 바뀐 경우. 이제 검사기에 문자열이 없으므로 애초에 바꿀 것이 없고,
  이 파일을 못 읽으면 검사기는 판정을 내지 않고 rc 126 으로 죽는다(초록으로 새지 않는다).
- **못 잡는다** — 이 파일과 콘텐츠를 _같이_ 고치는 경우. 그건 제3자가 없으면 원리적으로
  불가능하다. 다만 그때는 정본 파일의 diff 가 남으므로 _검토 가능한 행위_ 가 된다.
  이전 상태(검사기+콘텐츠 동시 수정)와의 차이가 정확히 이것이다.

## 값

절 번호(`룻 2:12` 등)의 자구 정본은 여기가 아니라 `docs/VERSES-RUTH-GAE.md` 다.
성구 자구 검사(AC 30·14)는 그 파일을 읽어 대조하므로 제3자 대조가 살아 있다.

```yaml
closing_caption:
  # 2026-08-11 값 변경 — 주어를 되살린다. 앞 값은 2:12b 에서 주어까지 잘라낸
  #   「그의 날개 아래에 보호를 받으러 온 네게」였다. 그 자름의 대가가 둘이었다:
  #   ① 「그의」가 가리킬 말이 화면에 없다(선행사 부재).
  #   ② 미션의 마지막 문장에서 유일하게 남은 행위자가 **사용자**였다.
  #   b 전문은 주어가 「이스라엘의 하나님 여호와께서」다. 사용자 결정(2026-08-11).
  # ⚠️ 이 값은 `content/ruth/scene3.yml` 의 2:12 자막과 **글자까지 같아졌다.**
  #   앞 판이 「같지 않고 메아리 관계다」라고 적었던 그 계약은 이 변경으로 깨진다.
  #   깨진 채로 두는 것이 결정이다 — 되돌리려면 주어를 다시 잘라야 하기 때문이다.
  #   자름과 구별 중 자름을 버렸다. 집행하는 검사기는 없었다(산문 계약이었다).
  text_ko: 이스라엘의 하나님 여호와께서 그의 날개 아래에 보호를 받으러 온 네게
  verse_ref: 룻 2:12 중
closing_lines:
  stay_beside: 곁에 남은 자리에도 그늘이 닿았다.
  step_back: 한 걸음 물러선 자리에도 그늘이 닿았다.
  default: 이 자리에도 그늘이 닿았다.
disclaimer:
  text_ko: |-
    이 이야기는 고통에 이유를 붙이지 않습니다.
    성경 본문을 그대로 옮길 뿐, 지금 겪고 계신 일을 설명하지 않습니다.
    지금 계신 곳이 안전하지 않다면, 그곳에 머무는 것이 이 이야기의 교훈이 아닙니다. 안전이 우선입니다.
    지금 힘드시면: {{crisis_resources.default}}
  position: top_right
  style: small_persistent
ruth_2_11:
  short_text_ko: 네가 시어머니에게 행한 모든 것과 네 부모와 고국을 떠나 전에 알지 못하던 백성에게로 온 일이 내게 분명히 알려졌느니라
  forbidden_fragment: 네 남편이 죽은 후로
# 2026-08-11 값 변경 — `suppress_all_narrative_captions` → 아래 값.
#   부정형(「서사 자막이라는 *분류* 를 끈다」)은 분류가 하나라도 새로 생기면 조용히
#   샌다. 실제로 이 리포의 화자 표기는 20종이 넘고(`scripture_caption` 57건 ·
#   `narrator` 35건 · `system` 24건 …), 그중 `narrative` 라는 값은 **한 건도 없다** —
#   즉 부정형 문자열은 자기가 끄겠다고 적은 분류를 리포에서 가리키지도 못했다.
#   긍정형(「자막류는 전부 끄고 위기 카드만 남긴다」)은 분류가 늘어도 기본이 「끔」이다.
#   ⚠️ 주장 범위는 **자막류**까지다. `disclaimer`(`style: small_persistent`)가
#   `pause_fade_black` 이후에도 떠 있는지는 어디에도 적혀 있지 않다 — 미해소.
#   ⚠️ 그리고 이 세 키를 읽는 런타임 코드는 지금 **0건**이다(`.kt`·`.tsx` 전수 0).
#   문자열을 고쳐도 화면 동작은 달라지지 않는다. 바뀐 것은 계약 문언이고,
#   집행하는 것은 AC 23(정본 ↔ 5개 Scene 값 대조)까지다.
crisis_latch:
  post_crisis_render_policy: suppress_all_captions_except_crisis_card
  post_crisis_latch_scope: mission
  crisis_card_position: terminal_screen
consent_cards:
  ruth_entry_consent:
    covers:
      - 1
      - 2
    declined_route: 3
    text_ko: |-
      이 이야기에는 사별한 사람들이 나옵니다.
      남편을 잃은 두 여인과, 남편과 두 아들을 모두 잃은 한 여인입니다.
      굶주림과, 낯선 땅에서 이방인으로 불리는 일이 나옵니다.
      마지막은 성문에서 사람들이 축복하는 장면입니다. 한 아이가 태어나지만 그 장면은 화면에 나오지 않습니다.
      · 중간에 한 여인이 밤에 홀로 한 남자 곁으로 가는 장면이 있습니다. 본문은 어떤 성적인 일도 서술하지 않으며, 이 장면은 그런 일을 재연하지 않습니다. 그 장면 직전에 다시 여쭙습니다.
      · 전체 5-7분입니다. 어느 지점에서든 멈추거나 나갈 수 있습니다.
      · 지금 보지 않으셔도 이야기는 온전히 끝맺어집니다.
      계속하시겠어요?
      [계속한다]  [사별 장면은 건너뛴다]
      음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
      지금 힘드시면: {{crisis_resources.default}}
  ruth_midpoint_consent:
    covers:
      - 3
      - 5
    declined_route: closing
    text_ko: |-
      여기서부터는 낯선 땅에서의 일입니다.
      한 여인이 남의 밭에서 이삭을 줍고, 사람들은 그를 "모압 여인"이라고 부릅니다.
      그 여인은 남편을 잃은 사람이고, 사람들이 그 일을 입에 올립니다.
      이야기 끝에 한 아이가 태어나지만, 출산 장면도 아기도 화면에 나오지 않습니다.
      · 약 3분입니다. 여기서 마치셔도 이야기는 온전히 끝맺어집니다.
      계속하시겠어요?
      [계속한다]  [여기서 마친다]
      음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
      지금 힘드시면: {{crisis_resources.default}}
  ruth_scene4_night_warning:
    covers:
      - 4
    declined_route: 5
    text_ko: |-
      다음 장면은 밤의 타작 마당입니다.
      본문은 어떤 성적인 일도 서술하지 않으며, 이 장면은 그런 일을 재연하지 않습니다.
      지금 계신 곳이 안전하지 않다면, 그곳에 머무는 것이 이 이야기의 교훈이 아닙니다. 안전이 우선입니다.
      한 여인이 자기보다 힘 있는 사람 곁에서 밤을 보내고, 아침 전에 떠납니다.
      · 약 1분입니다. 건너뛰셔도 이야기는 이어집니다.
      계속하시겠어요?
      [계속한다]  [건너뛰기 — 성문 장면으로 이동]
      음성/자막 강도: [ 자막만 ] [ 약 ] [ 기본 ]
      지금 힘드시면: {{crisis_resources.default}}
skip_destinations:
  "1": 3
  "2": 3
  "3": 4
  "4": 5
  "5": ruth_scene5_alt_short
f66_entry_gate:
  id: F66_entry_state_gate
  trigger_conditions:
    - source: safety_alerts
      rule: severity='high' 이력 존재 (user_id 기준, category 무관)
    - source: emotion_logs
      rule: 최근 3일 연속 감정강도 9+ (F-6.2 임계값 공유)
  on_trigger:
    closing_line_force: null_variant
    voice_intensity_preset: subtitle_only
    scene4_skip_preoffered: true
    skip_button_visual_emphasis: true
exclusions:
  total: 20
  by_scope:
    verse_text: 16
    content_leaf: 4
```

## 항목별 메모

- `closing_caption` — 룻 2:12 의 부분 인용이다. 전문이 아니라 이 조각만 쓴다.
- `closing_lines` — `belonging_label` 별 3종. **집합 크기 3이 요점이다**(AC 10). 3줄이 그대로
  있는 채 변형이 더 생기면 평가 문장이 된다. `faith_tone` 은 여기에 적용되지 않는다.
- `disclaimer` — 5개 Scene 전부 + 종결 화면까지 6곳에 같은 문안이 붙는다(AC 11·32).
  셋째 줄("지금 계신 곳이 안전하지 않다면…")이 F-6.5(피해 상황 부인)를 집행한다.
- `ruth_2_11` — 축약본만 렌더한다. `forbidden_fragment` 는 `content/ruth` 어디에도 있으면
  안 된다(AC 28).
- `skip_destinations` — 5개 Scene 전부에 키가 있어야 한다(G7). 키가 3개뿐인 판으로 줄이면
  G7 과 동시에 만족할 수 없다. 자세한 근거는 `scripts/check_ruth_captions.py` 의
  `SKIP_DESTINATIONS` 주석에 남아 있다.
- `f66_entry_gate.trigger_conditions` — **항목 수 2가 요점이다**(AC 29). 둘째 항목(F-6.2 축)이
  빠지는 것이 알려진 오독 방향이다.
- `exclusions` — 개수와 scope 구성을 잰다(AC 31). 선언되지 않은 배제는 어떤 게이트의
  대상도 아니므로, 빠진 배제는 다른 게이트가 못 잡는다.
