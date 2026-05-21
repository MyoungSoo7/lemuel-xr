# 기능 명세서 — Lemuel XR

> **버전**: 0.1 (Phase 0 — 설계 단계, 구현 X)
> **범위**: 트랙 A (Theme 1~7) + 트랙 B (Theme 8~11) + 3차원 진입 + 정신건강 안전장치 + 신학 검증
> **상위 문서**: [`PLAN.md`](PLAN.md), [`BUILD-PLAN.md`](BUILD-PLAN.md), [`DB-SCHEMA.md`](DB-SCHEMA.md), [`MVP-JOSEPH.md`](MVP-JOSEPH.md), [`TRACK-A-1-4-WISDOM-EMOTION.md`](TRACK-A-1-4-WISDOM-EMOTION.md)
> **시퀀스 다이어그램**: [`SEQUENCE-DIAGRAMS.md`](SEQUENCE-DIAGRAMS.md)

---

## 0. 한 줄 정의

성경 인물·지혜를 매개로 **감정 회복(트랙 A 정적)** 과 **자기 서사화(트랙 B 게임)** 을 양방향 제공하는 AI 기반 영적 웰빙 XR 앱.

---

## 1. 사용자 역할

| 역할 | 식별 | 권한 |
|---|---|---|
| **Guest** | device fingerprint | 트랙 A 전부, 트랙 B 1편 (요셉), 일기 90일 보존 |
| **OAuth User** | Google / Kakao sub | 트랙 B 전체, 무제한 보존, 다기기 동기화, 구독 결제 |
| **Theology Reviewer** | OAuth + role=`theology_reviewer` | AI 출력 검토·승인·차단 |
| **Admin** | OAuth + role=`admin` | 사용자 데이터 삭제 요청 처리, 콘텐츠 버전 관리, 신학 리뷰어 임명 |

---

## 2. 기능 영역

### F-1. 감정 입력 / 분류

| ID | 기능 | 입력 | 출력 | 비고 |
|----|---|---|---|---|
| F-1.1 | 자유 텍스트 감정 입력 | 1~500자 텍스트 | 정규화된 emotion log row | 자해 키워드 즉시 F-6.1 트리거 |
| F-1.2 | 감정 라벨 8종 선택 + 강도 슬라이더 | 라벨(불안/슬픔/분노/외로움/기쁨/평온/감사/혼란) + 1~10 | emotion log row | 가장 빠른 진입 — LLM 호출 0회 |
| F-1.3 | LLM 기반 감정 분류 | F-1.1 의 텍스트 | Theme 1~7 중 1~2개 추천 | Gemini Flash, fallback 체인 적용 (F-5.4 참고) |
| F-1.4 | 진입 모드 선택 | 영성 / 감성 / 이성 / 자동 | UX 분기 + 콘텐츠 톤 변경 | 사용자가 `users.preferred_mode` 에 저장 가능 |

### F-2. 트랙 A — 정적 회복 콘텐츠 (Theme 1~7)

| ID | Theme | 주요 콘텐츠 | LLM 활용 |
|----|---|---|---|
| F-2.1 | 1. 일기와 묵상 | 자유 형식 / 5W1H / 감정 라벨 + 강도 | AI 거울 모드 (시각 호흡 표시) + 묵상 변환 (옵션) |
| F-2.2 | 2. 잠언과 지혜 | 상황별 잠언 카드 + 해석 | 잠언 매칭 (RAG), 해석 LLM |
| F-2.3 | 3. 전도서와 인생 | 인생관 카드 + 시점 전환 | 사용자 입력 → 전도서 절 매칭 (RAG) |
| F-2.4 | 4. 시편과 감정 | 감정 → 시편 매핑, 낭독(TTS) | 감정 라벨링 + TTS |
| F-2.5 | 5. 고통과 진리 | 욥기·고난 서사 + 신학 해설 | RAG + 해설 LLM |
| F-2.6 | 6. 마음을 지키는 것 (잠언 4:23) | 마음 보호 가이드 + 호흡 | 호흡 패턴 1종 + LLM 멘트 |
| F-2.7 | 7. 사람을 두려워하지 않는 것 (이사야 41) | 다윗·다니엘 사례 + 실천 팁 | 사례 RAG + 실천 팁 LLM |

각 Theme 은 **영성·감성·이성 3차원 framework** 으로 분기 — 사용자가 같은 시편 23편을 만나도 진입 모드에 따라 다른 묵상 흐름.

### F-3. 트랙 B — 서사 게임 (Theme 8~11)

| ID | 인물 | 구원 유형 | 미션 분량 | 상태 |
|----|---|---|---|---|
| F-3.1 | 요셉 | 경제 (풍년/흉년 곡식 분배) | 5~7분 | MVP 1순위. Scene 5개. |
| F-3.2 | 모세 | 정치 (홍해 앞 백성 설득) | 6~8분 | Phase 2 |
| F-3.3 | 다윗 | 외세 (골리앗 앞 5개 돌 선택) | 5~7분 | Phase 2 |
| F-3.4 | 예수 | 영적 (광야 시험 / 발 씻기) | 7~10분 | Phase 3, 설계 진행 중 (Issue #3) |

각 미션 공통 구조:
1. Scene 진입 (배경·내레이션·TTS)
2. XR 인터랙션 (손으로 잡기·놓기·선택)
3. 결정 분기 (1~3개)
4. LLM 으로 내면 독백·결말 톤 생성 (캐시 가능)
5. 결말 + 회복 메시지 + 3차원 mapping 카드

### F-4. AI 묵상 변환

| ID | 기능 | 입력 | 출력 |
|----|---|---|---|
| F-4.1 | 일기 → 시편 톤 변환 | 사용자 일기 본문 | 시편 톤 1~2문장 |
| F-4.2 | 감정 → 묵상문 | 감정 라벨 + 강도 | personal devotional 한 단락 |
| F-4.3 | TTS 낭독 | 위 두 출력 | 음성 파일 (선택) |
| F-4.4 | 저장 / 폐기 | 사용자 선택 | DB 또는 즉시 폐기 |

모든 AI 출력은 **"AI 보조 — 본문은 성경 참조"** 표시 필수. 자동 출판 금지.

### F-5. AI 인프라

| ID | 기능 | 모델 / 서비스 |
|----|---|---|
| F-5.1 | LLM 호출 (감정·묵상·해석) | Google Gemini (Flash 우선, Pro fallback) |
| F-5.2 | 임베딩 (성경 RAG) | pgvector 우선, 대용량 시 Pinecone |
| F-5.3 | TTS | ElevenLabs (한국어 음성 자연도 1위) |
| F-5.4 | 503 fallback 모델 체인 | `gemini-flash-latest` → `gemini-2.0-flash` → `gemini-1.5-flash` → 명시 모델로 자동 재시도 |
| F-5.5 | LLM 캐시 | `llm_cache` 테이블 — Scene 별 분기 결과·내면 독백 사전 캐시 |

### F-6. 정신건강 안전장치

| ID | 트리거 | 대응 |
|----|---|---|
| F-6.1 | 자해·자살 키워드 (정규식 + 의미 매칭) | 즉시 위기 자원 카드 노출 (1577-0199 자살예방상담전화 등) |
| F-6.2 | 3일 연속 우울·절망 강도 9+ | "전문가 상담 추천" 카드 노출 + 한국심리상담센터 링크 |
| F-6.3 | AI 응답 톤 제어 | 사용자가 "AI 가 답하지 말기" 토글 가능 (기본 ON) |
| F-6.4 | 십자가·고난 트라우마 자극 | 트랙 B 예수 미션 진입 전 *정서 부담* 경고 카드 |
| F-6.5 | *"고난의 정당화"* 가스라이팅 방지 | 묵상 출력에 *"피해자에게 강요되지 않아야 함"* footer 자동 추가 (Theme 5, 11) |
| F-6.6 | *"부활 메시지의 회복 압박"* 방지 | 우울증·자해 이력 있는 사용자에게는 부활 메시지 톤 자동 완화 |

### F-7. 신학 검증

| ID | 흐름 | 주체 |
|----|---|---|
| F-7.1 | 콘텐츠 버전 등록 | 콘텐츠 작성자 |
| F-7.2 | 신학 리뷰어 검토 | `reviewer_profiles.role='theology'` |
| F-7.3 | 분쟁 가능 지점 명시 | 작성자가 `content_versions.disputed_points` JSONB 에 기록 |
| F-7.4 | 출판 승인 / 거부 / 보류 | 신학 리뷰어 → `theology_reviews.verdict` |
| F-7.5 | 영지주의·뉴에이지 해석 차단 | 자동 키워드 필터 + 리뷰어 수동 검수 |

자동 출판 금지. 모든 LLM 출력은 사용자에게 *"AI 보조"* 표시.

### F-7.5. 임상 검증 — *신학 검증과 병렬*

> **거버넌스 상세**: [`docs/governance/CLINICAL-REVIEW.md`](governance/CLINICAL-REVIEW.md)
> **이슈**: [#4 — 임상자문 영입 (Milstein 2025 COPE 프레임워크)](https://github.com/MyoungSoo7/lemuel-xr/issues/4)
> **DB**: `reviewer_profiles` + `clinical_reviews` + `content_versions."references"` JSONB (V20260521040956)

신학 검증으로는 잡지 못하는 *trauma-informed* / *crisis 자원* / *근거 적절성* 위험을 별도 게이트로 처리. 신학 + 임상 *양쪽 모두 approve* 여야 `content_versions.status='published'`.

| ID | 흐름 | 주체 |
|----|---|---|
| F-7.5.1 | 신학 검토와 *병렬* 임상 큐 진입 | `reviewer_profiles.role='clinical'` 활성 자문가 |
| F-7.5.2 | 임상 체크리스트 4종 (1~5 score) | trauma_safety / crisis_resource_compliance / **moral_injury_risk** (Jones 2022 PMID 35609469 직접 매핑) / evidence_quality |
| F-7.5.3 | 인용 PMID 추적 | 작성자가 `content_versions."references"` JSONB 에 PMID 배열 적재 → 임상 자문이 `clinical_reviews.referenced_pmids` 와 cross-check |
| F-7.5.4 | **Veto 단독 reject 권한** | moral_injury_risk ≤ 2 / 자해 안전망 부재 / consent 게이트 없는 trauma 자극 시 임상 자문 단독 reject (`clinical_reviews.veto_used`) |
| F-7.5.5 | Required review 대상 | F-6 안전장치 변경 / LLM 시스템 프롬프트 변경 / Theme 5·11 고난 narrative / trigger_warning ≥ medium Scene |
| F-7.5.6 | Optional review 대상 | Theme 1~3 routine 콘텐츠 (작성자 요청 시) |
| F-7.5.7 | 결정 불일치 escalation | 신학 OK / 임상 reject → 임상 우선 (사용자 안전). 신학 reject / 임상 OK → 신학 우선 (콘텐츠 정체성) |
| F-7.5.8 | 2-of-2 approve 필수 콘텐츠 | Theme 11 (예수) 모든 콘텐츠 + 모든 trigger_warning=high Scene |
| F-7.5.9 | SLA | Routine 5 영업일 / 고난 narrative 10 영업일 / 안전장치 3 영업일 (긴급) |
| F-7.5.10 | Conflict of interest | 자문가는 본인 작성 콘텐츠 검토 불가 (`reviewer_id ≠ created_by`) |

전체 검토 흐름은 [`SEQUENCE-DIAGRAMS.md`](SEQUENCE-DIAGRAMS.md) §5 (신학·임상 *병렬* 검증) 참고.

### F-8. 사용자 데이터 관리

| ID | 기능 | 비고 |
|----|---|---|
| F-8.1 | 데이터 보존 기간 설정 | `users.data_retention_days` (기본 90, NULL=영구) |
| F-8.2 | 전체 데이터 삭제 요청 | GDPR / 개인정보보호법 — 30일 내 처리 |
| F-8.3 | 일기 row-level 암호화 | `pgcrypto` 적용 |
| F-8.4 | 게스트 → OAuth 변환 시 데이터 이전 | device_fingerprint 매칭 1회 |

### F-9. 인증 / 인가

| ID | 기능 | 비고 |
|----|---|---|
| F-9.1 | 익명 게스트 | device fingerprint 만 — 모든 PII 옵션 |
| F-9.2 | OAuth (Google) | `external_id` = sub claim |
| F-9.3 | OAuth (Kakao) | 한국 시장 1순위 |
| F-9.4 | 신앙 톤 설정 | `faith_tone`: strong / balanced / soft |

---

## 3. 비기능 요구사항

| 영역 | 요구사항 | 목표 값 |
|---|---|---|
| **성능** | API p95 latency | < 800ms (LLM 비포함), < 4s (LLM 포함) |
| **성능** | LLM cache hit rate (Scene 분기) | > 70% |
| **가용성** | API uptime | 99.5% (Phase 0~1), 99.9% (Phase 2+) |
| **신학적 정확성** | LLM 출력 환각 비율 | < 5% (분기별 신학 리뷰어 샘플 검수) |
| **정서 안전성** | 자해 키워드 false negative | < 1% (정규식 + 의미 매칭 이중 게이트) |
| **데이터 보안** | 일기 본문 암호화 | row-level (`pgcrypto`) |
| **다국어** | UI 언어 지원 | Phase 1: ko / Phase 2: en / Phase 3: es, pt |

---

## 4. API 엔드포인트 (요약)

전체 API 는 `BUILD-PLAN.md §3` 의 게이트웨이 라우팅을 따른다.

| Method | Path | 설명 |
|---|---|---|
| `POST` | `/api/emotion/classify` | F-1.3 — 감정 분류 |
| `POST` | `/api/emotion/log` | F-1.1 / F-1.2 — emotion log 적재 |
| `GET` | `/api/content/recommend?emotion=...` | F-2.x — Theme 추천 |
| `GET` | `/api/content/{topicId}/scene?mode=spiritual` | XR Scene 콘텐츠 (영성/감성/이성 분기) |
| `POST` | `/api/diary/entries` | F-2.1 — 일기 작성 |
| `POST` | `/api/diary/entries/{id}/devotional` | F-4.1 — 묵상 변환 |
| `POST` | `/api/game/joseph/start` | F-3.1 — 요셉 게임 세션 시작 |
| `POST` | `/api/game/joseph/decisions` | Scene 결정 적재 |
| `GET` | `/api/scripture/passages?ref=psalm-23` | 성경 본문 조회 |
| `POST` | `/api/scripture/search` | RAG (pgvector) 의미 검색 |
| `GET` | `/api/safety/resources` | F-6 위기 자원 목록 |
| `POST` | `/api/user/data/delete` | F-8.2 — 전체 데이터 삭제 요청 |
| `POST` | `/api/theology/reviews` | F-7.2 — 신학 검토 결과 등록 |

---

## 5. 도메인 모델 (헥사고날 매핑)

```
backend/src/main/java/.../xr/
├── emotion/                             # F-1
│   ├── domain/Emotion.java              # 감정 라벨 enum + 강도 VO
│   ├── application/
│   │   ├── ClassifyEmotionUseCase       # F-1.3 분류 유스케이스
│   │   └── EmotionClassifierService     # 도메인 서비스
│   └── adapter/in/web/EmotionController # /api/emotion/*
│
├── game/                                # F-3
│   ├── domain/GameSession.java          # 게임 세션 aggregate
│   ├── adapter/in/web/JosephGameController  # /api/game/joseph/*
│   └── adapter/out/persistence/
│       ├── GameSessionJpaEntity
│       └── GameSessionRepository
│
├── scripture/                           # 본문 + RAG
│   ├── adapter/in/web/ScriptureController   # /api/scripture/*
│   └── adapter/out/persistence/
│       ├── ScripturePassageEntity
│       └── ScripturePassageRepository
│
├── diary/                               # F-2.1, F-4 (예정)
├── safety/                              # F-6 (예정)
└── theology/                            # F-7 (예정)
```

**의존 방향 (헥사고날 규칙)**
- `domain` → 외부 의존 0
- `application` → domain 만 의존, JPA·Spring 직접 X
- `adapter/in` → application 호출
- `adapter/out` → application 의 port 인터페이스 구현, JPA 등 외부 기술 사용

LLM·TTS·Pinecone 등 외부 서비스 호출은 모두 `adapter/out` 으로 추출. 도메인이 LLM 모델 이름을 모르도록 유지.

---

## 6. 데이터 모델 요약

자세한 스키마는 [`DB-SCHEMA.md`](DB-SCHEMA.md) 참고.

| 도메인 | 핵심 테이블 |
|---|---|
| IDENTITY | `users`, `devices`, `sessions` |
| EMOTION | `emotion_logs`, `emotion_dimensions`, `recovery_metrics` |
| GAME | `game_sessions`, `game_decisions`, `scene_views` |
| CONTENT | `diary_entries`, `user_psalms`, `proverbs_interactions` |
| SCRIPTURE | `scripture_passages`, `scripture_embeddings` |
| SAFETY | `safety_alerts`, `crisis_resources_log` |
| THEOLOGY | `theology_reviews`, `content_versions` |
| AI | `llm_cache`, `llm_usage` |

---

## 7. 외부 통합

| 외부 | 용도 | 호출 위치 |
|---|---|---|
| **Google Gemini API** | F-5.1 LLM 호출 (Python AI 사이드카 경유) | `adapter/out/llm/GeminiClient` |
| **ElevenLabs** | F-5.3 TTS | `adapter/out/tts/ElevenLabsClient` |
| **pgvector / Pinecone** | F-5.2 RAG | `adapter/out/embedding/*` |
| **OAuth (Google, Kakao)** | F-9 인증 | Spring Security OAuth2 |
| **Unity Client** | XR Frontend | gRPC 또는 REST (Phase 결정) |

---

## 8. MVP 범위 (V1.0)

`PLAN.md §7` 의 V1.0 정의를 명세 차원으로 옮기면:

- F-1: 1.1, 1.2 (라벨 + 텍스트 입력)
- F-2: 1, 2, 3, 4, 5 (트랙 A 5종)
- F-3: 1 (요셉만)
- F-4: 1, 2 (묵상 변환)
- F-5: 1, 5 (Gemini + cache)
- F-6: 1, 2 (자해 키워드 + 우울 누적 알림)
- F-7: 1, 2, 4 (콘텐츠 등록 + 신학 리뷰 + 출판 결정)
- F-8: 1, 2, 3
- F-9: 1, 2 (게스트 + Google)

**제외**: 트랙 B 모세·다윗·예수, ElevenLabs TTS, 다국어, B2B, 익명 기도 매칭.
