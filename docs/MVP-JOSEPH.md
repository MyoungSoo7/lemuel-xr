# MVP — 요셉 XR 미션 상세 설계

## 0. 왜 요셉이 MVP 로 적합한가

| 평가 항목 | 요셉 | 다윗 | 모세 | 예수 |
|---|---|---|---|---|
| XR 인터랙션 자연스러움 | ⭐⭐⭐ 곡식 자루 잡고 분배 | ⭐⭐ 돌 5개 선택 | ⭐ 군중 설득 (UI 한계) | ⭐⭐ 발 씻기 |
| 결정 분기 명확함 | ⭐⭐⭐ 저장량·분배 우선순위 | ⭐⭐ 무기 선택 | ⭐⭐ 길 선택 | ⭐ 추상적 |
| 짧은 미션 (5~10분) 가능 | ⭐⭐⭐ | ⭐⭐⭐ | ⭐ 서사 길어짐 | ⭐⭐ |
| 신학적 부담 (해석 분쟁) | ⭐⭐⭐ 낮음 | ⭐⭐ 중간 | ⭐⭐ | ⭐ 가장 큼 |
| 시각화 비용 (에셋) | ⭐⭐⭐ 곡식·창고·이집트 분위기 — Asset Store 풍부 | ⭐⭐ 광야 단순 | ⭐⭐⭐ 홍해 어려움 | ⭐⭐⭐ 갈릴리 어려움 |

**합계 요셉이 1순위 맞음.** Phase 2 에서 다윗 → 모세 → 예수 순으로 확장.

---

## 1. 게임 컨셉 — "곡식 7년"

7년의 풍년 동안 곡식을 모으고, 7년의 흉년 동안 분배하는 결정을 압축해서 체험.

### 핵심 메시지
> *"지금의 풍요가 영원하지 않다는 자각이, 미래의 굶주리는 자를 살린다."*
> — 요셉의 통찰을 사용자가 *결정* 으로 체감

### 미션 구조 (5~7분)

```
Scene 1: 파라오 꿈 해석 (1분, 영상 + 내레이션)
   ↓
Scene 2: 풍년기 — 곡식 저장 결정 (1~2분, XR 인터랙션)
   ↓
Scene 3: 흉년기 — 분배 결정 (2~3분, XR 인터랙션, 핵심)
   ↓
Scene 4: 형제와 재회 (1분, 영상 + 선택)
   ↓
Scene 5: 결말 + 회복 메시지 (30초)
```

---

## 2. Scene 별 디테일

### Scene 1: 파라오 꿈 해석
- 사용자는 *어두운 궁전 안* 에 서 있음
- 파라오가 꿈을 이야기함 (TTS 또는 사전 녹음)
- 7마리 마른 소가 7마리 살찐 소를 삼키는 장면이 *XR 공중* 에 떠 있음 (애니메이션)
- 사용자는 "꿈 해석 책" 을 손으로 잡고 펼침
- 책의 페이지가 *7년 풍년 / 7년 흉년* 문구로 채워짐
- 진행: 자동 다음 scene

**기술**: 사전 만든 애니메이션 + Skinned Mesh Renderer. LLM 호출 없음.

---

### Scene 2: 풍년기 — 저장 결정 (핵심 인터랙션 #1)

배경: 황금빛 보리밭. 멀리 *7개의 곡식 창고* 가 세워지는 중.

UI: 사용자 앞에 *곡식 자루 3개* 가 떠 있음.
- 자루 A — "1/5 저장"
- 자루 B — "1/3 저장" (요셉이 실제 한 비율과 비슷)
- 자루 C — "1/2 저장"

사용자는 손으로 자루를 잡고 *창고* 에 넣음. 각 선택에 따라 다음 scene 에 남는 곡식 양이 바뀜.

**LLM 호출**: 사용자가 자루를 잡았을 때 *요셉의 내면 독백* 을 LLM 으로 생성 (서버 사이드, 캐시 가능). 예시 프롬프트:
```
당신은 청년 요셉입니다. 파라오의 꿈을 해석한 직후,
이집트 전체 곡식의 [PERCENTAGE]% 를 저장하기로 결정했습니다.
당신의 내면 독백을 한국어 2~3문장으로 작성하세요.
백성을 향한 책임감과 두려움이 섞여 있어야 합니다.
```
3개 선택 × 1개 독백 = 미리 캐싱해 두면 LLM 호출 0회로도 가능.

---

### Scene 3: 흉년기 — 분배 결정 (핵심 인터랙션 #2 + 게임의 중심)

배경: 누런 사막. 창고 앞에 *3개의 줄* 이 있음.
- 줄 A — 이집트 농민 (수십 명, 굶주린 모습)
- 줄 B — 이주민 가족 (어린이 포함)
- 줄 C — 무역 상인 (대가 지불 가능)

사용자 앞에는 *남은 곡식 자루 N 개* (Scene 2 의 결정에서 계산됨).

규칙:
- 자루는 한 번에 한 줄에만 줄 수 있음
- 모든 자루를 분배하면 scene 종료
- 각 분배 후 *그 줄의 사람들* 이 인사함 (단순 애니메이션)

**분기 결과**:
| 분배 패턴 | LLM 생성 결과 |
|---|---|
| 농민 우선 | "이집트가 살았다. 백성이 너를 기억할 것이다." |
| 이주민 우선 | "야곱의 가문이 너를 통해 살았다. 형제가 너를 다시 만나러 올 것이다." |
| 상인 우선 | "재정은 견고해졌으나 굶는 자들의 원망이 쌓였다. 다시 선택할 기회가 있다." |

이 분기 결과는 사실 *Scene 4 의 시나리오* 가 동일하게 흘러가지만, **요셉의 표정·언어·결말 톤이 다름**.

**LLM 호출**: 1회 (분배 패턴을 받아 결말 톤을 생성). 캐시 가능.

---

### Scene 4: 형제와 재회 (1분)

배경: 궁전. 야곱의 아들들이 곡식 사러 왔음을 모르고 입장.

요셉(NPC) 이 *얼굴을 가리고* 형제들을 바라봄. 사용자에게 선택지 제시:
- A: 정체를 즉시 밝힘 → "이는 하나님이 나를 보내신 것이라"
- B: 잠시 시험함 (베냐민 데려오라 요청)
- C: 침묵 (가장 어려운 선택)

각 선택에 따라 LLM 이 요셉의 대사 한 줄을 생성. 본문 (창세기 45 장 인용) 은 DB 에서 꺼냄.

---

### Scene 5: 결말 + 회복 메시지

사용자는 *어두운 묵상 공간* 으로 복귀.

화면에 한 줄 떠오름:
> *"하나님이 생명을 구원하시려고 나를 너희 앞서 보내셨나니" (창 45:5)*

그 아래 사용자 입력 화면에서 받은 *원래 감정 (예: 불안)* 에 매칭된 회복 문구:
> *"지금의 결핍이 미래의 누군가를 살릴 양식이 될지 모른다."*

미션 종료. 첫 화면 복귀.

---

## 3. XR 인터랙션 포인트

| 인터랙션 | Unity 구현 |
|---|---|
| 곡식 자루 잡기 | `XR Grab Interactable` (XR Interaction Toolkit) |
| 자루를 창고/줄에 던지기 | `XR Socket Interactor` + 충돌 감지 |
| 책 펼치기 | `XR Grab Interactable` + Animator (페이지 flip) |
| 시선 응시 (NPC 와 눈맞춤) | `Eye Gaze` (Quest Pro) or `Head Direction` (Quest 2/3) |
| 텍스트 부유 (본문) | `TextMeshPro` World Space + Billboard 스크립트 |

**Quest 2/3 기준 권장** — Pro 의 Eye Gaze 는 옵션.

---

## 4. 백엔드 API (Spring)

| Endpoint | Method | 역할 |
|---|---|---|
| `POST /api/emotion/classify` | 사용자 텍스트 → 7개 감정 분류 (gpt-4o-mini) |
| `GET /api/content/recommend?emotion=X` | 매핑된 주제 1~2개 반환 |
| `POST /api/game/joseph/start` | 세션 생성 + Scene 1 페이로드 |
| `POST /api/game/joseph/scene/{n}/decide` | 사용자 선택 전송 → 다음 scene 데이터 (LLM 호출 후 캐시) |
| `POST /api/game/joseph/complete` | 완료 + 사용자 감정/결정 기록 |
| `GET /api/scripture/{ref}` | 본문 인용 (창세기 41~45) — DB lookup, LLM 안 씀 |

---

## 5. 데이터 모델 (PostgreSQL)

```sql
-- 사용자 (게스트 허용)
CREATE TABLE users (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP,
    guest BOOLEAN DEFAULT TRUE
);

-- 감정 입력 기록
CREATE TABLE emotion_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id UUID REFERENCES users(id),
    raw_text TEXT,
    classified_emotion VARCHAR(30),
    confidence FLOAT,
    created_at TIMESTAMP
);

-- 게임 세션
CREATE TABLE game_sessions (
    id UUID PRIMARY KEY,
    user_id UUID,
    character VARCHAR(20),   -- 'joseph', 'david', ...
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    decisions JSONB          -- {"scene2_save": "1/3", "scene3_distribute": ["farmer","immigrant"]}
);

-- 본문 (창세기 41~45)
CREATE TABLE scripture_passages (
    id BIGSERIAL PRIMARY KEY,
    reference VARCHAR(50),   -- 'gen-45:5'
    text TEXT,
    embedding vector(3072)   -- pgvector
);

-- LLM 응답 캐시
CREATE TABLE llm_cache (
    cache_key VARCHAR(200) PRIMARY KEY,
    response TEXT,
    created_at TIMESTAMP
);
```

---

## 6. 6주 일정 재조정 (요셉 MVP)

### Week 1 — 인프라 + 본문 시드
- helm-deploy 차트 `xr-project` 추가, ArgoCD 등록
- Spring Boot 4 프로젝트 셋업 (settlement 의 hexagonal 패턴 copy)
- 창세기 41~45 본문 수집 + DB 시드 + 임베딩
- 감정 분류 (`emotion_classify`) 프로토타입

### Week 2 — Joseph API 5개 엔드포인트 + LLM 캐싱
- 5개 게임 API 구현
- LLM 응답 캐시 테이블 + 사전 생성 스크립트
- 단위 테스트 + Postman 컬렉션
- Telegram 봇으로 헬스 알람 연결

### Week 3 — Unity 6 셋업 + Scene 1
- Unity 6 + URP + XR Interaction Toolkit 설정
- Quest 빌드 환경 (Android 빌드 SDK + Meta XR SDK)
- Scene 1 구현 (꿈 해석 — 영상 + 책)
- Asset Store 에서 이집트 분위기 에셋 구매 ($30 내외)

### Week 4 — Scene 2 + Scene 3 (핵심 인터랙션)
- 곡식 자루 잡기 + 창고 넣기 (XR Grab + Socket)
- 분배 줄 인터랙션
- Spring API 연동 (UnityWebRequest)

### Week 5 — Scene 4 + 5 + LLM 통합
- 형제 재회 분기
- 결말 + 회복 메시지
- LLM 호출 통합 + 캐시 동작 확인
- 풀 플로우 테스트

### Week 6 — 통합 테스트 + 데모
- 5~10명 사용자 테스트 (Quest 2/3 보유자)
- 피드백 수집 + 빌드 폴리시
- Phase 2 백로그 정리 (다윗·모세·예수)

---

## 7. 첫 5일 액션 (이번 주)

| Day | 작업 |
|---|---|
| 1 | helm-deploy 에 `charts/xr-project/` 디렉토리 + Chart.yaml + values.yaml + ArgoCD application yaml. Spring Boot 4 프로젝트 git init |
| 2 | PostgreSQL 스키마 + Flyway V1~V3 (users, emotion_logs, game_sessions, scripture_passages) 작성. 창세기 41~45 텍스트 수집 |
| 3 | `POST /api/emotion/classify` Spring AI + OpenAI Function Calling 구현. 7개 감정 enum: 불안·슬픔·분노·혼란·외로움·지침·감사 |
| 4 | `POST /api/game/joseph/start` + `decide` + `complete` 골격. LLM 응답 캐시 테이블. 정적 시나리오 데이터 yaml 로 작성 |
| 5 | Unity 6 프로젝트 생성 + XR Toolkit 임포트 + Meta XR SDK + Quest 2 빌드 환경 셋업 (CardBoard 또는 Quest 시뮬레이터 활용) |

---

## 8. 리소스 + 비용 (요셉 MVP 6주)

| 항목 | 비용 |
|---|---|
| Unity Asset Store (이집트 풍경·곡식 모델·궁전) | $50~100 |
| OpenAI API (LLM 캐싱 적용 시 사용자 100명/월) | $20~50 |
| Meta Quest 2/3 (개발용 본인 디바이스) | (이미 보유 가정) |
| TTS — 파라오 내레이션 (사전 녹음 1회) | ElevenLabs 한 달 $5 |
| 클러스터 추가 부담 | $0 (기존 K3s 재사용) |
| 합계 | **~$100 (1회) + $50/월** |

---

## 9. 사전 결정 필요한 것

1. **타겟 디바이스** — Quest 2 / Quest 3 / Quest Pro / WebXR 중 어느 것 우선?
   - 추천: **Quest 2/3** (Quest Pro 는 Eye Gaze 옵션, WebXR 는 Phase 2)
2. **LLM 호출 시점** — 미션 중 실시간 (지연 1~2초) vs 사전 캐싱 (변형 없음)
   - 추천: **하이브리드** — 시나리오 핵심 분기는 사전 캐시, 사용자 입력 응답만 실시간
3. **TTS 음성** — ElevenLabs (영문 자연, 한국어 약함) vs 사전 녹음 (지인 음성 활용)
   - 추천: **사전 녹음 시작** — MVP 단계에선 단순 + 비용 0
4. **본문 번역본** — 개역개정 / 새번역 / 공동번역?
   - 결정: 사용자 본인 선호
5. **사용자 데이터 익명화** — 게스트 모드만 / 회원가입 옵션 / 익명+로그인 둘 다?
   - 추천: **게스트만 (MVP)**, Phase 2 에서 회원 시스템

---

## 10. 차별화 한 줄 (요셉 MVP 한정)

> *"7년의 풍요와 7년의 흉년을 5분 안에 손으로 경험하는 XR 묵상 게임."*

- 텍스트로 읽으면 30초 — *손으로* 곡식 자루를 옮기면 5분
- 그 5분이 "*내가 풍요로울 때 무엇을 저장해 두는가*" 라는 질문을 사용자 안에 남김

이게 핵심 가치 명제.
