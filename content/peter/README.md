# content/peter — 베드로 미션 Scene 정의 (Theme 14 · 실패·수치심 이후의 정체성 재구성)

> ⚠️ **초안 고지** — seed(`seed-PETER.md` v4) 확정 전문 기반 초안.
> **`lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필요** — 특히 Scene 2(부인·자기정죄 자극)·Scene 3(통곡 정서 정점)는 인간 사인오프 필수.
> 검토 게이트 기록: `docs/CONTENT-EVALUATION-GATES.md` 통과 기록 필요.
> `blocks_scene`(R1 발화 감지 시 정지 여부) A/B 결정은 인간 임상 자문 전까지 미해결 — `docs/MVP-PETER.md` §8.3 참조. 현재 구현은 기준값 A(`pause_fade_black_show_crisis_card`)로 다른 인물과 통일.

## 파일

| 파일         | Scene                             | 본문                  | 민감도                                                                                         |
| ------------ | --------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------- |
| `scene1.yml` | 중보와 실패 예고 — 시몬아, 시몬아 | 눅 22:31-34           | 낮음 (중보를 실패 예고보다 먼저 배치 — R2)                                                     |
| `scene2.yml` | 세 번의 부인 ★ 핵심 인터랙션 #1   | 눅 22:54-60, 요 18:18 | **high** — 진입 전 consent_card + Scene 4 직행 skip 경로 (`consent_covers_scenes: [2, 3]`)     |
| `scene3.yml` | 주께서 돌이켜 보시니 — 통곡       | 눅 22:61-62           | **high** — Scene 2 consent_card 로 커버(별도 카드 없음), `crisis_reminder` 상시 노출           |
| `scene4.yml` | 다시 던진 그물 ★ 핵심 인터랙션 #2 | 요 21:1-6             | 낮음 (노동 복귀 연출 — 회한의 도피로 각색 금지, R3)                                            |
| `scene5.yml` | 숯불 앞의 회복 — 이탈 지점        | 요 21:9-17            | **mid** — `crisis_reminder`(Scene 3 과 다른 문구) + `restore_label` 선택 + 12종 정적 회복 문구 |

본 미션은 요한복음 21:17 에서 끝난다. 그 이후 두 절은 R1·R4 사유로 완전히 배제되며, 배제 사실을 문자열로 남기는 자리는 `scene5.yml` 의 `excluded_passages_note` 한 줄로 한정된다 — 자세한 사유는 `docs/MVP-PETER.md` disputed_points 5번·§12.6-a 참조.

## 스키마 정본

`content/elijah/scene*.yml` · `content/solomon/scene*.yml` 컨벤션 그대로 미러 — consent_card / footer_persistent / lint_forbidden_tokens(`safety_gates[]` 내부 중첩, top-level 아님) / `R1_voice_self_harm_listener`(action: `pause_fade_black_show_crisis_card`, enforced_at: always, 원문 미저장 해시만, 게임 로직보다 우선) / disputed_points 블록 구조.

베드로 미션 고유 확장 키(top-level, 문서 루트):

- `consent_card_id` / `consent_trigger_ko` / `consent_covers_scenes` / `skip_alternative_scene_id` / `skip_summary_subtitle` — Scene 2 전용, seed §4 R4 확정.
- `consent_cards[]` / `skip_summary` — Scene 2 전용.
- `crisis_reminder`(`persistent`/`dismissible`/`text_ko`) — Scene 3·Scene 5 전용, `safety_gates[]` 의 `footer_persistent` 와는 별개 트랙. Scene 3 과 Scene 5 는 문구가 서로 다르다(seed §4-1-a).
- `denial_card_id`(Scene 2 — `deny_knowing`/`deny_belonging`/`deny_understanding`, 본문 1:1 고정, `decisions` 미기록·`restore_label` 비상관).
- `restore_label`(Scene 5 — `unforgivable`/`unworthy`/`afraid_again`/null, `faith_tone` 3단과 결합해 사전 저작 12종 정적 문구로 귀결. LLM 은 opt-in 시에만 호출).
- `excluded_passages_note`(Scene 5 전용 1줄 — 요한복음 21:17 이후 배제 사유, `<!-- EXCLUSION-DECL -->` 마킹).

## 안전 제약축 (R1~R5 요약)

- **R1** — 음성 자해 감지 리스너 `R1_voice_self_harm_listener` 5/5 Scene 상시(발화 원문 미저장, 해시만). Scene 3 통곡 장면은 자막·시선 애니메이션만 — 1인칭 자기멸시 서술 생성 금지.
- **R2** — 인과형 가스라이팅(실패에 신적 의도 부여) 금지 lint. Scene 1 은 중보(22:31-32)를 실패 예고(22:34)보다 먼저 배치해 구조적으로 방어.
- **R3** — 회복 압박(재발 방지 다짐 강요) 금지 lint. 부인 카드·회복 문구 어디에도 명령형·권유형·결과 약속 0개. `on_timeout: treat_as_complete` / `proceed_without_label` 로 실패 상태 자체를 제거(Scene 1·3·4·5 공통 원칙).
- **R4** — Scene 2 진입 직전 `consent_card`(`consent_covers_scenes: [2, 3]`) + Scene 4 직행 skip + `skip_summary`(통곡 미포함, 4줄 정적 해설). Scene 3 은 Scene 2 동의로 커버되어 별도 카드 없음.
- **R5** — Scene 2·Scene 5 `default_path: static_curation` + `llm_optin_only: true`. Scene 5 는 `restore_label`(3값+null) × `faith_tone`(strong/balanced/soft) = 9 + 3 null-fallback = 총 12종 문구 전부 사전 저작·정적 큐레이션 — 기본 경로에서 LLM 미호출.
- **crisis 자원** — 하드코딩 금지, `{{crisis_resources.default}}` 토큰만 (본 디렉터리 yml 내 실측 **12곳**: Scene 1 2 + Scene 2 2 + Scene 3 3(R1 1·`crisis_reminder` 1·footer 1) + Scene 4 2 + Scene 5 3(R1 1·`crisis_reminder` 1·footer 1)).

## denial_card_id ↔ 본문 1:1 고정 (Scene 2)

| id                   | 본문                              | 카드 문구                            |
| -------------------- | --------------------------------- | ------------------------------------ |
| `deny_knowing`       | 눅 22:57                          | "나는 그 사람을 모릅니다."           |
| `deny_belonging`     | 눅 22:58                          | "나는 그 사람들 중 하나가 아닙니다." |
| `deny_understanding` | 눅 22:60 (선택지 없음, 자동 진행) | "나는 무슨 말인지 모르겠습니다."     |

세 카드 모두 정당하며 틀린 답이 없다(`note: R3_all_denials_are_the_text`). `denial_card_id` 는 `decisions` 에 절대 기록하지 않고 `restore_label` 과 상관시키지 않는다.

## restore_label × faith_tone (Scene 5)

`restore_label`(`unforgivable`/`unworthy`/`afraid_again`/null) 과 `faith_tone`(strong/balanced/soft)의 조합이 `POST /api/game/peter/complete` 응답의 회복 문구를 결정한다. 전체 12종(9 + null-fallback 3) 은 전부 사전 저작 정적 텍스트이며, `tone_check.py` 기준(고유명사 직접 사용=strong, `분` 의존명사 1회만=balanced, 둘 다 없음=soft)을 전수 통과했다(seed 자체 검증). 전문은 `scene5.yml` `branches[].br_s5_recovery_message` 참조.

## 관련 문서

- 설계: `docs/MVP-PETER.md`
- 정본 seed: `seed-PETER.md`(v4, 검토 담당 보관)
- 신학 참고문헌: `docs/THEOLOGY-REFERENCES.md` §7(베드로 항목 — 미반영, 공유 파일이라 본 PR 범위 밖)

_AI 보조 — 본문은 성경 참조 — storyteller 역할. 위기 시 {{crisis_resources.default}}._
