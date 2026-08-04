# Scripture-Grounding 골든셋 — 라벨링·측정 규약

> **목적**: 근거성 게이트(`backend/.../ai/grounding`)의 **정확도·재현율을 지속 측정**해서
> 승격계약([`docs/superpowers/specs/2026-07-22-grounding-enforce-promotion-contract.md`](../../docs/superpowers/specs/2026-07-22-grounding-enforce-promotion-contract.md)) §2 의
> **P3(오탐률 < 5%)·P4(임계치 확정)·P6(드리프트 알람)** 을 감이 아니라 숫자로 채우는 것.
>
> 여기 있는 건 **데이터셋과 규약**이다. 측정 코드는 `backend/src/test/kotlin/.../ai/grounding/eval/` 에 있다.

---

## 1. 왜 테스트 리소스가 아니라 리포 루트인가

이전에는 픽스처 5개가 `backend/src/test/resources/grounding/` 에 있었다. 옮긴 이유:

- **골든셋은 테스트 픽스처가 아니라 자산이다.** 라벨링 이력·검토 상태·버전이 붙어야 하고, 백엔드 모듈 안에 숨어 있으면 그게 안 붙는다.
- **저작 콘텐츠(`content/`)와 같은 위치 규약**을 따른다. 백엔드 클래스패스 밖의 데이터를 테스트가 읽는 패턴은 이 리포에 이미 있다(`ContentSafetyGateEnforcementTest`).
- 클래스패스 밖이면 Gradle 이 입력으로 못 보고 `:test` 가 UP-TO-DATE 로 건너뛴다 → `backend/build.gradle.kts` 에 `inputs.dir("../eval/grounding")` 로 등록해 뒀다. **데이터셋만 고쳐도 테스트가 다시 돈다.**

## 2. 디렉터리

```
eval/grounding/
  README.md            ← 이 문서 (규약)
  v1/
    manifest.json      ← 데이터셋 메타: 튜닝 기준 모델·고정 임계치·클래스 정의·목표 표본수·규칙
    fixtures/*.json    ← 픽스처 1건 = 파일 1개
```

버전 디렉터리(`v1/`)를 나눈 이유: **임계치는 데이터셋 버전에 종속**이다. 나중에 "recall 0.9" 를 봤을 때
그게 어느 셋 기준인지 모르면 그 숫자는 쓸모가 없다. 셋 구성을 크게 바꾸면 `v2/` 를 새로 판다.

## 3. 픽스처 스키마

```jsonc
{
  "id": "orthodox-job", // 파일명과 같아야 한다 (리포트·로그의 조인 키)
  "class": "orthodox", // manifest.json 의 classes[].id 중 하나
  "difficulty": "easy|medium|hard",
  "purpose": "meditation", // 게이트 메트릭의 purpose 태그와 같은 축
  "expectedStatus": "ACCEPTED|REJECTED|NO_EVIDENCE|INCONCLUSIVE", // class 의 expectedStatus 와 일치해야 한다
  "review": {
    "status": "signed_off|draft",
    "labeledBy": "human|structural|claude-draft",
    "labeledAt": "2026-07-19",
    "rationale": "왜 이 라벨인가 — 나중에 라벨을 의심할 때 읽을 유일한 근거",
  },
  "meditationText": "...", // 평가 대상 (AI 생성물 역할)
  "passages": [{ "reference": "욥 42:5", "text": "..." }], // 공급된 근거 본문
}
```

무결성(스키마·id 중복·class↔expectedStatus 정합·본문 재사용 여부)은
`GroundingDatasetTest` 가 **네트워크 없이** 검증한다. CI 에서 항상 돈다.

## 4. 라벨링 절차 (draft → signed_off)

`draft` 는 리포트에 별도 집계로만 나오고 **게이트 판정에는 절대 들어가지 않는다.**
승격시키려면 [`docs/CONTENT-EVALUATION-GATES.md`](../../docs/CONTENT-EVALUATION-GATES.md) 의 3단 구조를 그대로 적용한다:

1. **1단 기계 검증** — `GroundingDatasetTest` 통과 (스키마·정합성).
2. **2단 의미 검증** — `lemuel-theology-reviewer` 와 `lemuel-mental-health-safety` 를 **병렬·독립**으로 돌린다.
   두 검토는 서로의 결과를 보지 않는다. 지적사항은 픽스처에 반영하고, 반영 결과는 에이전트 보고가 아니라 **직접 실측**으로 재확인한다.
3. **3단 사람 사인오프** — `review.status` 를 `signed_off` 로 바꾸고 `labeledBy: "human"`, `labeledAt`, `rationale` 을 채운다.
   R1(자살사고) 본문을 다루는 픽스처는 **어떤 자동 합의로도 승격 불가** — 인간 안전 검토자 사인오프가 마지막 게이트다.

`labeledBy: "structural"` 은 예외다. `NO_EVIDENCE`/`INCONCLUSIVE` 처럼 **임베딩 이전에 코드 분기가 라벨을 확정**하는
케이스는 신학 판단이 개입할 여지가 없어 2단을 건너뛴다.

## 5. 절대 규칙 (깨면 지표가 무의미해진다)

- **LLM 이 라벨을 만들지 않는다.** 승격계약 §1.1 이 "판정은 결정론적이고 LLM 재판정으로 뒤집을 수 없다"고 고정했다.
  LLM-judge 로 정답을 만들면 게이트가 LLM 을 채점하는 게 아니라 LLM 이 게이트를 채점하게 된다.
- **프로덕션 통과분을 자동 편입하지 않는다.** 게이트가 통과시킨 것을 정답으로 삼으면 자기 채점이다.
  운영 표본을 넣으려면 사람이 라벨을 새로 붙여야 한다.
- **성경 본문은 이미 원문 대조를 마친 구절만 재사용한다.** 새 구절을 도입할 땐 개역개정 원문 대조(대한성서공회)가 선행한다.
  지금 `v1` 의 모든 픽스처는 2026-07-19 에 검증된 5구절(욥 42:5·욥 1:21·시 88:1·시 88:18·요 9:3)만 쓴다.
- **임계치를 바꾸면 근거가 된 스윕 리포트 경로를 커밋 메시지에 남긴다.**

## 6. 표본 수에 대한 정직한 경고

현재 `signed_off` 는 7건(사람 5 + 구조적 2)이다. 목표는 60건·클래스당 8건 이상이다.
**이 표본으로 계산한 precision/recall 은 방향 지표일 뿐 승격 근거가 아니다.**
그래서 리포트는 모든 지표 옆에 표본 수를 같이 찍고, 목표 미달이면 헤더에 경고를 낸다.

특히 `suffering-justification`(고난정당화) 층이 임계치를 결정한다.
현행 `maxUnsupportedRate=0.3` 은 사실상 이 클래스 표본 **한 건**(관측 미근거율 0.33)을 넘기려고 0.5 에서 조인 값이다.
n=1 로 정한 임계치라는 뜻이고, 이 층의 표본을 늘리는 게 데이터셋 확장의 1순위다.

## 7. 어떻게 돌리나

```bash
# 1단 — 데이터셋 무결성 + 지표 계산 로직 (네트워크 불필요, CI 에서 항상 실행)
cd backend && gradle test --tests '*GoldenSetIntegrityTest' --tests '*EvalMetricsTest'

# 2단 — 실제 임베딩으로 임계치 스윕 리포트 (GEMINI_API_KEY 필요)
GEMINI_API_KEY=... gradle test --tests '*GroundingThresholdSweepReport'
#   → build/reports/grounding-eval/latest.json  (+ 콘솔 요약표)

# 고정 임계치 회귀 확인 (GEMINI_API_KEY 필요)
GEMINI_API_KEY=... gradle test --tests '*ScriptureGroundingValidationTest'
```

`GEMINI_API_KEY` 가 없으면 라이브 테스트 2종은 `@EnabledIfEnvironmentVariable` 로 자동 skip 된다.
**즉 CI 는 정확도를 측정하지 못한다** — 키가 없는 곳에 측정 체계를 두는 건 측정하는 시늉이다.
그래서 Phase 3 은 측정 주체를 CI 가 아니라 **배포된 애플리케이션**으로 옮긴다(§8).

## 8. Phase 3 — 애플리케이션이 스스로 채점한다 (구현 완료, 기본 비활성)

골든셋은 빌드 시 `eval/grounding/v*/` → jar 안 `grounding-golden-set/` 으로 복사된다
(`backend/build.gradle.kts` 의 `processResources`). 배포된 파드가 클래스패스에서 읽어
하루 1회 채점하고, 결과를 `grounding.goldenset.*` gauge 로 낸다. 수집은 **이미 있는**
`ServiceMonitor lemuel-xr-prod/lemuel-xr-backend` 가 그대로 한다 — 신규 부품 0, Pushgateway 불필요.

| 설정 | 기본값 | 뜻 |
|---|---|---|
| `GROUNDING_EVAL_ENABLED` | `false` | 켜야 돈다. 켜면 유료 임베딩 API 를 주기 호출한다 |
| `GROUNDING_EVAL_CRON` | `0 30 3 * * *` | 매일 03:30 KST (ShedLock `xr-grounding-goldenset-eval` 로 파드 1개만) |
| `GROUNDING_EVAL_VERSION` | `v1` | 채점할 골든셋 버전 |
| `GEMINI_API_KEY` | (없음) | **켜려면 필수.** 없이 켜면 부팅에서 즉시 실패한다 |

주요 지표(`grounding.goldenset.` 접두사): `precision` · `recall` · `f1` ·
`false_reject_rate`(P3 정의 = FP/(TP+FP)) · `false_positive_rate`(통계적 FPR) ·
`exact_accuracy` · `abstain_rate` · `samples` · `mismatches` · `excluded_drafts` ·
`embedded_texts` · `class_accuracy{class}` · `policy.similarity_threshold` ·
`policy.max_unsupported_rate` · `last_success_epoch_seconds` · `runs` · `failures`.

두 가지 설계 원칙이 지표를 정직하게 만든다:

1. **값이 없으면 0 이 아니라 `NaN`.** 표본·분모가 없을 때 0 을 내면 "오탐률 0%" 로 읽혀
   P3(<5%) 을 표본 없이 통과한 것처럼 보인다. 빈 값이 조용히 틀린 값보다 낫다.
2. **실패해도 지난 값을 덮지 않는다.** 임베딩 장애 시 `failures` 만 오르고
   `last_success_epoch_seconds` 가 늙어 장애가 드러난다. 지표를 0 으로 밀면 가짜 회귀 알람이 된다.

채점은 `signed_off` 표본만 대상으로 하고 합성 트래픽이므로 게이트 운영 메트릭(`grounding.*`)에
섞이지 않는다(무동작 `GroundingMetricsPort` 주입).

> **아직 안 켰다.** 켜려면 `charts/lemuel-xr/templates/app.yaml` 의 백엔드 Deployment 에
> 기존 시크릿 `lemuel-xr-gemini-secret` 참조와 `GROUNDING_EVAL_ENABLED=true` 를 넣어야 한다
> (지금 그 Deployment 에는 `GEMINI_API_KEY` 가 없다 — 키는 AI 사이드카에만 있다).
> 그건 프로덕션 변경이라 별도 승인 후 진행한다.

## 9. 다음 단계 (Phase 4, 미구현)

- 직전 런 대비 recall·오탐률 악화 시 알람. `p3FalseRejectRate > 0.05` 2회 연속 시 Alertmanager.
- per-fixture 원시 결과를 stdout JSON → fluent-bit → ELK (회귀 시 "어느 문장이 뒤집혔나" 추적용).
- 표본을 목표치(§6)까지 채우기 전에는 어떤 수치 하한도 과적합 고정에 불과하다 — 그게 선행 조건이다.
