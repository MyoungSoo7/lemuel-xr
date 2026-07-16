# 기독교 묵상 XR 프로젝트 — 빌드 플랜

## 1. 프로젝트 한 줄 정의

사용자의 감정을 입력받아 *성경 본문 + 묵상 공간(XR)* 을 제공하고, 원할 시 구약·신약 인물(요셉·모세·다윗·예수) 서사 기반 짧은 게임 미션으로 확장하는 **감정 회복형 XR 콘텐츠 플랫폼**.

핵심 가치: *수동적 텍스트 콘텐츠* → *공간적 몰입형 묵상* 으로 전환.

---

## 2. 두 가지 사용자 시나리오

### (A) 정적 회복 플로우 (주제 1~7)
> 사용자가 "오늘 너무 불안해" 입력 → AI 가 *불안* 분류 → 관련 주제(시편과 감정, 마음을 지키는 것, 사람을 두려워하지 않는 것 중 1~2개) 추천 → Unity XR 공간에 들어가 음악·내레이션·해당 본문이 흐르는 묵상 공간 체험

| 단계 | 주제군 | 콘텐츠 형태 |
|---|---|---|
| 1 | 일기와 묵상 | 일기 작성 + 본문 인용 |
| 2 | 잠언과 지혜 | 잠언 한 구절 카드 |
| 3 | 전도서와 인생 | 인생 관점 short essay |
| 4 | 시편과 감정 | 감정별 시편 매핑 |
| 5 | 고통과 진리 | 욥기 발췌 + 해설 |
| 6 | 마음을 지키는 것 | 잠언 4:23 중심 묵상 |
| 7 | 사람을 두려워하지 않는 것 | 이사야 41 묵상 |

### (B) 서사 게임 플로우 (주제 8~11)
> 사용자가 "게임 원함" 선택 → AI 가 감정·상황에 맞춰 1명 추천 → 해당 인물의 핵심 결정 순간을 짧은 미션(5~10분)으로 체험

| # | 인물 | 핵심 서사 | 게임 미션 예시 |
|---|---|---|---|
| 8 | 요셉 | 경제적 구원자 | 7년 풍년 곡식 분배 결정 |
| 9 | 모세 | 정치적 구원자 | 홍해 앞 백성 설득 |
| 10 | 다윗 | 외세적 구원자 | 골리앗 앞 5개 돌 선택 |
| 11 | 예수 | 영적 구원자 | 베드로 발 씻기 / 광야 시험 |

---

## 3. 시스템 아키텍처

```
┌──────────────────────────┐
│   Unity Client (XR)      │  ← 묵상 공간 + 인물 미션 게임
│   - Meta Quest / WebXR    │
│   - 감정 입력 UI          │
│   - STT/TTS (선택)         │
└─────┬────────────────────┘
      │ HTTPS / WebSocket
      ↓
┌──────────────────────────┐
│   API Gateway (Spring)    │  ← Spring Cloud Gateway 재사용 가능
│   /api/emotion/*          │
│   /api/content/*          │
│   /api/game/*             │
└─────┬───────────────────┬─┘
      ↓                   ↓
┌─────────────┐   ┌──────────────────┐
│ Emotion     │   │ Content Service   │
│ Classifier  │   │ - 11 주제 콘텐츠   │
│ (OpenAI/    │   │ - 인물 미션 시나리오│
│  Gemini)    │   │ - 서버사이드 렌더링 │
└─────────────┘   └────────┬──────────┘
                           ↓
                    ┌──────────────┐
                    │ PostgreSQL   │  ← 감정 기록 / 콘텐츠 메타
                    │ + pgvector   │  ← 본문 임베딩 (RAG)
                    └──────────────┘
```

### 기술 스택 권장

| 영역 | 기술 | 선택 이유 |
|---|---|---|
| XR 클라이언트 | **Unity 6 (LTS) + XR Interaction Toolkit** | Quest·WebXR·iOS 동시 빌드 가능 |
| Backend | **Spring Boot 4 + Kotlin 2.2.20** (JDK 25 툴체인, 100% Kotlin·Lombok 제거) | 본인 settlement·asat 와 동일 스택 계열. 운영 노하우 그대로 이전 |
| LLM | **OpenAI GPT-4o 또는 Google Gemini** | Spring AI 1.0 로 양쪽 모두 swap 가능 |
| DB | **PostgreSQL 16 + pgvector** | 본인 sparta-msa 와 동일. 본문 의미 검색 즉시 적용 |
| Cache | **Redis** | 세션·동시 사용자 추적 |
| STT/TTS | **OpenAI Whisper + ElevenLabs** (or Azure Speech) | 음성 대화 옵션 — Phase 2 |
| 배포 | **GHCR + ArgoCD + K3s** | 기존 인프라 재사용. helm-deploy 에 차트 추가만 |
| 관측 | **ELK + Telegram ChatOps 봇** | 기존 그대로 |

---

## 4. 기능 분해 + 모듈 매핑

| 기능 | Unity 측 | Spring 측 | LLM/외부 |
|---|---|---|---|
| 감정 입력 UI | 텍스트·이모지 패드 | — | — |
| 감정 분류 | 입력 전송 | `/api/emotion/classify` | OpenAI Function Calling |
| 주제 추천 | 카드 UI 렌더 | `/api/content/recommend?emotion=X` | RAG (pgvector 로 본문 + 주제 매칭) |
| XR 묵상 공간 | Skybox·라이팅·BGM·내레이션 재생 | `/api/content/{topicId}/scene` | TTS (선택) |
| 인물 미션 게임 | 분기 시나리오 + 인터랙션 | `/api/game/{character}/mission` | LLM 으로 대화·서사 생성 |
| 사용자 감정 기록 | — | `/api/emotion/history` | PostgreSQL |
| 음성 대화 | Whisper STT | `/api/conversation` | LLM + TTS |

---

## 5. MVP 범위 vs Phase 2

### MVP (4~6주 목표)
**들어가는 것**
1. 감정 텍스트 입력 (UI)
2. AI 감정 분류 (5~7 카테고리: 불안/슬픔/분노/혼란/외로움/지침/감사)
3. 주제 1~7 중 매핑된 1~2개 추천
4. Unity 묵상 공간 1개 (skybox + BGM + 본문 텍스트 부유)
5. 인물 미션 1개 (예: 다윗 — 골리앗 5개 돌 선택, 5분 분량)

**들어가지 않는 것**
- 음성 대화 (STT/TTS)
- 멀티플레이
- 회원가입·과거 기록 조회 (게스트 모드만)
- 11개 주제·4명 인물 전부 (1+1 부터)

### Phase 2 (8~12주)
- 11개 주제 콘텐츠 모두 작성
- 인물 미션 4개 (요셉·모세·다윗·예수)
- 음성 대화 (Whisper STT + ElevenLabs TTS)
- 회원 시스템 + 감정 기록 history 조회
- WebXR 빌드 (브라우저에서도 동작)

### Phase 3 (보너스)
- 공동체 묵상 (멀티유저 같은 공간)
- 개인 일기장 + AI 코멘트
- iOS/Android native 빌드

---

## 6. 개발 단계 (Sprint Plan, MVP 6주)

### Week 1 — 인프라 + 콘텐츠 골격
- helm-deploy 에 새 차트 `xr-project` 추가 (Spring backend + Postgres)
- ArgoCD application 등록, GHCR 이미지 푸시 파이프라인
- 11개 주제·4명 인물 본문 데이터 수집 + DB 시드
- 감정 → 주제 매핑 룰 초기 설정

### Week 2 — Emotion Classifier + Content API
- `/api/emotion/classify` 구현 (Spring AI + OpenAI Function Calling)
- `/api/content/recommend` 구현 (감정→주제 lookup + pgvector 보조)
- 본문 임베딩 색인 (sparta-msa 의 RAG 패턴 그대로)
- 단위 테스트 + Telegram ChatOps 봇으로 헬스 체크

### Week 3 — Unity 클라이언트 셋업
- Unity 6 + URP + XR Interaction Toolkit 프로젝트 생성
- 감정 입력 UI (3D Canvas)
- 첫 묵상 공간 (skybox + 직접 모델링 안 하고 Unity Asset Store 활용)
- Spring API 연동 (UnityWebRequest)

### Week 4 — XR 통합 + 묵상 콘텐츠
- Meta Quest 빌드 + 실기기 테스트 (또는 WebXR)
- 본문 텍스트를 3D 공간에 자연스럽게 배치 (TextMeshPro World Space)
- BGM·내레이션 재생
- 사용자 입력 → 분류 → 묵상 공간 진입 풀 플로우

### Week 5 — 인물 미션 게임 1개
- 다윗 골리앗 미션 (시작·선택·결말 3 노드 분기 시나리오)
- LLM 으로 다윗 내면 독백 생성 (런타임 호출)
- 미션 완료 → 회복 메시지 → 첫 화면 복귀

### Week 6 — 통합 테스트 + 데모
- E2E 시나리오 테스트 (Playwright 가 아닌 Unity Test Framework + Spring Postman/RestAssured)
- 첫 데모 사용자 5~10명 인터뷰
- 개선 사항 도출 + Phase 2 백로그 정리

---

## 7. 기술 위험 & 대응

| 위험 | 영향 | 대응 |
|---|---|---|
| LLM API 비용 폭증 | 사용자당 월 $5+ 가능 | 응답 캐싱 (Redis), 감정 분류는 작은 모델로 (gpt-4o-mini), 인물 미션 서사는 사전 생성 후 LLM 으로 분기만 |
| LLM hallucination (성경 본문 왜곡) | 신뢰성 치명 | 본문 인용은 *DB 에서만 불러옴*, LLM 은 본문 자체를 생성하지 않음 (해설·내면 독백만). RAG 가드레일 필수 |
| Quest 헤드셋 보유율 낮음 | 사용자 진입 장벽 | WebXR 빌드 병행 — 데스크탑·모바일 브라우저에서도 비-XR 모드 제공 |
| 묵상 공간 에셋 제작 시간 | Unity 신규 학습 곡선 | Asset Store 유료 에셋 활용 (Sky Studio · Nature Pack 등 $20~50). 처음부터 자체 모델링 X |
| 감정 분류 정확도 부족 | 추천 주제 어긋남 | 사용자 피드백 ("이 주제 맞아요?") 수집 → fine-tune 데이터로 축적. 초기엔 규칙 기반 + LLM 하이브리드 |
| STT/TTS 지연 | UX 단절 | Phase 2 로 미루기. MVP 는 텍스트 only |

---

## 8. 일정 + 리소스 예상

| Phase | 기간 | 작업 비율 |
|---|---|---|
| MVP | 6주 | Backend 40% / Unity 50% / DevOps 10% |
| Phase 2 | 추가 8주 | Backend 30% / Unity 40% / 콘텐츠 작성 25% / DevOps 5% |
| Phase 3 | 추가 12주+ | 멀티유저·iOS native·고도화 |

**단일 개발자 가정** (본인 + 콘텐츠 자문 1명)
- 콘텐츠 자문: 11개 주제 본문 선정 + 4명 인물 미션 시나리오 검증 (목사 또는 신학생급)
- 디자인: Unity Asset 활용 우선, 필요 시 외주

---

## 9. 인프라 비용 예상

| 항목 | 월 비용 (MVP 사용자 100명 기준) |
|---|---|
| OpenAI API (gpt-4o-mini 분류 + gpt-4o 서사) | $50~100 |
| K3s 클러스터 추가 부담 | $0 (기존 인프라 재사용) |
| Cloudflare R2 (이미지/오디오 자산) | $5 이하 |
| 도메인 (xr.lemuel.co.kr 같은 sub) | $0 (기존 lemuel.co.kr 활용) |
| 합계 | **~$100/월** |

Phase 2 (사용자 1,000명 + 음성 대화) 시 LLM·STT/TTS 비용이 $500~1000/월 까지 올라갈 수 있음. 이때 자체 모델 fine-tune 검토.

---

## 10. 다음 액션

1. **본문 콘텐츠 정리** (1주) — 11개 주제 × 3~5 구절·해설 작성. 콘텐츠 자문 섭외
2. **MVP 차트 셋업** (3일) — helm-deploy 에 `xr-project` 추가, ArgoCD 앱 등록
3. **Emotion Classifier 프로토타입** (3일) — Spring Boot + OpenAI Function Calling, 7개 감정 분류 정확도 70%+ 검증
4. **Unity 학습 + 첫 빌드** (5일) — XR Interaction Toolkit 튜토리얼, 첫 묵상 공간 mockup
5. **다윗 미션 시나리오 작성** (2일) — 3 노드 분기 → LLM 프롬프트 설계

---

## 11. 차별화 포인트 — 면접·소개용 한 줄

> "감정을 입력하면 성경 본문이 *공간* 으로 변하는 XR 묵상 플랫폼. Spring + Unity + OpenAI 의 한국형 기독교 디지털 헬스케어."

기존 묵상 앱(빛과 소금 같은 텍스트 중심) 대비:
1. *공간 몰입* 으로 짧은 시간에 깊은 회복
2. *AI 매칭* 으로 현재 감정에 정확히 들어맞는 본문
3. *서사 게임* 으로 일회성 콘텐츠가 아닌 반복 방문 동기

기존 명상 앱(Calm·Headspace 등) 대비:
- 기독교 본문 자체가 가진 *해석의 깊이* — generic 명상 앱은 못 다루는 영역
