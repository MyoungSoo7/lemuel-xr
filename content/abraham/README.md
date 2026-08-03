# content/abraham — 아브라함 미션 Scene 정의 (Theme 17 · 지연된 약속·불확실성 인내)

> ⚠️ **초안 고지** — 수기 seed(`seed-ABRAHAM.md`, v5-b) 기반 ≤70% 초안.
> **`lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필요** — 특히 Scene 3(불임·상속자 없음 고백,
> R1 인접)·Scene 4(창세기 18:14 처리, 이 미션 단일 최대 R2 위험)·Scene 5(노년 출산, R4 동의 게이트)는 인간 사인오프 권고.
>
> **제품 안전선**: 이삭을 제물로 드리라는 시험 사건(창세기 21장 직후에 이어지는 장, 신약 재인용 포함)은 이 미션 전체에서
> 완전히 배제한다 — 아동 위해 이미지, 제품 안전선 판단(신학 판단 아님). 여종 서사(창 16, 창 21:8-21)·아내를 누이라 속인
> 사건(창 12:10-20)도 본편에 쓰지 않는다.

## 파일

| 파일         | Scene                                                | 본문                            | 민감도                                                                        |
| ------------ | ---------------------------------------------------- | ------------------------------- | ----------------------------------------------------------------------------- |
| `scene1.yml` | 떠남 — 부르심과 순종                                 | 창 12:1-2, 12:4                 | 낮음                                                                          |
| `scene2.yml` | 장막 — 반복되는 이동 (본문 인용 없는 유일한 Scene)   | 없음(정황 요약)                 | 낮음                                                                          |
| `scene3.yml` | 별, 그리고 사백 년 ★ 핵심 인터랙션 #1(뭇별 헤아리기) | 창 15:2-3, 15:5-6, 15:13, 15:16 | **mid** — 상속자 없음 고백 + 사백 년 압제 예고, dwell_limit + crisis_reminder |
| `scene4.yml` | 웃음 — 두 웃음의 비대칭, 18:14 특수 처리             | 창 17:1, 17:5, 17:17, 18:12-15  | **mid~high** — 이 미션 단일 최대 R2 위험 지점(18:14)                          |
| `scene5.yml` | 이삭 — 오랜 기다림 끝의 출생                         | 창 21:1-2, 21:5-7               | **mid** — R4 진입 전 동의 게이트(노년 출산 서사), skip 경로 있음              |

## 스키마 정본

`content/elijah/scene*.yml` · `content/solomon/scene*.yml` 컨벤션을 미러 — consent_card / footer_persistent /
`lint_forbidden_tokens` / `R1_voice_self_harm_listener`(action: `pause_fade_black_show_crisis_card`, enforced_at: always,
원문 미저장 해시만) / `theology_footer_refs.disputed_points` 블록 구조. `lint_forbidden_tokens` 는
`safety_gates[].id: R2_R3_lint_forbidden` 하위에 중첩(seed §7-1(a) 정본) — 최상위 미러는 두지 않았다. 단, 동일
파이프라인의 다른 인물(`content/daniel/`)은 최상위 배치를 택한 사례가 있어 교차 검증이 필요하다
(`docs/MVP-ABRAHAM.md` §15 열린 질문 2번 참조).

## 안전 제약축 (R1~R5 요약)

- **R1** — 자해·위기 발화 감지 시 게임 로직보다 우선. `R1_voice_self_harm_listener` 5/5 Scene 상시(`action:
pause_fade_black_show_crisis_card`, `fade_to_black_seconds: 0.8`). Scene 3 은 15:2-3(항의) 인용 자막을 사용자 1인칭
  절망 서술로 LLM 재생성하지 않으며, 15:13(사백 년 압제) 블록은 `dwell_limit_on_four_hundred_years`(최대 3초) 로 장기
  고착을 막는다.
- **R2** — 지연 정당화 gaslighting 금지 lint 52종("때가 되면"·"당신의 때"·"미루시" 등, `lint_forbidden_tokens` 참조).
  Scene 3 의 R2 근거는 "본문이 이유를 침묵한다"가 아니라 "본문이 준 이유(15:16)가 아브람 자신에게 전이되지 않는다"이다
  (seed §1-2 정정본). Scene 4 는 `R2_gen18_14_no_second_person_transfer` — 18:14 발췌·2인칭 전이·역추론 전면 금지.
  **정직한 한계 고지**: 이 lint 는 정적 텍스트 풀만 검사하며, opt-in LLM 생성 응답 중 새로 확인된 위험 문장 8종은 이
  lint 로 감지되지 않는 것이 확인되어 있다(8/8 미검출) — "차단됨"·"우회 불가"라고 표현하지 않는다. 실제 방어선은
  `docs/MVP-ABRAHAM.md` §12.6 의 인간 검토 9개 항목이다.
- **R3** — 기다림 성과 압박 금지(같은 52종 lint 에 포함). Scene 3 `wait_label` 3경로(voice_the_lack / hold_the_promise /
  sit_with_unknowing) 모두 정당 — 우열 없음(`R3_all_waits_valid`). Scene 5 마무리 문구도 "포기하지 않으면 결국 받는다"
  류를 쓰지 않는다.
- **R4** — Scene 5 진입 전 consent_card(노년 출산 서사 사전 고지, `voice_intensity_options: [subtitle_only, low, default]`).
  skip 시 21:6 인용 자막 한 장만 노출 후 라우팅 직행 — skip 목적지는 **같은** Scene 5(별도 파일 없음).
- **R5** — `default_path: static_curation` + `llm_optin_only: true` 5/5 Scene 최상위. 분기 텍스트(Scene 3 카드 응답,
  Scene 5 마무리 문구 9종+폴백) 전부 정적으로 존재 — opt-out 도 서사가 완결된다.
- **crisis 자원** — 하드코딩 금지, `{{crisis_resources.default}}` 토큰만. 본 디렉터리 yml 내 실측 **13곳**: Scene 1
  Pre-Scene 0 게이트 1 + `footer_persistent` 5(Scene 마다 1) + `R1_voice_self_harm_listener.card_text_ko` 5(Scene 마다 1)
  - `crisis_reminder`(Scene 3, 5) 2.

## 콘텐츠 배제 경계 (제품 안전선 — 신학 판단 아님)

- 이삭을 제물로 드리라는 시험 사건(창세기 21장 직후에 이어지는 장, 신약 재인용 포함) — **전면 배제**. 이 사건과
  직결된 절대 배제 리터럴 표기(정본은 작업 지시 원문·`seed-ABRAHAM.md` 참조 — 이 README 에는 목록을 다시 옮겨
  적지 않는다)는 이 디렉터리 어디에도 등장하지 않는다.
- 여종 서사(창 16, 창 21:8-21, 하갈·이스마엘) — 학대·유기 narrative, 본편 미사용. `scene5.yml` 의 disputed_points
  DP2 에만 존재 언급.
- 아내를 누이라 속인 사건(창 12:10-20) — 본편 미사용.
- 개명 연출(창 17:5, 아브람→아브라함)은 고정 텍스트만 사용 — 사용자 실명·프로필 데이터 투입 금지
  (`safety_gates.real_name_exclusion`, `scene4.yml`).

## Scene 3 핵심 인터랙션 상세

| 오브젝트/동작         | 의미                                       | 판정                                                                 |
| --------------------- | ------------------------------------------ | -------------------------------------------------------------------- |
| 뭇별 헤아리기(응시)   | 창 15:5, 셀 수 없음이라는 약속의 크기      | 성공/실패 판정 없음 — "셀 수 없음"이 의도된 결말                     |
| `wait_label` 3지 카드 | 없음을 말함 / 약속을 붙듦 / 모른 채로 있음 | 세 경로 모두 정당(R3_all_waits_valid), 미선택 20초 시 `null` 로 진행 |

`wait_label` 은 Scene 5 `br_s5_closing_message` 라우팅으로 그대로 이월된다. 어느 경로든(선택·미선택 모두) Scene 5 는
완결된다.

## 관련 문서

- 설계: `docs/MVP-ABRAHAM.md`
- Scene 5 마무리 문구 9종 + 폴백 전문: `docs/MVP-ABRAHAM.md` §14 부록 (scene5.yml 은 balanced 톤 3종만 기본 탑재)
- 정본 seed: `seed-ABRAHAM.md`(v5-b) — 이 디렉터리와 `docs/MVP-ABRAHAM.md` 의 유일한 근거 문서

_AI 보조 — 본문은 성경 참조._
