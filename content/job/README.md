# content/job — 욥 미션 Scene 정의 (Theme 5 · 답 없는 고난과 비탄의 허용)

> ⚠️ **초안 고지** — **런타임 대본을 근거로 back-fill 한 저작 계층이다.**
> 사용자 노출 문장은 전부 `backend/src/main/resources/scenarios/job.yml` 의
> `static_text` · `options[].response` · `reflection_prompt` · `value_prompt` ·
> `suffering_footer` · `crisis_reminder` 원문이며, 각 블록에 `source_of_record` 로
> 출처를 적어 두었다. **없던 대본을 지어낸 것이 아니다.**
> 새로 쓴 것은 XR 무대(environment · npcs · interactions · transition)뿐이다.
>
> **`lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필요** —
> 특히 Scene 2(출생 저주·죽음 소원)는 인간 사인오프 권고.

## 왜 이 디렉터리가 뒤늦게 생겼나

`scripts/track_b_readiness.py` 가 **단계 역전**(runtime_content 있음 / authoring 없음)으로
욥과 예수를 잡고 있었다. 런타임에는 이미 대본이 나가는데 저작 계층이 없는 상태였다.
역전을 없애는 방향은 둘인데 — 런타임을 내리거나, 저작을 채우거나 — 이미 나가고 있는 것을
문서화하는 쪽을 택했다.

## 파일

| 파일 | Scene | 본문 | 민감도 |
|---|---|---|---|
| `scene1.yml` | 침묵의 7일 — 함께 앉아 있기 | 욥 2:13 | 낮음 (도입, 5초 후 skip 가능) |
| `scene2.yml` | 태어난 날을 저주하다 | 욥 3:3 (+ 3:11, 6:8) | **mid** — 진입 전 consent_card + Scene 3 직행 skip |
| `scene3.yml` | 친구들의 위로 — 정답이 아닌 것 ★ 핵심 인터랙션 | 욥 4:7 (+ 42:7) | 낮음 (선택 비강제, 40초 후 자동 진행) |
| `scene4.yml` | 폭풍 가운데서 하나님의 응답 | 욥 38:1 | 낮음 (폭풍 강도 토글 기본 '약') |
| `scene5.yml` | 회복 — 그러나 답 없이 | 욥 42:5 | 낮음 (완주 뱃지·축하 연출 없음) |

## 스키마 정본

`content/solomon/scene*.yml`(ruleset `newchar-v5`, 5 Scene) 컨벤션을 미러했다 —
consent_card / footer_persistent / `lint_forbidden_tokens` /
`R1_voice_self_harm_listener`(action `pause_fade_black_show_crisis_card`, enforced_at `always`) /
`default_path: static_curation` / null 폴백 라우트 / `theology_footer_refs` 의 disputed_points 구조.

## 안전 제약축

축을 **위반이 일어나는 자리에** 둔다. 전 Scene 에 같은 목록을 복사하면 초록만 늘고
집행은 늘지 않는다.

| 축 | 선언 위치 | 표층형 수 | 왜 거기인가 |
|---|---|---|---|
| R2 (고난 가스라이팅) | `scene3.yml`, `scene5.yml` | 13 | 욥의 친구들이 하는 말이 이 축의 교과서적 표본이고(욥 42:7 이 그것을 책망한다), 결말부 footer 도 같은 위험을 진다 |
| T1 (이단 신학) | `scene3.yml` | 8 | 고난의 원인을 당사자에게 귀책하는 번영신학 역방향이 친구들의 논리와 같은 자리에 있다 |
| R3 (회복 보장 금지) | `scene5.yml` | 35 | 욥 42:10 '갑절 회복' 을 결말로 쓰지 않는다는 결정이 여기서 지켜진다 |
| R1 (자해 청취) | 5/5 Scene | — | 상시. 게임 로직보다 우선 |
| R4 (사전 동의) | `scene1.yml`(Pre-Scene 0), `scene2.yml`(트리거) | — | Scene 2 는 skip 경로가 Scene 3 직행 |
| R5 (AI opt-out) | 5/5 Scene | — | 욥 미션은 LLM 분기 자체가 없다. 전 응답 사전 큐레이션 |

**토큰은 저작층에서 발명하지 않았다.** 56종 합집합 전부가 이미 런타임 전역 목록
(`backend/src/main/resources/application.yml` :: `safety.forbidden-tokens.list`, 688종)에
있는 표층형이다. 저작에만 있고 런타임 방어는 0인 상태를
`ContentSafetyGateEnforcementTest` 가 막는다.

### 초록이 주장하는 범위

**표층형 lint 다.** 게이트 PASS 는 「선언된 정확한 표층형이 대상 텍스트에 없다」까지이고
「R2/R3/T1 위반 0건」이 아니다. 유의어 재작성("이 시간이 당신을 빚고 있습니다",
"결국 다 제자리로 돌아옵니다")은 이 lint 로 막히지 않는다. 그리스도론 이단은 T1 8종의
범위 밖이다.

집행 수단이 없는 게이트는 그렇게 적어 두었다 —
`scene2.yml :: lament_not_romanticized` 와 `scene5.yml :: suffering_footer_required`
는 `enforcement: reviewer_only` 다. 연출(카메라 워크·음악 상승)과 문장 존재 요구는
토큰으로 표현되지 않는다. `enforcement: structural` 로 적고 아무 테스트 이름이나
붙이면 그 테스트는 이 파일들에 대해 공회전한다 — 그 형태를 여기서 재현하지 않는다.

## 결말 설계 — 42:5 에서 끊는다

본문 인용은 욥 42:5("이제는 눈으로 주를 뵈옵나이다")에서 멈춘다. 42:6(번역·해석 이견)과
42:10~17(재산·자녀 회복)은 무대에 올리지 않는다. 환경에서도 되찾은 재산을 그리지 않는다
(`scene5.yml :: environment.restored_wealth_visuals: excluded`).

이 배제는 **해석적 선택이며 본문에는 있다.** '성경에 없다' 가 아니라 '이 미션이 다루지
않는다' 이다 — 이 구분을 흐리지 않는다. 이유는 회복 보장 서사가 정신건강상 해로울 수
있다는 것이고, 런타임 `job.yml :: restoration_note` 가 같은 결정을 이미 적어 두었다.

## 게이트 실측 (2026-08-22)

`python3 scripts/newchar_gates.py --character job` → **PASS 22 / FAIL 0 / BLOCKED 7**.

BLOCKED 7 은 통과가 아니라 **판정 불가**다. 내역: `G0b`·`G0e`·`G9`·`G9d`(설정에
`exclusions` 미정의 — 배제 목록이 비어 있어 순회 0회) · `G0c`(`token_examples` 0개) ·
`G0d`(`polite_evasive` 0개) · `G2v`(`latch_contract` 미선언). 전부
`scripts/gates/job.yml` 에 선언이 없어서 못 재는 것이고, 콘텐츠가 통과한 것이 아니다.

## 관련 문서

- 런타임 대본(정본): `backend/src/main/resources/scenarios/job.yml`
- 설계: `docs/MVP-JOB.md`
- 게이트 설정: `scripts/gates/job.yml`

*AI 보조 — 본문은 성경 참조.*
