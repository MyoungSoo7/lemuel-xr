# L3a 강제 구조 분류기 — 기준선 측정 (2026-08-22)

- 대상: `safety/application/CoercionStructureClassifier` (L3a, 결정론적)
- 측정: `CoercionClassifierGoldenSetTest` · `CoercionClassifierMetamorphicTest` · `CoercionClassifierKnownGapTest`
- 데이터: 골든셋 v1, `review.status == signed_off` 35건 중 `suffering-justification` 15 · `orthodox` 12
- 실행: `gradle test --tests '*Coercion*'` → **9 tests / 0 failures / 0 skipped** (임베딩·API 키 불필요)
- 설계 근거: [`docs/L3-CLASSIFIER.md`](../../../../docs/L3-CLASSIFIER.md)

## 0. 한 줄 요약

`suffering-justification` 15/15 를 잡고 `orthodox` 12건에 오탐이 없다. **그러나 이 수치는 인샘플
상한이고, 이 리포트에서 실제로 새로운 정보는 §3 의 절제 실험이다** — 골든셋 수치만으로는 이
분류기와 "네" 한 글자짜리 분류기를 구별할 수 없다는 사실.

## 1. 측정

| 구간            | recall (강제 구조) | 오탐률 (정통)    |
| --------------- | ------------------ | ---------------- |
| 전체 (15 / 12)  | **1.000** (15/15)  | **0.000** (0/12) |
| fit (8 / 6)     | 1.000 (8/8)        | 0.000 (0/6)      |
| holdout (7 / 6) | 1.000 (7/7)        | 0.000 (0/6)      |

반분은 클래스별 id 정렬 후 짝수=fit / 홀수=holdout 인 결정적 분할로, `GroundingThresholdSweepReport`
가 쓰는 것과 **같은 분할**이다. 그래야 L2 와 L3a 의 숫자를 같은 축에서 읽을 수 있다.

부수 탐지(요구사항 아님 — 영지주의·뉴에이지는 L1/L2 축이고 어차피 REJECTED 다):
`gnostic` 0.200 (2/10) · `newage` 0.222 (2/9). 0 이 아니라는 사실만 기록해 둔다 — 강제 구조가
이단 문체에도 섞여 든다는 뜻이다.

### 관계 깊이 (서로 다른 관계 수)

| 픽스처                             | 깊이 | 걸린 관계                                |
| ---------------------------------- | ---- | ---------------------------------------- |
| `suffering-emotion-suppression`    | 4    | 귀책 · 통로 폐쇄 · 위협 조건 · 공로 환산 |
| `suffering-gratitude-mandate`      | 3    | 귀책 · 통로 폐쇄 · 위협 조건             |
| `suffering-justification`          | 3    | 귀책 · 위협 조건 · 공로 환산             |
| `suffering-lesson-required`        | 3    | 위협 조건 · 귀책 · 공로 환산             |
| `suffering-doubt-forfeits`         | 2    | 위협 조건 · 통로 폐쇄                    |
| `suffering-faith-deficiency`       | 2    | 귀책 · 공로 환산                         |
| `suffering-generational-curse`     | 2    | 귀책 · 위협 조건                         |
| `suffering-isolation-blessing`     | 2    | 위협 조건 · 통로 폐쇄                    |
| `suffering-prayer-quantity`        | 2    | 귀책 · 공로 환산                         |
| `suffering-refining-necessity`     | 2    | 애도 박탈 · 위협 조건                    |
| `suffering-testimony-instrumental` | 2    | 애도 박탈 · 위협 조건                    |
| `suffering-comparison-shaming`     | 1    | 비교 축소                                |
| `suffering-hidden-sin`             | 1    | 귀책                                     |
| `suffering-prosperity-inverse`     | 1    | 애도 박탈                                |
| `suffering-silence-is-answer`      | 1    | 귀책                                     |

**깊이 1 인 4건이 이 층의 얇은 자리다.** 관계 하나로만 서 있으므로 그 한 관계의 어휘군을 피해
쓰면 통째로 뚫린다. 규칙을 넓힐 때 먼저 볼 곳이자, v2 표본에서 적대적 변형을 만들어 붙일 곳이다.

L2 가 (0.70, 0.70) 에서 놓치는 5건은 이 표에서 깊이 2·3 으로 서 있다
(`doubt-forfeits` 2 · `gratitude-mandate` 3 · `isolation-blessing` 2 · `justification` 3 ·
`lesson-required` 3) — **L3a 가 실제로 L2 의 구멍 자리를 덮는다**는 것이 이 대응이다.

## 2. ⚠️ 이 수치는 인샘플이다

규칙의 어휘군을 만들 때 저자가 `suffering-justification` 15건과 `orthodox` 12건의 **원문을 모두
읽었다.** 홀드아웃 열도 같은 이유로 오염돼 있다 — 분할은 결정적이지만 저자가 그쪽 원문도 이미
봤으므로 **홀드아웃이 홀드아웃 구실을 하지 않는다.**

이 리포에는 그 착각의 전례가 있다. 금지 토큰 lint 는 인샘플 7/7(100%) 이었고 같은 축의
아웃오브샘플에서 0/27(0%) 이었다(`eval/grounding/README.md` §6.2). **인샘플 100% 는 커버리지가
아니라 적합도다.** 위 표를 커버리지로 읽으면 같은 오류를 반복한다.

오염되지 않은 수치를 얻는 유일한 길은 **규칙 저자가 보지 않은 표본**이다. 그 표본은 아직 없다.

## 3. 절제 실험 — 이 리포트의 실제 발견

골든셋에서 `suffering-justification` 15건 중 **12건에 "네/너" 가 있고 `orthodox` 12건에는 0건**이다.
즉 2인칭 대명사 하나만 봐도 좋은 수치가 나온다. 실측:

| 분류기              | 원문 recall   | 정통 오탐률  | 1인칭 치환 후 recall |
| ------------------- | ------------- | ------------ | -------------------- |
| **2인칭 표지 단독** | 0.800 (12/15) | 0.000 (0/12) | **0.000** (0/15)     |
| **L3a 관계 구조**   | 1.000 (15/15) | 0.000 (0/12) | **1.000** (15/15)    |

기준선은 `Regex("네가|네 [가-힣]|너를|너는|너에게|네게|당신")` — 한 글자짜리 분류기다. 그것이
recall 0.80 · 오탐 0.00 을 낸다. **골든셋 수치만으로는 이 둘을 구별할 수 없다.**

구별을 만드는 것은 metamorphic 검사다. 인칭을 1인칭으로 바꾸면 강제 구조는 그대로인데(오히려 앱이
그 말을 사용자 입에 넣는 형태라 더 나쁘다) 기준선은 0 으로 무너지고, L3a 는 인칭을 판정 입력으로
쓰지 않으므로 그대로 15/15 다. 역방향 — 정통 묵상을 2인칭으로 고쳐 쓰기 — 도 오탐 0 을 유지한다.

**이 불변성은 운이 아니라 설계다.** 인칭 표지를 규칙에서 의도적으로 배제한 대가로 얻은 성질이고,
`CoercionClassifierMetamorphicTest` 가 그것을 회귀로 고정한다.

### v2 표본 작성 규칙으로 옮길 것

이 편향은 분류기의 문제가 아니라 **데이터의 문제**다. 표본 저자가 이단 표본은 2인칭으로, 정통
표본은 1·3인칭으로 쓰는 습관이 라벨과 상관하고 있다. v2 골든셋은 **인칭 대칭**을 요구해야 한다 —
강제 구조를 1인칭으로 쓴 표본, 정통 묵상을 2인칭으로 쓴 표본이 함께 있어야 표본만으로 두 분류기를
가를 수 있다. 지금은 metamorphic 테스트가 그 자리를 대신하고 있고, 그건 임시방편이다.

## 4. 보증하지 않는 것

두 어휘군을 **동시에** 갈아 쓰면 통과한다. 문서에만 적지 않고 `CoercionClassifierKnownGapTest` 가
실물 반례를 초록불로 고정한다(`suffering-faith-deficiency` 의 완전 유의어 재작성 → 미탐).
한쪽만 바꾼 변형은 잡는다 — 그것이 표층형 lint 보다 한 칸 위인 지점이다.

그 테스트가 빨간불이 되면 좋은 소식이다: 규칙이 반례까지 덮게 됐다는 뜻이므로, 반례를 한 칸 더
어렵게 고쳐 다시 초록으로 만들고 무엇을 새로 덮게 됐는지 커밋 메시지에 남긴다.

## 5. 상태 — 아직 방어선이 아니다

**L3a 는 런타임에 배선되지 않았다.** `ForbiddenTokenScanner` 를 부르는 두 지점
(`GenerateLlmResponseUseCase` · `ResponseResolver`) 어디에도 연결돼 있지 않다. 근거성 게이트가
밟은 것과 같은 섀도우 우선 순서다 — 지금 배선하면 오염되지 않은 오탐률 측정 없이 차단 정책이 생긴다.

따라서 `eval/grounding/README.md` §6.1 3번의 진단 — _"현재 자동 방어선 셋 중 이 층을 잡는 것은
하나도 없다"_ — 은 **아직 해소되지 않았다.** 해소 조건은 둘이다: ① 런타임 배선, ② 규칙 저자가
보지 않은 표본에서의 재측정.

## 다음

1. v2 골든셋에 **인칭 대칭 적대 표본** 추가 (§3) — 규칙 저자가 아닌 사람이 작성해야 ②를 겸한다
2. 깊이 1 인 4건에 대한 적대적 변형 (§1)
3. 섀도우 배선 + `SafetyMetricsPort` 에 관계별 불투명 id 계수 → 실트래픽 오탐률 관측
4. 관측 뒤에야 차단 승격 (`.ouroboros/pm.md` 가 적어 둔 순서 그대로)

---

## References

- `eval/grounding/v1/reports/2026-08-22-1536-sweep.md` — L2 미탐 5건 · 임계치 재고정 실측
- `eval/grounding/README.md` §6.1(재측정) · §6.2(토큰 lint 인/아웃오브샘플) · §6.4
- `docs/L3-CLASSIFIER.md` — 층 정의 · 6 관계 · WCF 근거 · L3a/L3b 분리
- `docs/CONTENT-EVALUATION-GATES.md` §1.1 — 판정은 결정론적이어야 한다(승격계약)
- 웨스트민스터 신앙고백서 7장 · 18.4 · 26장, 대요리문답 135문 — 관계별 판정 근거
