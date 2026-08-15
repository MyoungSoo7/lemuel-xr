# Lemuel XR

*Created At: 2026-08-15T17:10:10.900397+00:00*

## Goal

성경 인물을 통해 하나님을 배우는 감정 회복·서사 게임 XR 플랫폼 (역순 브라운필드: 기존 코드 1056파일에서 PRD 복원)

## User Stories

1. **As a** 묵상 사용자, **I want to** 성경 인물이 하나님을 만난 자리를 직접 걸어보기, **so that** 그 인물의 경험을 통해 하나님을 배운다.
2. **As a** 위기 상황의 묵상 사용자, **I want to** 절망·자해 관련 문장을 입력했을 때 즉시 위기 자원 안내를 받기, **so that** 어두운 본문(욥기·시편88편·엘리야의 로뎀나무)을 다루는 앱에서 안전하게 보호받는다.
3. **As a** 묵상 사용자, **I want to** LLM이 생성한 묵상문을 인물·씬 맥락에 맞게 제공받기, **so that** 매번 새로운 묵상으로 성경 인물의 경험에 깊이 들어간다.
4. **As a** 콘텐츠 저작자/운영자, **I want to** 씬·시나리오 YAML을 3단 게이트(기계→의미→합의)로 검증하기, **so that** 정신건강·신학적으로 안전한 콘텐츠만 배포된다.

## Constraints

- 치료 도구 아님 — 임상 진단·치료 영역이 아니라 예방적 자기훈련 (2026-05-22 정착)
  - ⚠️ 정정: 생성본 초안의 "Recovery 영성 '동행' 플랫폼" 표현은 **폐기된 중간 단계 프레이밍**이다. 리포는 `9dac0ca` 에서 그 표현을 떠났고, 지금은 오너 확정(하나님 배우기)이 상위다. "Recovery"·"환자"·"임상" 어휘는 사용하지 않는다.
- 북극성은 '성경 인물을 통해 하나님을 배우기' 단일 — 자살예방·영적 비상 대비 프레이밍은 superseded (제품 오너 확정)
- 프레이밍이 superseded돼도 안전 게이트는 핵심 제약으로 유지, 약화 금지 — 게이트 트리거는 앱 목적이 아니라 사용자 입력이므로
- 정신건강 안전 게이트 현 구현 유지: CrisisKeywordScanner(CRITICAL→LLM 스킵+LOCKOUT), ForbiddenTokenScanner(치환·1회 재시도·하드폴백), DisclaimerGateFilter(451, 위기자원 면제)
- 위기 자원 전화번호 하드코딩 검사 (frontend CI) 유지
- 금지 콘텐츠 정책: 자해 유도·가스라이팅·회복 압박·고난 정당화·영지주의·뉴에이지·번영신학 차단
- 고난정당화 판정 3술어: (1) 귀책 전가 — 고통 원인을 사용자 죄·믿음·감사 부족으로 지목 (2) 반증 불가능한 조건부 약속 — 회복 미도래 시 원인이 전부 사용자 귀속 (3) 상실의 실재 무효화 — '…일 뿐' 류 축소 어법
- 판정 경계 원칙: 교리의 내용이 아니라 발화의 방향 — 하나님을 향한 진술은 통과, 사용자를 향한 귀책·약속·축소는 차단
- 신학 런타임 판정 기준: 니케아+종교개혁 개신교 합의 수준 (옵션 B 확정) — 교파 distinctive는 런타임 게이트가 판정하지 않음
- MVP-JESUS.md의 합신/WCF 확정은 예수 인물 전용 저작 지침으로 격하, 런타임 기준 아님. 문서에 '저작 지침이며 런타임 판정 기준이 아님' 명시
- 인물별로 신학 기준이 다른 설계 명시적 기각 — 런타임 판정은 전 인물 단일(B), 저작 지침만 인물별로 좁을 수 있음
- LLM이 골든셋 라벨을 만들지 않는다 — LLM 초안 제안 후 사람 서명은 허용하되 서명 없는 표본은 골든셋에 불가 (eval/grounding/README §5)
- 신학 차단 시 사용자에게 차단 사실 노출 금지 — 폴백이 조용히 대신 나감 (위기 LOCKOUT과 정반대: 위기=반드시 노출, 신학=보이면 안 됨)
- 하드 폴백 문구: '지금은 어떤 말도 보태지 않겠습니다. 여기 이대로 머물러도 괜찮습니다.' — 신학·안전 양축 공통 단일 문구, 별도 문구 만들지 않음 (문구 차이로 차단 사유 유추 방지)
- ForbiddenTokenScanner 금지어 목록(680개)의 정신건강 축 토큰은 현행 동결 — '고난에도 뜻이' 류 유지 확정
- 인물 로스터 (2026-08-16 실측 — 생성본 초안의 "Stage 1 = 욥·엘리야·시편88편, 요셉 후순위" 는 **사실이 아니므로 폐기**):
  - `content/` 11개: abraham · daniel · david · elijah · esther · jacob · **joseph** · moses · peter · ruth · solomon
  - `backend/.../scenarios/` 8개: david · elijah · **jesus** · **job** · joseph · moses · ruth · solomon
  - `docs/MVP-*.md` 19개 (인물 약 13종). 요셉은 `content/joseph` + `scenarios/joseph.yml` + `MVP-JOSEPH.md` + `MVP-JOSEPH-CONTENT.md` 로 **가장 완성도 높은 축에 속한다 — 후순위가 아니다.**
  - `시편 88편`은 인물이 아니라 본문이며 AR Track-A 영역이다. 인물 로스터에 넣지 않는다.
  - VR 몰입 4인물(요셉·모세·다윗·예수)은 `docs/MISSION.md` 기준이며, 위 구현 현황과 층위가 다르다(기획 축 vs 구현 축).
- AI 생성은 활성화 상태 (application.yml default true, helm 오버라이드 없음, AI 파드 Running)
- CI 게이트 초록불 ≠ 통과 — gate-baseline은 드리프트만 차단하며 12개 인물 게이트 전부 현재 BLOCKED. PRD에 이 사실 명시 필수
- 3단 우선순위 판정 체계: (1) K3s 배포 실행 중 = 최상위 진실 (2) CI 게이트 강제 = 확정 스펙 (3) docs 명세 = 계획 문서
- 충돌 산출물: PRD 말미에 docs↔코드 불일치 목록을 표로 남기고 각 행에 판정 근거(배포/게이트/문서) 기재
- 스택: Spring Boot 헥사고날 백엔드 + Next.js/R3F 프론트 + LLM 묵상 생성 + TTS 사이드카 + Unity(stub)

## Success Criteria

1. 골든셋 gnostic-inner-divinity 표본이 REJECTED로 자동 판정됨 (현재 모든 자동 게이트 통과 — 이 1건이 게이트 존재 이유의 증명)
2. 골든셋 orthodox 4건 전부 ACCEPTED 유지, 오탐 0건 (신학 게이트가 정통 콘텐츠를 막으면 제품이 죽는다)
3. 골든셋 suffering-justification 계열 3건 전부 REJECTED
4. 신학 축 토큰(영지주의·뉴에이지·고난정당화 잔여 ~15개)이 ForbiddenTokenScanner에 추가되어 L2 재현율 7/7, 정밀도 100% 달성 (현재: 재현율 2/7, 정밀도 100%)
5. L1 프롬프트 가드에 신학 배제 어휘(이단 목록·배제 교리명) 주입됨 (현재 신학 관련 0개)
6. 신학 게이트 판정이 safety_alerts DB row + Micrometer 카운터로 기록됨 (안전 게이트와 동일 관측성 수준)
7. 신학 차단 시 기존 재생성→하드폴백 캐스케이드(GenerateLlmResponseUseCase.kt:71-89) 경로로 처리됨 — 별도 경로 신설 아님
8. CI에서 L2 토큰 린트 신학 테스트가 GEMINI_API_KEY 없이 무조건 실행됨 (현재 ScriptureGroundingValidationTest는 @EnabledIfEnvironmentVariable으로 항상 skip)
9. 신학 토큰 확정은 기존 라벨링 절차(에이전트 2종 병렬 검토 → 사람 서명) 적용됨
10. docs/MISSION.md가 superseded 프레이밍 반영하여 갱신됨

## Assumptions

- 골든셋 라벨 서명자는 오너 본인 — 외부 신학 자문은 비구속적 '추천'으로만 존재 (docs/safety-guidelines.md:75-77, 2026-05-22 자문가 필수 검증 폐기)
- 라벨링 절차(에이전트 2종 병렬 검토 → 사람 최종 서명)는 이미 운영 중인 현행 관행의 문서화임, 신규 제안 아님
- 리스크 R-1: 골든셋 60건 확장은 오너 1인 처리량 병목 — 외부 의존성이 아니라 내부 대역폭 리스크. 완화: v1은 현행 13건으로 착수, L2 토큰은 라벨 병목과 무관하게 진행 가능
- v1 신학 토큰 ~15개는 골든셋 REJECTED 5건에서 추출하므로 적대 표본 과적합 가능 — v1을 '신학 게이트 완성'으로 읽으면 안 됨 (CI 초록불 함정과 동종 오독 위험)
- 임베딩 기반 근거성 게이트(gemini-embedding-001 코사인 유사도 임계 0.62)는 함의(entailment) 검사 불가 구조적 한계 확인됨 — 신학 정통성 판정 도구로 사용 불가 (eval/grounding/README §6.1)
- docs/FUNCTIONAL-SPEC.md:106의 content_versions.disputed_points JSONB 참조는 이중 무효 (컬럼 미존재 + V20260522014900에서 테이블 삭제)
- lemuel-theology-reviewer.md:12의 theology_reviews.decision 참조는 고아 (테이블 삭제됨)
- GenerateLlmResponseUseCase.kt:52의 '임상 자문 영입 전까지 LLM 생성 비활성화' 주석은 stale — 실제로는 2026-05-22부터 활성 (불일치 목록 1번)
- ArchUnit 라이브러리는 없음 — DataStandardTest.kt의 자체 소스 스캔일 뿐 (초기 전제 오류 정정)
- 폴백 경로(GenerateLlmResponseUseCase)는 캐시 오염 차단·오염 응답 비캐싱·REQUIRES_NEW 트랜잭션 격리가 이미 구현된 재사용 가능한 기존 자산
- 런타임 금칙 목록 680개는 100% 정신건강 축이고 신학 축 토큰은 문자 그대로 0개 (2026-08-16 전량 대조 실측)

## Decide Later

The following items were deferred or identified as premature at this stage. They should be revisited when more context is available:

- GitHub branch protection의 required status check 설정 여부 — 리포 내용만으로 판정 불가, 미확인으로 남김
- 뮤테이션 스크립트 4종의 CI baseline 포함 여부 (현재 느려서 제외)
- docs/CONTENT-EVALUATION-GATES.md:30이 주장하는 disputed_points 필수 필드 검사를 실제로 구현할지 여부 (현재 해당 스크립트 부존재, 문서가 허위 주장 중)
- 골든셋 13→60건 확장 (클래스당 8건 이상, 리포 자체 목표) — v2, v1 선행조건 아님
- L3 LLM 함의 분류기 — v2, Gemini API(기존 무료 키) 재사용, 비동기 섀도우 배포 후 오탐률 확인 뒤 차단 승격
- 2단 의미 게이트 (신학·정신건강 에이전트 검토) — 코드 없음, 구현 필요 (Tier 3)
- 3단 합의 게이트 (3인 독립 검토 + 사람 서명) — 코드 없음, 구현 필요 (Tier 3)
- 1단 기계 게이트 차단력 복구 — 현재 드리프트만 차단하므로 결함으로 명시, 별도 요구사항
- GROUNDING_EVAL_ENABLED 섀도우→강제 승격 (기본 on, 차단 경로 연결)
- 정신건강 축 토큰 중 정통 교리 발화와 겹치는 항목 재검토 — 신학 게이트 v1 안정화 이후

> **정정**: 생성본 초안은 "요셉 인물 트랙 (후순위)" 과 "VR 4인물" 두 줄을 이 절에 넣었으나, 둘 다 *결정 대기 항목이 아니라 사실 진술*이므로 Constraints 의 인물 로스터로 옮겼다. 특히 "요셉 후순위" 는 실측과 어긋나 폐기했다.

## Existing Codebase Context

- **lemuel-xr** (`/Users/lms/lemuel-xr`)
  성경 기반 감정 회복 + 서사 게임 XR 플랫폼. Spring Boot 헥사고날 백엔드 + Next.js/R3F 프론트 + LLM 묵상 생성(ai/) + TTS 사이드카 + Unity(stub). 듀얼 트랙: 트랙A 정적 지혜·감정 콘텐츠, 트랙B 인물 미션(`content/` 11인물 + `scenarios/` 8종 — 위 인물 로스터 참조). 정신건강 안전 게이트는 런타임 강제 중이나 **신학 검증은 런타임·CI 강제 0건**(문서·사람 검토뿐)이고, 3단 평가 게이트는 1단(기계)만 CI 에 있으며 그마저 드리프트 검사라 차단력이 없다.

---
*PM ID: pm_seed_interview_20260815_162702*
*Interview ID: interview_20260815_162702*
