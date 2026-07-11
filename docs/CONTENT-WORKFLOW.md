# CONTENT-WORKFLOW — AI 생성 → 신학 검토 → 공개 프로세스

> AI 가 만든 묵상문·시편·게임 분기 대사가 *어떻게 사용자에게 도달하기까지*. 신학적 환각 방지 + 사용자 보호.

---

## 0. 원칙

| 원칙 | 의미 |
|---|---|
| **AI 자동 출판 금지** | LLM 생성 → 사람 검토 → 공개 |
| **계층화 검토** | 콘텐츠 타입별 검토 수준 다름 (위험도 기반) |
| **버전 영속** | 모든 콘텐츠는 *불변 버전* — 변경 시 새 버전 |
| **롤백 가능** | 문제 발생 시 *즉시 이전 버전* 복귀 |
| **사용자 피드백 루프** | *"이 응답이 어색해요"* 가 검토 큐로 |

---

## 1. 콘텐츠 분류 — 위험도 기반

| 카테고리 | 예시 | 위험도 | 검토 수준 |
|---|---|---|---|
| **A. 정적 본문** | 성경 본문 (창 45:5, 시 23편 등) | 🟢 0 | 인용만 — 검토 불필요 |
| **B. 결정적 사용자 경험** | 게임 결말 메시지, 시편 매칭 추천 | 🔴 높음 | **2명 검토** (신학 + 심리) |
| **C. 게임 NPC 대사** | 아론·엘리압·골리앗 한 줄 | 🟡 중간 | **1명 검토** (신학) |
| **D. 사용자 개인 묵상** | 사용자 일기 → AI 묵상 변환 | 🟡 중간 | **LLM 1차 + 사용자 거부 가능** |
| **E. 시스템 메시지** | 위기 자원·안내·툴팁 | 🟢 낮음 | 1명 (법무) |
| **F. 시각 자료** | 일러스트·아이콘 | 🟢 낮음 | 1명 (디자인) |

---

## 2. 상태 머신

```
   draft  ─── (AI 1차 검토) ──▶  ai_reviewed
                                      │
                                      ▼
                                 review_pending
                                  ├──▶ approved  ──▶ published
                                  │                       │
                                  ├──▶ revise            (rollback 가능)
                                  │      │
                                  │      ▼
                                  │   draft (다시)
                                  │
                                  └──▶ rejected ──▶ archived
```

DB 의 `content_versions.status` 컬럼:
- `draft` — AI 가 막 생성
- `ai_reviewed` — LLM 1차 검토 통과
- `review_pending` — 사람 검토 대기
- `approved` — 사람 승인
- `published` — 사용자에게 노출 중
- `rejected` — 검토 거절 → 아카이브
- `archived` — 더 이상 사용 X (히스토리만 보관)

---

## 3. AI 1차 검토 (Auto-screening)

**언제**: AI 가 콘텐츠 생성 직후

**무엇**: 별도 LLM (Claude 3.5 Sonnet) 이 *생성된 콘텐츠를 검토*

### Prompt 템플릿

```
<system>
당신은 정통 개신교 신학 검토자입니다. 주어진 콘텐츠가 사용자에게 공개되기
전 다음을 체크합니다:

1. 정통 신학에서 벗어나지 않는가
2. 성경 본문 *직접 인용* 부분이 정확한가 (잘못된 인용은 reject)
3. 우울/불안 사용자에게 *해로울 수 있는 표현* 이 있는가
4. *번영 신학*, *영지주의*, *뉴에이지* 해석이 있는가
5. 자살·자해 등 위험 키워드 *부추기는* 내용이 있는가

판정: 'approve' | 'revise' | 'reject'
이유: 한 문장
risk_tags: [...]   # 위 5가지 중 매칭된 위험
</system>

<user>
콘텐츠 타입: {content_type}
범위: {scope}
본문:
{generated_text}

JSON 응답:
</user>
```

### 결과

| 판정 | 액션 |
|---|---|
| `approve` | `ai_reviewed` 상태로 진행 → 사람 검토 대기열 |
| `revise` | `draft` 로 돌려보내고 *원본 LLM 에 재생성 요청* (1회만) |
| `reject` | `archived` 직행. 시도 자체 폐기 |

### 자동 거절 패턴

- 위기 키워드 (자살·자해) 출력 → 즉시 reject
- 성경 본문 인용에 *알려진 표현 X* (예: "예수님이 *해탈* 을 말씀하셨다") → reject

---

## 4. 사람 검토 — 카테고리별 워크플로우

### 4.1 B. 결정적 사용자 경험 (게임 결말·시편 매칭)

**2명 검토 필수** — 신학 + 심리

```
ai_reviewed
   │
   ├──▶ 신학 검토자 ─▶ {approve, revise, reject}
   │       │
   │       (approve 시)
   │       ▼
   ├──▶ 심리 검토자 ─▶ {approve, revise, reject}
   │       │
   │       (둘 다 approve 시)
   │       ▼
   approved
```

**검토 기준**:

| 검토자 | 체크리스트 |
|---|---|
| 신학 | 정통 신학 / 성경 본문 정확 / 해석 일반화 가능 |
| 심리 | 우울 사용자 자극 X / 가스라이팅 X / 임상적 안전 |

**SLA**: 콘텐츠 생성 → 24시간 안 1차 검토 → 48시간 안 최종.

### 4.2 C. 게임 NPC 대사

**1명 검토** (신학)

```
ai_reviewed → 신학 검토자 → {approve | revise | reject} → approved
```

SLA: 12시간.

### 4.3 D. 사용자 개인 묵상 (실시간)

**검토자 사전 검토 X** — 다만:
- LLM 1차 검토 결과 위험도 *높음* 이면 *사용자에게 표시 안 함*
- *위험 낮음* 이면 사용자에게 *"AI 묵상" 라벨 + 거절 옵션* 으로 표시
- 사용자가 *"이상해요"* 신고 → 검토 큐로

### 4.4 E. 시스템 메시지·F. 시각

별도 워크플로우 (디자인 리뷰 또는 법무 리뷰).

---

## 5. 검토자 역할 정의

### 신학 검토자

**자격**:
- 신학과 학사+ 또는
- 목사 안수 또는
- 신학교 재학 (3학년+)

**책임**:
- 콘텐츠가 *정통 개신교 범위* 내 확인
- 성경 본문 인용 정확성
- 특정 교단 편향 회피 (장로교/감리교/침례교/순복음 중립)

**도구**:
- 검토 대시보드 (관리자 화면)
- 본문 비교 도구 (개역개정 vs LLM 출력)
- 코멘트 + 분류 (heresy_risk, doctrinal_unclear, pastoral_concern...)

### 심리 검토자

**자격**:
- 임상심리 석사+ 또는
- 정신건강사회복지사 1급+ 또는
- 정신건강의학과 의사

**책임**:
- 우울·불안 사용자에게 *안전*
- 트라우마 자극 회피
- 가스라이팅 패턴 검출 (*"고통도 다 뜻이 있다"* 류)

**도구**:
- 검토 대시보드
- *PHQ-9 류 검사 점수* 와 콘텐츠 매칭 시뮬레이션

---

## 6. 콘텐츠 버전 관리

### 6.1 모든 콘텐츠 = 불변 버전

DB `content_versions` 테이블에 *추가만* — UPDATE 없음.

```sql
INSERT INTO content_versions (
    content_type, scope, text, language,
    status, created_by, approved_by, approved_at, published_at
) VALUES (
    'closing_message',
    'joseph:scene_5:emotion_anxiety',
    '오늘 손에 쥔 자루가...',
    'ko',
    'published',
    'ai-claude-3.5-sonnet-v1',
    'human:reviewer_abc',
    '2026-06-15T...',
    '2026-06-15T...'
);
```

같은 *scope* 의 다른 버전이 추가될 때:
- 새 버전 INSERT → `published`
- 옛 버전 → `archived`

### 6.2 사용자에게 보이는 버전 = `published`

```sql
SELECT text FROM content_versions
WHERE scope = 'joseph:scene_5:emotion_anxiety'
  AND status = 'published'
ORDER BY published_at DESC
LIMIT 1;
```

### 6.3 롤백

문제 발생 시:
```sql
-- 현재 버전 archive
UPDATE content_versions SET status='archived' WHERE id='current-v3';
-- 이전 v2 를 다시 published
UPDATE content_versions SET status='published', published_at=now() WHERE id='v2';
```

→ 1초 안 롤백.

---

## 7. 사용자 피드백 루프

### 7.1 인앱 피드백 UI

콘텐츠 끝나면:
```
이 응답 어땠어요?
  👍 좋아요    🤔 어색해요    🚨 신고
```

`🚨 신고` 누르면 즉시 검토 큐 진입. *해당 사용자 세션에서 그 콘텐츠 안 보임*.

### 7.2 피드백 → 검토 워크플로우

```
사용자 신고
    │
    ▼
content_feedback (DB)
    │
    ├─ 같은 콘텐츠 신고 *3회 누적* → 즉시 archive (자동)
    │
    └─ 신고 1~2회 → 검토자 큐 (24h 안 확인)
```

### 7.3 신고 분류

| 분류 | 처리 |
|---|---|
| "신학적으로 이상" | 신학 검토자 |
| "마음에 상처" | 심리 검토자 |
| "성경 본문 틀림" | 즉시 archive + 신학 검토 |
| "위기 상황" | crisis_alerts 와 연계 |

---

## 8. 정기 감사 (Audit)

### 8.1 월간 감사

매월 1일 자동 cron:
- 지난 30일 *published* 콘텐츠 100건 무작위 샘플
- 신학·심리 검토자 *재검토*
- 통과율 < 95% 시 *prompt v* 업데이트 트리거

### 8.2 분기별 외부 자문

- 외부 신학자 1명 + 외부 임상심리사 1명
- 시뮬레이션 사용자 시나리오 20개 실행
- 보고서 → 차기 prompt 버전 반영

---

## 9. 콘텐츠 생성 → 공개 timeline 예시

```
T0:     사용자가 Scene 3 분배 결정 (요셉 Scene 3)
T0+50ms: 시스템이 *해당 분기 조합* 의 캐시 조회
         캐시 hit → 캐시 콘텐츠 표시
         
캐시 miss 경로:
T0+50ms:  LLM 호출 (gpt-4o-mini)
T0+2s:    LLM 응답 → AI 1차 검토 (Claude)
T0+5s:    AI 검토 = approve → ai_reviewed 상태로 DB 저장
T0+5.1s:  *임시 콘텐츠* 로 사용자에게 표시 (마크 "AI 임시")
T0+24h:   신학 검토자 큐 진입 → 검토
T0+48h:   최종 approve → published
T0+48h+:  같은 분기 다른 사용자 → published 콘텐츠 표시
```

→ *첫 사용자* 는 *AI 임시* 콘텐츠 만남. 24~48시간 후 *검토된* 콘텐츠로 자연 전환.

### *임시 콘텐츠* UI 표시

```
┌─────────────────────────────────────────┐
│                                          │
│   *AI 가 막 만든 응답입니다*              │
│                                          │
│   "오늘 손에 쥔 자루가 ..."              │
│                                          │
│   ⚠ 신학·심리 검토 진행 중               │
│   🔧 어색하면 알려주세요 [신고]           │
│                                          │
└─────────────────────────────────────────┘
```

---

## 10. 사전 캐싱 — 검토 부담 완화

**핵심 통찰**: 사용자 선택 분기가 *유한* 하면 *모든 조합 사전 생성·사전 검토*.

### 사전 생성 대상

| 분류 | 분기 수 | 사전 검토 가능? |
|---|---|---|
| 게임 결말 메시지 (요셉) | 9 | ✅ |
| 게임 결말 메시지 (모세) | 3 (톤) | ✅ |
| 게임 결말 메시지 (다윗) | 5 (감정별) | ✅ |
| 시편 매칭 (10 감정 × 강도 1~10) | 100 | ✅ (이건 본문 인용이라 검토 가벼움) |
| 잠언 카드 추천 (상황별) | 무한 | ❌ — 실시간 + AI 1차 검토 |
| 사용자 묵상 변환 | 무한 (사용자별) | ❌ — 사용자 거절 가능 |

→ *유한 분기는 사전 100% 검토*. 무한 분기는 *임시 + 사후 검토*.

---

## 11. 검토 시스템 — 자체 도구 (Phase 2)

### Admin 대시보드 화면

```
┌──────────────────────────────────────────────────┐
│  Lemuel — 검토 대시보드                          │
├──────────────────────────────────────────────────┤
│                                                   │
│  📋 검토 대기                                     │
│    신학: 12 건  (SLA 24h 내 처리)                │
│    심리: 8 건   (SLA 24h)                        │
│                                                   │
│  ⚠ 사용자 신고                                    │
│    오늘: 3 건  (모두 처리됨)                     │
│    이번주: 14 건                                  │
│                                                   │
│  📊 통계                                          │
│    이번주 published: 89 건                        │
│    revise: 12 건 (13%)                            │
│    reject: 3 건 (3.4%)                            │
│                                                   │
└──────────────────────────────────────────────────┘
```

각 콘텐츠 클릭하면:
```
[원문 콘텐츠 표시]
[Scene·상황 컨텍스트]
[성경 본문 비교]
[AI 1차 검토 결과·이유]
[심리 영향 시뮬레이션]

[ Approve ] [ Revise (사유) ] [ Reject (사유) ]
```

---

## 12. 비상 시 — Kill Switch

문제 콘텐츠 발견 시 *즉시 모든 사용자에게 그만 보이게*:

```sql
-- 즉시 published → emergency_disabled
UPDATE content_versions
SET status = 'emergency_disabled',
    disabled_reason = '[REASON]',
    disabled_at = now()
WHERE scope = '[SCOPE]'
  AND status = 'published';
```

사용자 노출은 즉시 멈춤. *fallback 콘텐츠* (없으면 일반 메시지) 자동 노출.

### Fallback 콘텐츠

scope 별로 *최후 보루* 사전 작성:
- `joseph:scene_5:*` fallback = *"오늘도 함께 했어요. 다음에 또 만나요."*
- `david:scene_5:*` fallback = *"수고하셨어요. 다음 거인이 와도 함께해요."*

---

## 13. 검토 메트릭 — Prometheus

```
content_review_pending{category="theological"} 12
content_review_pending{category="psychological"} 8
content_review_pending{category="design"} 4

content_review_throughput_total{category, reviewer_id} 234
content_review_decision_total{category, decision="approve"} 198
content_review_decision_total{category, decision="revise"} 30
content_review_decision_total{category, decision="reject"} 6

content_user_feedback_total{type="negative"} 22
content_user_feedback_total{type="positive"} 187

content_emergency_disable_total 1
content_published_active_count 1842
```

### 알람

- *대기 콘텐츠 > 30건* → Telegram (검토자에게 push)
- *SLA 위반 (48h 초과)* → 매일 09:00 알람
- *사용자 신고 시간당 > 5* → 즉시 알람

---

## 14. 신학 자문 채널 (외부)

### 자문단 구성 (목표)

- 정통 개신교 1명 (장로/감리/침례 중)
- 임상심리사 1명 (정신건강 전문가)
- 인터·디지털 신학 1명 (Phase 3 다국어 자문)

### 자문 절차

1. 콘텐츠 검토 시 *애매한 경우* 자문단에 *비동기 질문* (Slack/Email)
2. 자문 응답 → 콘텐츠 결정 + *결정 근거 영구 기록*
3. *반복되는 자문* 은 prompt v 업데이트로 자동화

### 자문 비용

- 정기 자문 (월 2~4시간) — 자문료 50~100만원/월
- 긴급 자문 (시급) — 별도

→ MVP 검증 후 V1.0 출시 전 자문단 구성 권장.

---

## 15. 콘텐츠 IP·저작권

### 성경 본문 번역

- **개역개정**: 대한성서공회 비영리 사용 약관 — 무료 사용 가능
- **현대인의 성경**: 생명의말씀사 — 라이선스 협의 필요
- **NIV/ESV (영문)**: Phase 3 다국어 시 라이선스 협의

→ MVP 는 *개역개정 우선*. 사용자 톤 설정에서 *번역 선택지* 제공.

### AI 생성 콘텐츠 IP

- 사용자 일기·시편은 *사용자 IP*
- AI 가 그 위에 생성한 묵상은 *공동 IP* (사용자 + Lemuel)
- *공유* 시 사용자 명시적 동의 필수

---

## 16. 다음 단계

1. **Admin 대시보드 Spring Boot** + React 어드민 (Phase 2)
2. **검토자 채용·교육** — V1.0 출시 6주 전
3. **사전 캐싱 스크립트** — 모든 분기 조합 LLM 생성·검토·DB 저장
4. **사용자 피드백 UI** — Phase 1 (MVP) 부터
5. **Kill switch 동작** — 비상 시 1분 안 disable 가능 확인

---

> **TL;DR** — *AI 자동 출판 금지 + 위험도 기반 계층 검토 + 모든 콘텐츠 불변 버전 + 사용자 피드백 즉시 반영 + 비상 kill switch*. 사전 캐싱으로 검토 부담 80% 절감. 신학·심리 두 자문단이 *콘텐츠의 양심*.
