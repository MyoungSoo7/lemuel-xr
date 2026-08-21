# content/jesus — 예수 미션 저작 계층

## 왜 뒤늦게 채우는가

`track_b_readiness.py` 가 **단계 역전(stage inversion)** 을 잡았다. 예수 미션은
`docs/MVP-JESUS.md`(런타임 대본 설명)와 `backend/src/main/resources/scenarios/jesus.yml`
(실행되는 대본)은 있는데 그 앞 단계인 `content/jesus/`(무대 저작)이 없었다.
나중 단계가 있는데 앞 단계가 없는 상태다 — 이 디렉터리가 그 역전을 없앤다.

역전을 없앤다는 것은 **없던 대본을 지어냈다는 뜻이 아니다.** 사용자에게 노출되는
문장은 전부 `scenarios/jesus.yml` 에서 **그대로 옮겨 적었고**, 각 블록에
`source_of_record:` 로 어느 키에서 왔는지 적어 두었다. 새로 쓴 것은 XR 무대뿐이다 —
공간·조명·NPC 배치·인터랙션 형태·전환.

## 파일

| 파일 | Scene | 인터랙션 | 초 | 선언 축 |
|---|---|---|---|---|
| `scene1.yml` | 성육신 | static_monologue (cinematic) | 60 | **T1** |
| `scene2.yml` | 팔복 | scripture_reading | 90 | 없음 |
| `scene3.yml` | 만짐·병자를 고치심 | gesture_sequence | 90 | 없음 |
| `scene4.yml` | 길·진리·생명 | pick_one ★ | 120 | 없음 |
| `scene5.yml` | 겟세마네와 십자가 ★트리거 | contemplative | 120 | **R2** |
| `scene6.yml` | 부활·빈 무덤 | scripture_reading | 90 | **R3** |
| `scene7.yml` | 승천과 생명의 강 ★마지막 | outro | 60 | **T1** |

## 축은 위반이 일어나는 자리에만 둔다

| 축 | 종수 | 배치 | 왜 거기인가 |
|---|---|---|---|
| T1 이단 신학 금지 | 8 | Scene 1, 7 | 성육신은 영지주의가 부정하는 바로 그 지점이고, 성령·생명의 강은 뉴에이지 에너지 프레임이 실제로 들어올 자리다. 런타임의 `spirit_framing_note` 가 그것을 이미 금지하고 있다. |
| R2 고난 가스라이팅 금지 | 13 | Scene 5 | 고난을 정면으로 다루는 유일한 Scene. "당신의 고난에도 뜻이 있다" 가 나온다면 정확히 여기다. |
| R3 부활 압박 금지 | 55 | Scene 6 | 런타임이 선언한 유일한 축이고, 위반 자리는 부활 Scene 하나다. |

Scene 2·3·4 는 축을 선언하지 않는다. 같은 토큰 목록을 일곱 파일에 복사하면
**초록만 늘고 집행은 늘지 않는다.** 축이 없다는 것은 그 Scene 이 안전하다는 주장이
아니라, 그 축의 위반이 일어날 자리가 아니라는 판단이다.

축을 안 단 Scene 에는 주석에도 `R2`/`R3`/`T1` 문자열을 쓰지 않는다 —
Kotlin 산문 전용 래칫이 `#` 주석의 축 언급을 같은 파일의 집행 게이트와 대조한다.

## 초록이 주장하는 범위

`newchar_gates.py --character jesus` 의 PASS 는 **"선언된 토큰의 정확한 표층형이
대상에 없다"** 까지다. 다음은 주장하지 않는다.

- **R3 55종은 어간 절단형이다.** 런타임 머리말이 측정한 우회 3건이 실재한다
  (문서 `docs/MVP-JESUS-CONTENT.md` §6.2). 조사 하나만 끼워도 비껴간다.
- **T1 8종은 배제 목록 9항목 중 두 계열만 덮는다.** 은사지속론·개방신론·번영복음은
  토큰이 없다. 그리스도론 이단(가현설·양자론·아리우스)도 8종에 **없다** —
  Scene 1 에서 가장 위험한 것이 정작 그 게이트의 사거리 밖에 있다.
- **프레임은 못 막는다.** 금지어 없이도 성령을 개인의 내적 에너지로 그릴 수 있다.

## 집행 수단이 없는 게이트는 그렇게 적는다

`enforcement: reviewer_only` 로 선언한 게이트가 5건 있다.

| 게이트 | 파일 | 왜 기계가 못 재는가 |
|---|---|---|
| `docetism_visual_guard` | scene1 | 시각 연출의 가현설 함의는 토큰이 아니다 |
| `beatitude_not_a_task` | scene2 | 팔복을 과제로 읽히게 하는 어조에 표층형이 없다 |
| `no_instant_healing_promise` | scene3 | "나아질 것" 의 표층형이 무한하다 |
| `exclusivity_not_weaponized` | scene4 | 정죄 어조를 판정하는 검사가 이 리포에 없다 |
| `recognition_not_user_task` | scene6 | 같은 이유 |
| `spirit_framing_note` | scene7 | 어휘가 아니라 프레임이다 |

이 게이트들에 `enforcement: structural` + 아무 테스트 이름을 적어 넣으면
래칫 두 개를 다 통과한다. 그리고 그 테스트는 이 파일들에 대해 **0 토큰을 돌린다** —
이 리포에서 세 번 반복된 사고 유형(공집합에 대한 전칭명제를 초록으로 인쇄)이다.
집행 수단이 없으면 없다고 적는 쪽을 택했다.

반대로 구조 검사가 **실재하는** 게이트 4건(`R4_pre_scene0_consent_required` ·
`passion_visual_exclusion` · `suffering_footer_required` · `no_completion_reward`)은
`enforcement: structural` 로 적고 `verified_by` 로 Kotlin 테스트
`structural 선언은 실제 파일 구조로 뒷받침된다 — jesus 트리거·푸터·배제·무보상`
을 지목했다. 그 테스트는 이번에 같이 썼고, `no_completion_reward` 를 `false` 로
바꾸면 실제로 빨간불이 되는 것까지 확인했다.

`suffering_footer_required` 에 축 접두를 달지 않은 것은 별개 이유다 —
축 접두를 달면 토큰 목록을 요구받아 같은 lint 를 두 번 선언하게 된다.

## 손대면 안 되는 한 곳

`scene6.yml` 의 `nb_s6_reflection` — "당신이 스스로 **부활해 내야** 하는 과제가 아니라".
반말 어간이라 통과한다. 존댓말로 다듬으면 `부활해 내셔야 합` 이 되어 **이 미션 자신의
55종 목록에 자기 대본이 걸린다.** 어간 절단으로 토큰을 만든 대가이고, 런타임 머리말이
이 한 음절 차이를 이미 측정해 기록해 두었다. 파일에 `verbatim_locked: true` 로 표시했다.

## 측정 (2026-08-22)

```
python3 scripts/newchar_gates.py --character jesus
--- PASS 22 / FAIL 0 / BLOCKED 7 ---
```

BLOCKED 7 은 통과가 아니라 **판정 불가**다. 내역:

| 게이트 | 왜 판정 불가인가 |
|---|---|
| G0b · G0e · G9 · G9d | 설정 `exclusions` 미정의 — 배제 목록이 없어 0건이 무의미 |
| G0c | 설정 `token_examples` 0개 — 토큰:예문 대조 불가 |
| G0d | 설정 `polite_evasive` 0개 — 제품 말투 위험 문장 커버리지 불가 |
| G2v | `latch_contract` 미선언 — 래치 키의 *값* 을 재지 않았다 |

해소는 `scripts/gates/jesus.yml` 에 그 네 항목을 채우는 일이고 이 커밋의 범위가 아니다.
채우지 않은 채 BLOCKED 를 지우는 방법은 하나뿐인데(게이트를 삭제하는 것) 그것은
판정 불가를 통과로 바꾸는 게 아니라 판정 자체를 없애는 것이다.

## 남은 일

- [ ] `lemuel-theology-reviewer` 사후검토 (7 Scene 전부, 초안 상태)
- [ ] `lemuel-mental-health-safety` 사후검토
- [ ] `theology_footer_refs` 의 `*_candidate` 서지 확정 (7건 전부 미확보)
- [ ] `scripts/gates/jesus.yml` 의 `exclusions` · `token_examples` · `polite_evasive` ·
      `latch_contract` 를 채워 BLOCKED 7 해소
- [ ] 오디오 클립 `aud_s{1..7}_*_ko` 실제 녹음/합성
