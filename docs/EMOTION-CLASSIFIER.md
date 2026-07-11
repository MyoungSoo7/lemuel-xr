# EMOTION-CLASSIFIER — 사용자 감정 입력 → 분류·추천 알고리즘

> **역할**: 사용자가 *"오늘 너무 지쳤어"* 같은 자유 텍스트 → 정해진 감정 라벨 + 강도 + 추천 콘텐츠 출력. *모든 사용자 진입의 첫 AI 게이트*.

---

## 0. 입출력 계약

### Input
```json
{
  "raw_text": "오늘 너무 지쳤어. 사람들이 다 무서워.",
  "user_id": "uuid-...",
  "user_preferred_mode": "spiritual" | "emotional" | "rational" | null,
  "recent_emotions_30d": ["불안","외로움","불안"]  // 최근 패턴
}
```

### Output
```json
{
  "primary_emotion": "두려움",
  "secondary_emotion": "지침",
  "intensity": 7,
  "confidence": 0.86,

  "risk_flags": ["repeated_negative"],     // 또는 [] 또는 ["self_harm","suicide"]
  "crisis_level": "none",                  // 'none' | 'low' | 'medium' | 'high' | 'critical'

  "recommended_dimension": "emotional",    // 영성·감성·이성 추천 모드
  "recommended_track": "A",                // 'A' (정적) | 'B' (게임)
  "recommended_content": [
    {"type":"psalm","ref":"42","reason":"외로움+지침"},
    {"type":"theme","ref":"7","reason":"사람 두려움"},
    {"type":"mission","ref":"david","reason":"두려움 + 작음"}
  ],
  "fallback_used": false
}
```

---

## 1. 감정 라벨 체계

### 1차 라벨 (10개) — *모든 트랙 진입 분기 기준*

| 라벨 | 영문 | 활성화 |
|---|---|---|
| 불안 | anxiety | 미래 위협 |
| 슬픔 | sadness | 상실 |
| 분노 | anger | 침해 |
| 외로움 | loneliness | 관계 결핍 |
| 두려움 | fear | 즉각 위협 |
| 지침 | exhaustion | 자원 고갈 |
| 혼란 | confusion | 의미·결정 어려움 |
| 무자격감 | inadequacy | 자기비판 |
| 기쁨 | joy | 긍정적 활성 |
| 감사 | gratitude | 긍정적 안정 |

### 2차 라벨 (서브 감정, ~30개)

primary 안에서 더 세분화. 예: `불안 → {예기 불안, 사회 불안, 분리 불안, 공황}`

→ 1차로 분기 결정, 2차로 콘텐츠 미세 추천.

---

## 2. 분류 알고리즘 — 3단 폭포

```
사용자 입력
     │
     ├──▶ Step 1: 안전 게이트 (즉시·간단)
     │     - 자해·자살·살해 키워드 정규식 매칭
     │     - 매칭 시 즉시 crisis_level=critical
     │     - 매칭되면 분류 건너뛰고 위기 자원만 노출
     │
     ├──▶ Step 2: LLM 분류 (gpt-4o-mini)
     │     - Structured Output (Pydantic schema)
     │     - 1차·2차 라벨 + 강도 + confidence
     │     - 60ms p50, $0.0001/회
     │
     ├──▶ Step 3: 키워드 fallback (LLM 실패 시)
     │     - 사전 정의 키워드 사전 → 단순 매칭
     │     - confidence 항상 0.5 로 보수적
     │
     └──▶ Step 4: 추천 매핑
           - 1차 라벨 → 트랙 A/B 분기 룰
           - 2차 라벨 → 콘텐츠 ref 선택
           - 사용자 최근 30일 패턴 → 가중치
```

---

## 3. Step 1 — 안전 게이트 (Crisis Keyword)

### 키워드 사전 (한국어 + 영문)

```python
CRISIS_KEYWORDS_CRITICAL = [
    # 자살 명시
    "자살", "죽고 싶", "죽고싶", "스스로 목숨", "목숨을 끊", "끝내고 싶",
    "suicide", "kill myself", "end my life",
    # 자해 명시
    "자해", "베이고 싶", "긋고 싶",
    "self harm", "cutting myself",
]

CRISIS_KEYWORDS_HIGH = [
    # 간접 표현
    "사라지고 싶", "없어지고 싶", "그만 살고 싶", "버티기 힘들",
    "want to disappear", "can't go on",
]

CRISIS_KEYWORDS_MEDIUM = [
    # 위험 시그널
    "혼자 살아", "아무도 모를", "마지막", "유서",
]
```

### 매칭 후 분기

| crisis_level | 즉시 액션 |
|---|---|
| `critical` | 분류 skip. 즉시 위기 자원 4종 노출 (1577-0199 등). 일반 콘텐츠 추천 X |
| `high` | 분류 수행 + 위기 자원 *상단 배너*. 추천 콘텐츠도 함께 |
| `medium` | 분류 수행 + *조용한* 위기 자원 카드 (하단) |
| `low` / `none` | 일반 흐름 |

**중요**: critical 키워드 매칭 시 *LLM 호출 자체를 안 함*. 사용자 데이터를 외부 API 로 전송하지 않음 (프라이버시).

---

## 4. Step 2 — LLM 분류 (Primary)

### Prompt 템플릿 (v1.0)

```
<system>
당신은 한국어 감정 분류 전문가입니다. 사용자의 자유 텍스트를 분석해
다음 10개 감정 중 가장 두드러진 것 1~2개를 선택하고 강도(1~10)를 매깁니다.

감정 라벨: [불안, 슬픔, 분노, 외로움, 두려움, 지침, 혼란, 무자격감, 기쁨, 감사]

규칙:
1. 정확히 가장 강한 감정 1개를 primary 로 선택
2. 충분히 다른 감정이 함께 있으면 secondary 1개 추가, 아니면 null
3. 강도는 1(미약)~10(극심) — 표현의 강도와 어휘 선택 기준
4. confidence 는 0.0~1.0 — 텍스트가 짧거나 모호하면 낮게
5. 종교적·신학적 해석 금지. *심리적 라벨링만*.
6. PII (이름·전화·주소 등) 가 포함되어 있으면 응답에 포함하지 않음.

출력: JSON 만, 다른 설명 없음.
</system>

<user>
입력: "{raw_text}"

JSON schema:
{{
  "primary": "불안|슬픔|분노|외로움|두려움|지침|혼란|무자격감|기쁨|감사",
  "secondary": "...(같은 목록)... | null",
  "intensity": 1~10,
  "confidence": 0.0~1.0,
  "secondary_label_detail": "선택사항: 더 세분화된 표현 (예: '예기 불안', '사회 불안')"
}}
</user>
```

### Model 선택

| 시나리오 | Model | 비용 (1000 입력 토큰 + 100 출력) |
|---|---|---|
| 기본 | **gpt-4o-mini** | $0.000150 |
| Fallback | gpt-3.5-turbo | $0.0005 |
| 비용 절감 | Claude Haiku | $0.000125 |

→ **gpt-4o-mini 기본**. 월 30,000 콜 = **$4.5/월** (사용자 100명 × 일 10콜).

### Temperature·결정성

- `temperature=0.1` — 분류는 *결정적* 이어야 함
- `top_p=0.9`
- `seed=42` (재현 가능)

### Structured Output 강제

```python
# OpenAI Function Calling 또는 response_format json_schema
response_format = {
  "type": "json_schema",
  "json_schema": {
    "name": "emotion_classification",
    "schema": {
      "type": "object",
      "properties": {
        "primary": {"enum": [...10개...]},
        "secondary": {"oneOf": [{"enum":[...]}, {"type":"null"}]},
        "intensity": {"type":"integer","minimum":1,"maximum":10},
        "confidence": {"type":"number","minimum":0,"maximum":1}
      },
      "required": ["primary","intensity","confidence"]
    },
    "strict": true
  }
}
```

→ LLM 이 *enum 외 값 못 반환*. parsing 오류 방지.

---

## 5. Step 3 — Keyword Fallback (LLM 실패 시)

### 트리거 조건
- LLM 호출 timeout (>5초)
- API 에러 (rate limit, server error)
- confidence < 0.4 (LLM 자체가 모르겠다)

### 단순 키워드 사전

```python
EMOTION_KEYWORDS = {
    "불안": ["불안", "걱정", "초조", "긴장", "두근", "안절부절"],
    "슬픔": ["슬프", "우울", "눈물", "허전", "비통", "절망"],
    "분노": ["화나", "짜증", "열받", "분노", "원망"],
    "외로움": ["외로", "혼자", "고독", "쓸쓸"],
    "두려움": ["무서", "겁", "두려"],
    "지침": ["지치", "피곤", "힘들", "지쳐", "기진"],
    "혼란": ["혼란", "헷갈", "모르겠", "어떻게"],
    "무자격감": ["자격 없", "내가 뭐", "잘못", "부족"],
    "기쁨": ["기쁘", "행복", "신나", "좋"],
    "감사": ["감사", "고마"],
}
```

매칭: 단어 등장 횟수 → 최다 라벨 선택. confidence 항상 `0.5`. 강도 = `5` (기본).

→ *최후 보루*. UX 저하 일부 인정.

---

## 6. Step 4 — 추천 매핑

### 6.1 1차 라벨 → 트랙 A/B 분기

| 1차 감정 | 추천 트랙 | 이유 |
|---|---|---|
| 불안 | A → 시편 + 일기 | 진정 우선 |
| 슬픔 | A → 시편 (탄식) | 정서 인증 |
| 분노 | A → 시편 (저주 시편 X) + 일기 | 토로 |
| 외로움 | A → 시편 42편 + Theme 7 | 함께 인식 |
| 두려움 | B → 다윗 미션 (선택지) | *능동적 회복* |
| 지침 | A → Theme 6 (마음 지킴) | 수동 회복 |
| 혼란 | A → 잠언 카드 | 인지 재구조화 |
| 무자격감 | B → 모세 미션 (선택지) | *능동적 회복* |
| 기쁨 | A → 시편 (찬양) | 강화 |
| 감사 | A → 일기 (감사 일기) | 강화 |

**룰**: *부정 강한 감정* 은 A (진정), *능동 회복 가능한 감정* 은 B (게임).

**예외**: 사용자가 *이전에 B 게임 완주 경험* 있으면 B 추천 가중치 ↑.

### 6.2 사용자 최근 30일 패턴 가중치

```python
recent_emotions = ["불안","불안","외로움","불안"]   # 최근 7일 입력

# 같은 감정 반복 3회+ → 다른 콘텐츠 추천 (포화 방지)
if recent_emotions.count(primary) >= 3:
    # 같은 시편·일기 반복 노출 X. 다른 주제 (이성 모드) 추천.
    boost_dimension("rational")
    diversify_content_ref()
```

### 6.3 사용자 진입 모드 추정

사용자가 `preferred_mode` 미설정 시 텍스트 톤으로 추정:

| 텍스트 톤 | 추정 모드 |
|---|---|
| *"왜 이런 일이 일어났는지"* 의미 질문 | 영성 (spiritual) |
| 감정 단어 중심 *"슬퍼", "외로워"* | 감성 (emotional) |
| 문제 분석 *"어떻게 해결해야"* | 이성 (rational) |

→ LLM 에 추가 prompt 로 `recommended_dimension` 요청.

---

## 7. Edge Cases

| 케이스 | 대응 |
|---|---|
| **빈 입력** | 분류 skip. *"오늘 어떤 마음이세요?"* 8개 감정 버튼 노출 |
| **너무 짧음 (5자 미만)** | confidence 자동 ↓. 사용자에게 *"좀 더 풀어쓰실래요?"* (선택) |
| **영어/일본어 입력** | LLM 다국어 지원. 단 한국어 라벨로 매핑 |
| **비속어·욕설** | 정상 처리. *분노 강도 +2*. 결과에서 비속어 자체는 제거 |
| **PII (이름·전화·주소)** | LLM prompt 에 "PII 응답에 포함 금지" 명시. 저장 시 마스킹 |
| **여러 감정 혼재** | secondary 활용. UI 에 *"여러 감정이 함께 있네요"* 메시지 |
| **위트 / 농담** | 강도 낮게, confidence 낮게. 추천 콘텐츠도 *가벼운* 톤 |
| **무의미 입력 ("ㅁㄴㅇㄹ")** | LLM 이 *"입력을 이해 못 함"* 응답 → 빈 입력 흐름 |

---

## 8. 성능·비용·캐싱

### 8.1 응답 시간 목표

| 단계 | 목표 (p95) |
|---|---|
| 안전 게이트 | < 10ms |
| LLM 분류 | < 2초 |
| 추천 매핑 | < 50ms |
| **전체** | **< 2.5초** |

### 8.2 캐싱 전략

| 캐시 대상 | 키 | TTL |
|---|---|---|
| LLM 응답 (동일 텍스트) | sha256(raw_text) | 24h |
| 추천 콘텐츠 (감정 → ref) | `recommend:{primary}:{intensity}` | 1h (사용자별 X) |
| 키워드 사전 자체 | — | 영구 (앱 메모리) |

→ 같은 사용자가 *유사 텍스트* 입력 시 cache hit. 예상 hit rate **30~40%**.

### 8.3 비용 예측

| 사용자 규모 | 월 LLM 콜 | 비용 |
|---|---|---|
| 100 명 × 일 10콜 | 30,000 | **$4.5** |
| 1,000 명 × 일 5콜 | 150,000 | **$22.5** |
| 10,000 명 × 일 3콜 | 900,000 | **$135** |

→ 캐싱 40% hit 시 모든 수치 60% 로.

---

## 9. 학습·개선 루프

### 9.1 라벨링 품질 측정

- 사용자가 *"추천이 안 맞아요"* 피드백 → `mismatch` 라벨
- 매주 mismatch 100건 샘플링 → 신학·심리 자문 검토
- prompt 버전 (v1.0 → v1.1) 으로 개선

### 9.2 데이터 (개인정보 제거)

- `emotion_logs` 의 *raw_text + classified* 매칭 — 익명화 후 분석
- *어떤 표현* 이 *어떤 라벨* 로 가는지 통계
- 매월 *prompt 개선 후보 5개* 도출

---

## 10. 보안·프라이버시

| 항목 | 정책 |
|---|---|
| **PII 전송** | LLM prompt 에 PII (이름/전화/주소) 포함 시 *마스킹 후 전송* — `<NAME>`, `<PHONE>` 토큰 |
| **로그 보존** | `emotion_logs.raw_text` 는 pgcrypto 암호화 |
| **OpenAI 데이터 옵션** | OpenAI *zero data retention* 활성 (Business Tier) |
| **위기 키워드 매칭 시** | LLM 호출 *없이* 로컬 처리. 외부 전송 0 |
| **사용자 삭제 요청** | `emotion_logs` CASCADE 삭제. LLM cache 도 매칭 키 정리 |

---

## 11. 테스트 케이스 (Unit Test 시드)

```python
test_cases = [
    # (입력, 기대 primary, 기대 intensity, 기대 crisis_level)
    ("오늘 정말 좋아", "기쁨", 6, "none"),
    ("아무도 나를 안 좋아하는 것 같아", "외로움", 7, "none"),
    ("죽고 싶어", "_", "_", "critical"),  # 분류 skip
    ("일이 너무 많아 지친다", "지침", 7, "none"),
    ("ㅁㄴㅇㄹ", None, None, "none"),  # 무의미
    ("아 정말 짜증나, 다 짜증나, 다 미워", "분노", 8, "none"),
    ("내가 너무 못나서...", "무자격감", 7, "none"),
    ("", None, None, "none"),  # 빈 입력
]
```

---

## 12. Spring 헥사고날 매핑

```
emotion/
├── domain/
│   ├── Emotion.java                  # enum (10라벨)
│   ├── EmotionClassification.java    # value object
│   └── CrisisLevel.java              # enum
├── application/
│   ├── port/in/
│   │   ├── ClassifyEmotionUseCase.java
│   │   └── RecommendContentUseCase.java
│   ├── port/out/
│   │   ├── LlmClassifierPort.java
│   │   ├── EmotionLogRepository.java
│   │   └── ContentRecommenderPort.java
│   └── service/
│       ├── EmotionClassifierService.java     # 메인 orchestrator
│       ├── CrisisGateService.java            # Step 1
│       └── FallbackClassifierService.java    # Step 3
└── adapter/
    ├── in/web/EmotionController.java         # POST /api/emotion/classify
    └── out/
        ├── llm/OpenAiClassifierAdapter.java  # LLM 호출
        ├── llm/AnthropicClassifierAdapter.java
        └── persistence/EmotionLogJpaAdapter.java
```

---

> **TL;DR** — 4단 폭포 (안전 게이트 → LLM → fallback → 추천 매핑). 핵심 LLM 은 gpt-4o-mini structured output, 위기 키워드는 *로컬 처리 (외부 전송 0)*. 월 100 사용자에 $5 이하.
