# content/solomon — 솔로몬 미션 Scene 정의 (Theme 13 · 성공 속 허무 재정향)

> ⚠️ **초안 고지** — 수기 seed(독립 채점 0.82 통과, 기준 0.8) 기반 초안.
> **`lemuel-theology-reviewer` + `lemuel-mental-health-safety` 사후검토 필요** — 특히 Scene 3(영아 상실 언급·칼 위협)·Scene 4(허무 정서)는 인간 사인오프 권고.
> 검토 게이트 기록: `docs/CONTENT-EVALUATION-GATES.md` 1~2단 통과 기록 필요. 3단(합의)은 R1 본문 부재로 비발동.

## 파일

| 파일 | Scene | 본문 | 민감도 |
|---|---|---|---|
| `scene1.yml` | 기브온의 일천번제 — 부담·지혜 구함 | 왕상 3:4~9 | 낮음 (제물 올리기 = 비핵심 도입 연출, 서사 상태 불변) |
| `scene2.yml` | 꿈에 응답하신 하나님 — 수용됨 | 왕상 3:5, 3:11~13 | 낮음 |
| `scene3.yml` | 두 여인 재판 ★ 핵심 인터랙션 #1 | 왕상 3:16~28 | **mid** — 진입 전 consent_card + 요약 자막 skip 경로 |
| `scene4.yml` | 영광 속 허무 ("헛되다") | 왕상 10 요약 + 전 1:2, 2:4~11 | **low~mid** — consent_card + Scene 5 직행 skip |
| `scene5.yml` | 재정향 — 하나님을 경외하라 ★ 핵심 인터랙션 #2 | 전 12:13 | 낮음 (내려놓기 비강제, on_timeout 20초 완주 처리) |

## 스키마 정본

`content/elijah/scene*.yml` 컨벤션 그대로 미러 — consent_card / footer_persistent / lint_forbidden_tokens / `R1_voice_self_harm_listener`(action: `pause_fade_black_show_crisis_card`, enforced_at: always, 원문 미저장 해시만, 게임 로직보다 우선) / disputed_points 블록 구조.

## 안전 제약축 (R1~R5 요약)

- **R1** — 죽음선호 구절(전도서 4장 초반 류) 인용·각색·암시 전면 금지(기계 검증: 참조 문자열 0건). `R1_voice_self_harm_listener` 5/5 Scene 상시.
- **R2** — 허무 가스라이팅 금지 lint: "믿음이 부족"·"감사가 부족"·"욕심이 많아서"·"만족을 모르". Scene 4 는 공감 먼저·교정 없음.
- **R3** — 재정향 압박 금지 lint: "이제 깨달았으니"·"다시 열심히"·"허무를 극복". 내려놓기 비강제.
- **R4** — Scene 3 mid + Scene 4 low~mid consent_card, 각 skip 경로(Scene 3 → 요약 자막 후 Scene 4 / Scene 4 → Scene 5 직행).
- **R5** — `default_path: static_curation` + `llm_optin_only: true`. 분기 텍스트 전부 정적 존재 — opt-out 도 서사 완결.
- **crisis 자원** — 하드코딩 금지, `{{crisis_resources.default}}` 토큰만 (본 디렉터리 yml 내 실측 **9곳**: Scene 1 Pre-Scene 0 게이트 1 + Scene 3 consent 1 + Scene 4 consent 1 + Scene 4 overlay 1 + 전 Scene footer 5).

## 경로 의존 내려놓기 오브젝트 (Scene 5)

| 오브젝트 | 의미 | 등장 조건 |
|---|---|---|
| 왕관 | Scene 1 의 부담 | 항상 |
| 칼 | Scene 3 판결의 무게 | Scene 3 완주 시 (skip·미완주 — 이탈·R1 인터럽트 후 재방문 포함 — 시 판결 두루마리로 대체 — 칼 시각 트리거 회피, S3) |
| 보물 | Scene 4 의 허무 | Scene 4 방문 시 (skip 시 미등장) |

어느 경로든 Scene 5 는 완결된다.

## 관련 문서

- 설계: `docs/MVP-SOLOMON.md`
- 실 콘텐츠(대본·9조합 큐레이션): `docs/MVP-SOLOMON-CONTENT.md`

*AI 보조 — 본문은 성경 참조.*
