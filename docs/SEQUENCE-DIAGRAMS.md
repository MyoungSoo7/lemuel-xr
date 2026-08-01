# 시퀀스 다이어그램 — Lemuel XR

> **렌더링**: GitHub 마크다운에서 Mermaid 자동 렌더링. 로컬 미리보기는 VSCode `Markdown Preview Mermaid Support` 확장.
> **상위 문서**: [`FUNCTIONAL-SPEC.md`](FUNCTIONAL-SPEC.md), [`BUILD-PLAN.md`](BUILD-PLAN.md), [`DB-SCHEMA.md`](DB-SCHEMA.md)
> **범위**: 가장 자주 발생하는 7개 시나리오. 엣지 케이스는 각 다이어그램 하단 *Notes* 참고.

---

## 0. 등장 액터

| Actor | 무엇 |
|---|---|
| **User** | XR 헤드셋 (Quest / Vision Pro / Galaxy XR) 또는 Web 클라이언트 |
| **Unity Client** | XR 프론트엔드 — 입력·렌더링·인터랙션 |
| **Spring Backend** | Spring Boot 3.5.4 (JDK 21) — 헥사고날 application/adapter |
| **Python AI Sidecar** | FastAPI 등 — LLM / TTS / 임베딩 호출 책임 |
| **Gemini API** | Google AI Studio / Vertex AI — LLM 추론 |
| **PostgreSQL** | 17 + pgvector |
| **Reviewer** | 신학 리뷰어 (role=`theology_reviewer`) |
| **Admin** | 관리자 (데이터 삭제 처리 등) |

---

## 1. 트랙 A — 감정 입력 → Theme 추천 → XR Scene 진입

> F-1.3 + F-2.x + F-5.4 (fallback chain) 의 happy path.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UC as Unity Client
    participant API as Spring Backend
    participant AI as Python AI Sidecar
    participant GEM as Gemini API
    participant DB as PostgreSQL

    User->>UC: "오늘 너무 불안해" 입력
    UC->>API: POST /api/emotion/classify {text, deviceId}
    API->>API: SafetyController.detectSelfHarmKeyword(text)
    Note over API: 자해 키워드 없음 → 진행<br/>발견 시 → §4 흐름으로 분기
    API->>DB: INSERT emotion_logs (raw_text, device_id)
    API->>AI: POST /classify {text, lang=ko}
    AI->>GEM: gemini-flash-latest predict
    alt 503 UNAVAILABLE
        GEM-->>AI: 503
        AI->>GEM: gemini-2.0-flash retry
        alt 또 503
            GEM-->>AI: 503
            AI->>GEM: gemini-1.5-flash retry (최종 fallback)
        end
    end
    GEM-->>AI: {emotion: "anxiety", confidence: 0.84}
    AI-->>API: classified result
    API->>DB: UPDATE emotion_logs SET emotion='anxiety', confidence=0.84
    API->>DB: SELECT theme matches FOR emotion='anxiety'
    DB-->>API: [Theme 4 시편, Theme 6 마음 지키기]
    API-->>UC: { recommendedThemes: [...], emotionLogId }

    User->>UC: Theme 4 (시편과 감정) 선택
    UC->>API: GET /api/content/4/scene?mode=emotional
    API->>DB: SELECT scene assets FOR theme=4 mode=emotional
    DB-->>API: { sceneId, scriptureRef='psalm-13', bgmUrl, ttsScript }
    API-->>UC: scene payload
    UC->>UC: XR Scene 진입 — Skybox + BGM + TTS 시작
    User->>UC: 묵상 체험 (수동, LLM 호출 없음)
    UC->>API: POST /api/content/scene_views { sceneId, durationSec=180 }
    API->>DB: INSERT scene_views
```

**Notes**
- LLM 분류는 **사이드카 경유** — Spring 도메인이 Gemini 모델 이름을 모름 (헥사고날 규칙).
- fallback chain 은 `Python AI Sidecar` 내부에서 처리. Spring 입장에선 한 번의 호출.
- Scene asset 은 *정적 콘텐츠* (DB 또는 CDN) — LLM 추가 호출 없음.
- `mode=emotional` 은 F-1.4 의 영성/감성/이성 분기 중 하나. Scene 톤·내레이션이 모드별로 분기.

---

## 2. 일기 작성 → AI 묵상 변환 (옵션)

> F-2.1 + F-4.1 + F-7 신학 검증 게이트.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UC as Unity Client
    participant API as Spring Backend
    participant AI as Python AI Sidecar
    participant GEM as Gemini API
    participant DB as PostgreSQL

    User->>UC: 일기 5W1H 작성 (5분)
    UC->>API: POST /api/diary/entries { text, mode='rational' }
    API->>API: SafetyController.detectSelfHarmKeyword(text)
    API->>DB: INSERT diary_entries (encrypted via pgcrypto)
    DB-->>API: { entryId }
    API-->>UC: { entryId, savedAt }

    User->>UC: "묵상으로 변환" 버튼 탭
    Note over UC: 변환은 사용자 명시 동의 — 자동 호출 금지
    UC->>API: POST /api/diary/entries/{id}/devotional
    API->>DB: SELECT llm_cache WHERE input_hash = hash(text)
    alt 캐시 HIT
        DB-->>API: cached devotional
    else 캐시 MISS
        API->>AI: POST /devotional { text, tone='psalm', mode='rational' }
        AI->>GEM: gemini-flash with system prompt (psalm tone)
        GEM-->>AI: 1~2문장 devotional
        AI-->>API: { devotional, modelUsed, tokens }
        API->>DB: INSERT llm_cache (input_hash, output, model, ts)
        API->>DB: INSERT llm_usage (tokens, cost_estimate)
    end
    API-->>UC: { devotional, footer: "AI 보조 — 본문은 성경 참조" }

    alt 사용자 만족
        User->>UC: "저장"
        UC->>API: POST /api/diary/entries/{id}/devotional/save
        API->>DB: UPDATE diary_entries SET devotional=...
    else 사용자 거부
        User->>UC: "폐기"
        UC->>API: DELETE /api/diary/entries/{id}/devotional
        Note over API: 저장 안 함 — 압박 없음 (F-4.4)
    end
```

**Notes**
- 일기 본문은 `pgcrypto` row-level 암호화 (F-8.3).
- LLM 출력은 *항상* "AI 보조" footer 동반 — 사용자 UI 에서 분리 표시 (F-4.4, F-7).
- 사용자가 *"AI 가 답하지 말기"* 토글이 ON 이면 묵상 변환 버튼 자체가 disabled (F-6.3).
- 변환 결과는 *기본 폐기* — 저장은 사용자 명시 동의 필요.

---

## 3. 트랙 B 게임 — 요셉 미션 한 세션

> F-3.1 의 5 Scene 흐름. Scene 2~3 의 LLM 캐시 전략 포함.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UC as Unity Client
    participant API as Spring Backend
    participant AI as Python AI Sidecar
    participant GEM as Gemini API
    participant DB as PostgreSQL

    User->>UC: "요셉 미션" 선택
    UC->>API: POST /api/game/joseph/start { deviceId, mode='spiritual' }
    API->>DB: INSERT users (guest, device_fingerprint) IF NOT EXISTS
    Note over API: 첫 호출 시 guest user 자동 생성 (FK 위반 방지)
    API->>DB: INSERT game_sessions { user_id, character='joseph', started_at }
    DB-->>API: { sessionId }
    API-->>UC: { sessionId, sceneList: [s1..s5] }

    rect rgba(120,180,240,0.08)
        Note over UC,GEM: Scene 1 — 파라오 꿈 해석 (LLM 호출 없음)
        UC->>UC: 사전 애니메이션 재생 (어두운 궁전, 7마리 소)
        User->>UC: "꿈 해석 책" 잡기 — Scene 1 완료
        UC->>API: POST /api/game/joseph/decisions { sessionId, sceneId=1, decision='auto_proceed' }
        API->>DB: INSERT game_decisions
    end

    rect rgba(120,200,160,0.08)
        Note over UC,GEM: Scene 2 — 풍년기 저장 결정 (LLM 캐시 가능)
        UC->>UC: 자루 A(1/5) / B(1/3) / C(1/2) 표시
        User->>UC: 자루 B 잡고 창고에 넣음
        UC->>API: POST /api/game/joseph/decisions { sceneId=2, decision='store_1_3' }
        API->>DB: SELECT llm_cache WHERE prompt_key = 'joseph_s2_store_1_3'
        alt 캐시 HIT (예상 케이스 — 3개 분기 모두 사전 캐싱)
            DB-->>API: cached monologue
        else 캐시 MISS
            API->>AI: POST /joseph/scene2 { decision: 'store_1_3', mode: 'spiritual' }
            AI->>GEM: gemini-flash with system prompt (요셉 내면 독백)
            GEM-->>AI: 2~3문장
            AI-->>API: monologue
            API->>DB: INSERT llm_cache (prompt_key, output)
        end
        API->>DB: INSERT game_decisions, scene_views
        API-->>UC: { monologue, nextSceneId: 3, remainingGrainBags: 7 }
    end

    rect rgba(220,170,180,0.08)
        Note over UC,GEM: Scene 3 — 흉년기 분배 (핵심 결정, 분기 결과 결정적)
        UC->>UC: 3개 줄(농민/이주민/상인) 렌더
        loop 자루 N개 (Scene 2 의 결정에서 계산됨)
            User->>UC: 자루 하나를 줄 X 에 분배
            UC->>API: POST /api/game/joseph/decisions { sceneId=3, sub_decision }
        end
        UC->>API: POST /api/game/joseph/scene3/finalize { sessionId, distribution_pattern }
        API->>API: classify_distribution_pattern → 'farmer_first' | 'migrant_first' | 'trader_first'
        API->>DB: SELECT llm_cache WHERE prompt_key = 'joseph_s4_<pattern>'
        DB-->>API: cached scene4 tone (3 패턴 모두 사전 캐싱)
        API-->>UC: { nextSceneId: 4, scene4_tone }
    end

    rect rgba(200,200,140,0.08)
        Note over UC,GEM: Scene 4~5 — 형제 재회 + 결말 + 회복 메시지
        UC->>UC: scene4_tone 에 맞춰 형제 재회 영상 재생
        UC->>API: POST /api/game/joseph/decisions { sceneId=5, decision='end' }
        API->>DB: UPDATE game_sessions SET completed_at=now()
        API->>DB: INSERT recovery_metrics { user_id, theme=8, dimensions: {spiritual, emotional, rational} }
        API-->>UC: { recoveryCard: "...", scriptureRef: 'genesis 50:20' }
    end
```

**Notes**
- Scene 2 의 3개 분기 × Scene 3 의 3개 분기 = 9 패턴 — 모두 *사전 캐싱* 가능. **LLM 실시간 호출 0회** 도 달성 가능.
- 게스트 user 자동 생성은 `users` FK 위반을 막기 위한 단계 — 최근 commit `72a9420` 가 정확히 이 패턴 적용.
- `recovery_metrics` 는 사후 분석용 — 3차원 모드별 어떤 회복 효과가 있었는지 측정.
- 미완료(중도 이탈) 시 `completed_at` NULL → 다음 진입 시 *"이어서 하시겠어요?"* 옵션 (Phase 2).

---

## 4. 자해 키워드 감지 → 위기 자원 안내 (안전 게이트)

> F-6.1 — 모든 사용자 텍스트 입력에 적용되는 *우선 분기*.

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UC as Unity Client
    participant API as Spring Backend
    participant DB as PostgreSQL
    participant Admin as Admin Telegram Bot

    User->>UC: 텍스트 입력 (일기 / 감정 / 게임 채팅)
    UC->>API: POST /api/{any text endpoint}
    API->>API: SafetyController.detectSelfHarmKeyword(text)
    Note over API: 정규식 + 의미 매칭 이중 게이트
    alt 자해 키워드 발견
        API->>DB: INSERT safety_alerts { user_id, severity='high', context }
        API->>DB: INSERT crisis_resources_log { user_id, shown_at, resources }
        API->>Admin: 비동기 알림 (severity=high 일 때만)
        API-->>UC: { blocked: true, crisisResources: [...] }
        UC->>UC: 일반 흐름 중단 — 위기 자원 카드 노출
        Note over UC: 109 자살예방 상담전화<br/>1577-0199 정신건강상담전화<br/>한국심리상담센터
        alt 사용자가 "상담 연결" 탭
            UC->>UC: 전화 앱 또는 웹 링크 열기
            UC->>API: POST /api/safety/escalations { resource: 'kosp_1577' }
        else 사용자가 "괜찮아요" 탭
            UC->>API: POST /api/safety/acknowledge { alertId }
            UC->>UC: 일반 흐름 재개 (단, 입력은 차단된 채로 유지)
        end
    else 자해 키워드 없음
        API->>API: 일반 흐름 계속 진행
    end
```

**Notes**
- 정규식만으로는 false negative 가 많음 — 의미 매칭(임베딩 유사도) 이중 게이트 (F-6.1).
- *severity=high* 경우 Admin Telegram 봇으로 비동기 알림. PII 는 포함하지 않음 (user_id 만).
- 자해 키워드가 검출된 텍스트는 **저장하지 않거나** 별도 보호 컬럼에 격리. 일반 일기 테이블에 그대로 두지 않음.
- 사용자가 *"괜찮아요"* 를 눌러도 *그 입력 자체는 차단*. 일반 흐름으로 돌려보내되 그 텍스트는 처리하지 않음.

---

## 5. 신학·임상 *병렬* 검증 워크플로 (AI 출력 → 게시까지)

> F-7 + F-7.5 — 모든 새 콘텐츠가 거치는 *2-축 게이트*. 신학 자문과 임상 자문이 *동시에* 검토 큐에 진입. 양쪽 모두 `approve` 여야 published.
> 거버넌스 상세: [`docs/governance/CLINICAL-REVIEW.md`](governance/CLINICAL-REVIEW.md)

```mermaid
sequenceDiagram
    autonumber
    actor Author as Content Author
    actor Theology as Theology Reviewer
    actor Clinical as Clinical Reviewer
    participant API as Spring Backend
    participant DB as PostgreSQL
    participant Admin as Admin Telegram Bot

    Author->>API: POST /api/content/versions { topicId, body, llm_assisted, disputed_points, references[] }
    API->>API: 자동 키워드 필터 (영지주의·뉴에이지 1차) + safety guard
    Note over API: 자동 필터에 걸리면 즉시 rejected — reviewer 큐 진입 X
    API->>DB: INSERT content_versions { status='in_review', "references" JSONB }
    API->>DB: SELECT reviewer_profiles WHERE role IN ('theology','clinical') AND is_active AND scope MATCHES
    DB-->>API: [theology_a, clinical_a]

    par 신학·임상 *병렬* 큐 진입
        API->>Admin: 알림 — theology_a 에게 신규 검토 요청
        Admin-->>Theology: "콘텐츠 검토 요청 (신학)"
        Theology->>API: GET /api/theology/reviews/queue
        API-->>Theology: pending content versions
        Theology->>Theology: 본문 검토 + disputed_points 확인
        Theology->>API: POST /api/theology/reviews { versionId, verdict, scripture_accuracy, doctrinal_balance, therapeutic_safety, notes }
        API->>DB: INSERT theology_reviews
    and
        API->>Admin: 알림 — clinical_a 에게 신규 검토 요청
        Admin-->>Clinical: "콘텐츠 검토 요청 (임상)"
        Clinical->>API: GET /api/clinical/reviews/queue
        API-->>Clinical: pending + references PMID
        Clinical->>Clinical: trauma_safety / crisis_resource / moral_injury_risk / evidence_quality 체크
        Clinical->>API: POST /api/clinical/reviews { versionId, verdict, trauma_safety, crisis_resource_compliance, moral_injury_risk, evidence_quality, referenced_pmids }
        API->>DB: INSERT clinical_reviews
    end

    alt clinical_reviews.veto_used = TRUE  (moral_injury / 자해 안전망 부재 / consent 게이트 없음)
        API->>DB: UPDATE content_versions SET status='rejected'
        API->>DB: INSERT content_quarantine { reason, veto_by='clinical' }
        Note over API,DB: 임상 자문 단독 reject (F-7.5.4) — 신학 verdict 무관
    else 양쪽 모두 approve
        API->>DB: UPDATE content_versions SET status='published'
        API->>API: 콘텐츠가 사용자에게 노출 시작
        Note over API: 항상 "AI 보조" footer 자동 첨부 (F-4, F-7)
    else 한쪽이라도 request_changes
        API->>DB: UPDATE content_versions SET status='changes_requested'
        Author->>API: POST /api/content/versions/{id}/revisions { body, references[] }
        Note over Author,API: revisions 흐름은 검토 첫 라운드와 동일 (양쪽 다시 검토)
    else 신학 reject / 임상 OK
        Note over API,DB: 신학 우선 — 콘텐츠 정체성 보호 (F-7.5.7)
        API->>DB: UPDATE content_versions SET status='rejected'
        API->>DB: INSERT content_quarantine { reason, veto_by='theology' }
    else 신학 OK / 임상 reject
        Note over API,DB: 임상 우선 — 사용자 안전 (F-7.5.7)
        API->>DB: UPDATE content_versions SET status='rejected'
        API->>DB: INSERT content_quarantine { reason, veto_by='clinical' }
    end
```

**Notes**
- `disputed_points` 는 작성자가 *명시적으로* 등록 — "이 부분은 해석 분쟁 가능" 표시.
- **2-of-2 approve 필수 콘텐츠** (F-7.5.8): Theme 11 (예수) 모든 콘텐츠 + trigger_warning=high Scene. 같은 role 의 *두 자문가가 모두* approve.
- 자동 키워드 필터 (영지주의·뉴에이지) 가 1차 필터링 — 리뷰어가 보는 큐에 *이미 깨끗한 후보* 만 도달.
- `references` JSONB 의 PMID 와 `clinical_reviews.referenced_pmids` 가 *cross-check* — 인용 근거 부적절 시 evidence_quality 점수 감점.
- SLA (F-7.5.9): routine 5 영업일 / 고난 narrative 10 영업일 / F-6 안전장치 변경 3 영업일 (긴급).
- **Conflict of interest** (F-7.5.10): 자문가가 본인 작성 콘텐츠 검토 불가 — `reviewer_id ≠ content_versions.created_by` 가드.
- Veto 단독 권한은 *임상 자문* 만 (사용자 안전 우선 원칙). 신학 자문은 *2-of-2 거부* 만 효력.

---

## 6. Gemini fallback 모델 체인 (Python AI Sidecar 내부)

> F-5.4 — `gemini-flash-latest` 가 자주 503 UNAVAILABLE 을 반환하는 운영 사고에 대한 대응. (최근 commit `fca2292` 가 정확히 이 패턴.)

```mermaid
sequenceDiagram
    autonumber
    participant API as Spring Backend
    participant AI as Python AI Sidecar
    participant G1 as Gemini Flash Latest
    participant G2 as Gemini 2.0 Flash
    participant G3 as Gemini 1.5 Flash
    participant DB as PostgreSQL

    API->>AI: POST /classify { text }
    AI->>G1: POST :generateContent
    alt 200 OK
        G1-->>AI: response
        AI->>DB: INSERT llm_usage { model: 'flash-latest', success: true }
    else 503 UNAVAILABLE
        G1-->>AI: 503
        AI->>DB: INSERT llm_usage { model: 'flash-latest', success: false, error: '503' }
        AI->>G2: POST :generateContent (동일 prompt)
        alt 200 OK
            G2-->>AI: response
            AI->>DB: INSERT llm_usage { model: '2.0-flash', success: true, fallback_step: 1 }
        else 503 UNAVAILABLE
            G2-->>AI: 503
            AI->>G3: POST :generateContent
            alt 200 OK
                G3-->>AI: response
                AI->>DB: INSERT llm_usage { model: '1.5-flash', success: true, fallback_step: 2 }
            else 끝까지 실패
                G3-->>AI: 503
                AI-->>API: 503 + degraded marker
                API-->>API: graceful degradation — 사용자에게 *"AI 일시 사용 불가, 정적 콘텐츠 추천"*
            end
        end
    end
    AI-->>API: { result, model_used, fallback_step }
```

**Notes**
- 한 endpoint 당 *최대 3 번* 시도 — 무한 폴백 금지.
- `llm_usage` 에 각 시도의 모델·성공 여부·fallback_step 기록 — 어떤 모델이 가장 안정적인지 사후 분석 가능.
- 끝까지 실패 시 *graceful degradation* — 사용자에게 빈 화면 대신 *정적 콘텐츠* 노출.
- 이 흐름은 **Python 사이드카 안에서 완결** — Spring 입장에선 한 번의 호출이며 fallback 인지 모름.

---

## 7. 사용자 데이터 삭제 요청 (GDPR / 개인정보보호법)

> F-8.2 — 30일 내 처리.

```mermaid
sequenceDiagram
    autonumber
    actor User
    actor Admin
    participant UC as Unity Client / Web
    participant API as Spring Backend
    participant DB as PostgreSQL
    participant Audit as Audit Log Store

    User->>UC: "내 모든 데이터 삭제" 메뉴
    UC->>API: POST /api/user/data/delete { reason }
    API->>DB: INSERT data_deletion_requests { user_id, requested_at, reason, status='pending' }
    API->>DB: UPDATE users SET deleted_at=now() (soft delete — 30일 grace)
    API-->>UC: { ticket_id, eta: '30 days', cancelable_until }

    Note over User,API: 30일 동안 사용자는 본인 데이터 접근 차단,<br/>관리자는 보류 큐에서 확인

    alt 사용자가 cancel
        User->>API: POST /api/user/data/delete/{ticket_id}/cancel (cancelable_until 이내)
        API->>DB: UPDATE data_deletion_requests SET status='cancelled'
        API->>DB: UPDATE users SET deleted_at=NULL
        API-->>UC: { restored: true }
    else 30일 경과
        Note over API,DB: 배치 잡 (매일 03:00 KST)
        API->>API: scan pending requests WHERE requested_at < now() - 30 days
        API->>DB: BEGIN TX
        API->>DB: DELETE diary_entries WHERE user_id=...
        API->>DB: DELETE emotion_logs WHERE user_id=...
        API->>DB: DELETE game_sessions WHERE user_id=...
        API->>DB: DELETE scene_views WHERE user_id=...
        API->>DB: DELETE recovery_metrics WHERE user_id=...
        API->>DB: DELETE devices WHERE user_id=...
        API->>DB: DELETE users WHERE id=...
        API->>DB: UPDATE data_deletion_requests SET status='completed'
        API->>Audit: WRITE audit log (user_id_hash, completed_at) — *PII 제외*
        API->>DB: COMMIT
        Admin->>Admin: 처리 결과 알림 수신
    end
```

**Notes**
- *Soft delete* 30일 grace 기간 — 실수 클릭 방지.
- 삭제 후 *익명 통계* 는 유지: 어떤 Theme 이 인기 있었는지 등. 단 `user_id` 는 hash 처리.
- `pgcrypto` 로 암호화된 row 도 함께 삭제 — 키 폐기로는 부족 (실제 row 제거).
- 30일 grace 동안에도 데이터 접근은 차단 (해당 사용자에게).

---

## 8. 시퀀스 다이어그램 작성 컨벤션

다음 다이어그램을 추가할 때는 같은 컨벤션을 유지한다.

| 컨벤션 | 규칙 |
|---|---|
| **autonumber** | 항상 켠다 — 사후 토론 시 step 번호로 참조 가능 |
| **actor** | 사람 / Admin 봇은 `actor`, 시스템 컴포넌트는 `participant` |
| **rect** | Scene·Phase 묶음에 사용 — 다른 색조로 그룹 시각화 |
| **alt / else / loop** | 분기 / 반복 명시. *항상* notes 하단에 엣지 케이스 설명 |
| **Notes (하단)** | 다이어그램 *외부* 의 의사결정 / 운영 노트는 본문 하단에 |
| **헥사고날 경계 존중** | `Spring Backend` 는 LLM 모델 이름을 호출하지 않음 — 항상 `Python AI Sidecar` 경유 |

추가 후보 (작성 미정):
- 신학 리뷰어 임명 / 권한 위임 흐름
- B2B 교회 단체 구독 결제 흐름 (Phase 3+)
- 익명 기도 매칭 흐름 (Phase 3+)
- 다국어 번역 캐시 흐름 (Phase 3+)
