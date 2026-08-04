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
cd backend && ./gradlew test --tests '*GroundingDatasetTest' --tests '*EvalMetricsTest'

# 2단 — 실제 임베딩으로 임계치 스윕 리포트 (GEMINI_API_KEY 필요)
GEMINI_API_KEY=... ./gradlew test --tests '*GroundingThresholdSweepReport'
#   → build/reports/grounding-eval/latest.json  (+ 콘솔 요약표)

# 고정 임계치 회귀 확인 (GEMINI_API_KEY 필요)
GEMINI_API_KEY=... ./gradlew test --tests '*ScriptureGroundingValidationTest'
```

`GEMINI_API_KEY` 가 없으면 라이브 테스트 2종은 `@EnabledIfEnvironmentVariable` 로 자동 skip 된다.
**즉 지금 CI 는 정확도를 측정하지 않는다** — 이걸 야간 CronJob 으로 옮기는 게 Phase 3 이다.

## 8. 다음 단계 (Phase 3~4, 미구현)

- **Phase 3**: 야간 CronJob 으로 스윕을 돌리고 `grounding_eval_precision{class,dataset_version,threshold}` 등
  gauge 를 Prometheus 로 노출. 클러스터에 Pushgateway 가 없으므로, 이미 스크레이프 중인
  `ServiceMonitor lemuel-xr-prod/lemuel-xr-backend` 에 얹는 쪽이 신규 부품 0 이다.
  per-fixture 원시 결과는 stdout JSON → fluent-bit → ELK (회귀 시 "어느 문장이 뒤집혔나" 추적용).
- **Phase 4**: 직전 런 대비 recall·오탐률 악화 시 CI 실패. `p3FalseRejectRate > 0.05` 2회 연속 시 Alertmanager.
