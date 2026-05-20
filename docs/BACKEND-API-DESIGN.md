# Backend API 설계 — lemuel-xr

> **상태**: 설계 (구현 전)
> **최종 갱신**: 2026-05-21
> **범위**: 요셉 MVP (Phase 1) + 모세·다윗·예수 (Phase 2) + Track A 1~7
> **기존 코드 베이스**: Spring Boot 3.5.4 + JDK 21, hexagonal (adapter/in · adapter/out · application · domain)

이 문서는 **구현 가이드가 아니라 API 표면(surface) 설계 합의용 문서**다. 엔드포인트·요청/응답 스키마·상태 모델·오류 코드를 먼저 합의한 뒤 구현으로 넘어간다. 코드는 별도 PR.

---

## 0. 설계 원칙 (Why)

1. **Generic over character-specific**: 현재 `JosephGameController` 만 구현됨. 4 인물(요셉·모세·다윗·예수)이 *같은 scene-progression 패턴* 을 공유하므로 **`{character}` path variable + scenario yaml 로딩** 구조로 일반화.
2. **Track A · Track B 의 진입점은 다르지만 출력은 호환**: 둘 다 `emotion → recommendation → XR scene` 공통 구조. *추천 결과* DTO 가 두 트랙을 공통 표현.
3. **Hexagonal 유지**: 새 endpoint 도 기존 `adapter/in/web · application/<UseCase> · domain · adapter/out/persistence` 4-layer 따름.
4. **LLM 호출은 모두 backend 통과** (Unity 가 직접 LLM API 호출 X). 이유: 키 보호 + 캐시 hit rate 최대화 + 응답 정형화.
5. **3차원 모드(영성·감성·이성) 는 session-level 속성** — scene payload 가 모드에 따라 다른 narration/asset/강조점 반환.
6. **정서 보호 장치는 session-level toggle** — haptic 강도, 침묵 skip, 모욕 scene skip 등은 모두 세션 생성 시 결정.
7. **모든 응답은 안정된 ENUM 값으로 진행 상태 표현** — 클라이언트가 *문자열 매칭*하지 않게 enum.
8. **Idempotency**: `decide` / `complete` 는 동일 sceneId · decision 으로 재호출해도 무해.

---

## 1. 전체 API Surface 요약

| Bounded Context | Endpoint 수 | 비고 |
|---|---:|---|
| Auth / User | 3 | 게스트 UUID + 디바이스 식별 |
| Emotion (Track A 진입) | 2 | `classify` + `history` |
| Content (Track A 콘텐츠 1~7) | 5 | recommend / scene / journal / topics |
| Game (Track B 4 인물 공통) | 7 | start / decide / complete / sessions |
| Scripture | 3 | by-ref / range / semantic search |
| TTS proxy | 2 | synthesize / voices |
| LLM (internal-only) | 2 | generate / cache-warmup |
| Safety / Accessibility | 1 | emergency exit |
| Analytics | 2 | event / journey |
| **합계** | **27** | MVP(요셉) 단계는 13개로 충분 |

각 그룹 상세는 §3~§11 참조.

---

## 2. 인증·세션 모델

### 2.1 게스트 모드 (MVP)

- 디바이스별 UUID 자동 발급, 로컬 스토리지에 저장
- 모든 요청은 `Authorization: Bearer <opaque-token>` 헤더로 인증 (게스트도 JWT 발급)
- JWT payload: `{ sub: <user-uuid>, device: <quest3|vision_pro|galaxy_xr>, exp: 30일 }`

### 2.2 디바이스 종류 (ENUM)

```
QUEST_3 | QUEST_2 | VISION_PRO | GALAXY_XR | WEB_XR | DESKTOP_FALLBACK
```

### 2.3 3차원 모드 (ENUM)

```
SPIRITUAL    — 영성: 본문·신학 톤 강조
EMOTIONAL    — 감성: 음악·내레이션·시각 강조
RATIONAL     — 이성: 결정·맥락·분석 카드 강조
AUTO         — AI 자동 (이전 패턴 + 입력 감정 기반)
```

세션 시작 시 명시 (`mode`) 또는 `AUTO` 로 위임. 결정되면 세션 lifetime 동안 변경 불가.

### 2.4 사용자 안전 설정 (struct)

```json
{
  "hapticIntensity": "OFF" | "LOW" | "HIGH",
  "skipSilentScenes": false,
  "skipModulationScenes": false,
  "warningBeforeViolence": true,
  "tts": true
}
```

세션 시작 시 클라이언트가 보내거나, 사용자별 default (`PATCH /api/users/me/safety` 로 갱신) 적용.

---

## 3. Auth / User (3 endpoints)

### `POST /api/auth/guest`

게스트 UUID + JWT 발급. 첫 진입 또는 토큰 만료 시.

**Request**
```json
{
  "deviceId": "string (opaque, 100자 이하)",
  "deviceType": "QUEST_3",
  "appVersion": "0.1.0"
}
```

**Response 200**
```json
{
  "userId": "uuid",
  "token": "jwt",
  "expiresAt": "2026-06-20T00:00:00Z"
}
```

### `GET /api/users/me`

현재 사용자 메타 + 누적 미션·감정 카운트.

**Response 200**
```json
{
  "userId": "uuid",
  "deviceType": "QUEST_3",
  "createdAt": "...",
  "stats": {
    "emotionLogs": 12,
    "completedMissions": 3,
    "favoriteCharacter": "joseph"
  },
  "safety": { /* 2.4 struct */ }
}
```

### `PATCH /api/users/me/safety`

사용자 default 안전 설정 갱신.

**Request**: 위 §2.4 struct 부분 갱신 가능.

**Response 200**: 갱신된 전체 struct.

---

## 4. Emotion — Track A 진입점 (2 endpoints)

### `POST /api/emotion/classify` *(기존 — 확장 필요)*

기존 구현은 `{ emotion, confidence }` 만 반환. 다음 구조로 확장:

**Request**
```json
{
  "text": "오늘 너무 불안해서 잠이 안 와요",
  "context": {
    "previousEmotions": ["불안", "외로움"],   // 최근 24h 감정 (옵션)
    "preferredMode": "EMOTIONAL"             // 옵션
  }
}
```

**Response 200**
```json
{
  "emotionLogId": 12345,
  "primary": { "emotion": "ANXIETY", "confidence": 0.82 },
  "secondary": [
    { "emotion": "LONELINESS", "confidence": 0.31 }
  ],
  "recommendations": {
    "trackA": [
      { "topicId": 4, "title": "시편과 감정", "match": 0.91 },
      { "topicId": 6, "title": "마음을 지키는 것", "match": 0.74 }
    ],
    "trackB": [
      { "character": "MOSES", "scene": 4, "reason": "두려움→동행 인식", "match": 0.78 },
      { "character": "DAVID", "scene": 4, "reason": "공포+신뢰 통합", "match": 0.66 }
    ]
  }
}
```

**중요 변경**: 단일 분류가 아니라 *primary + secondary + recommendations* 까지 한 번에. 클라이언트 round-trip 감소.

### Emotion ENUM (7종)
```
ANXIETY (불안) | SADNESS (슬픔) | ANGER (분노) |
CONFUSION (혼란) | LONELINESS (외로움) | EXHAUSTION (지침) | GRATITUDE (감사)
```

### `GET /api/emotion/history?limit=20&since=ISO`

사용자 감정 입력 이력. Phase 2 — 회원 시스템 도입 후 의미.

**Response 200**
```json
{
  "items": [
    { "id": 12345, "emotion": "ANXIETY", "rawText": "...", "createdAt": "..." }
  ],
  "nextCursor": "..."
}
```

---

## 5. Content — Track A 콘텐츠 1~7 (5 endpoints)

> **현재 구현 없음**. Track A 전체 미구현. `docs/TRACK-A-1-4-WISDOM-EMOTION.md` 의 4개 주제 + BUILD-PLAN 의 7개 주제 기준.

### Topic ID 매핑
```
1 = JOURNAL (일기와 묵상)
2 = PROVERBS (잠언과 지혜)
3 = ECCLESIASTES (전도서와 인생)
4 = PSALMS (시편과 감정)
5 = JOB (고통과 진리)
6 = HEART (마음을 지키는 것)
7 = FEAR (사람을 두려워하지 않는 것)
```

### `GET /api/content/topics`

7개 주제 메타 일괄 반환. 클라이언트가 시작 화면 카드 빌드용.

### `GET /api/content/recommend?emotion=ANXIETY&limit=2`

감정 → 주제 매핑. classify 응답 안에 이미 포함되어 있지만, 직접 진입(주제 카드 클릭) 시 사용.

### `GET /api/content/topics/{topicId}/scene?mode=EMOTIONAL`

특정 주제의 XR 묵상 공간 메타. mode 에 따라 다른 자산 반환.

**Response 200**
```json
{
  "topicId": 4,
  "title": "시편과 감정",
  "mode": "EMOTIONAL",
  "scene": {
    "skybox": "psalm-night.exr",
    "bgmId": "lyre-soft-23",
    "narrationId": "psalm-23-emotional",
    "floatingTexts": [
      { "ref": "ps-23:1", "text": "여호와는 나의 목자시니..." }
    ],
    "ambient": { "wind": 0.4, "water": 0.2 }
  },
  "estimatedDurationSec": 240
}
```

### `POST /api/content/journal`

Topic 1 일기 작성.

**Request**
```json
{
  "topicId": 1,
  "text": "오늘 ...",
  "linkedEmotionLogId": 12345
}
```

**Response 200**: `{ "journalId": "uuid" }`

### `GET /api/content/journal?limit=20`

본인 일기 이력. Phase 2.

---

## 6. Game — Track B 4 인물 공통 (7 endpoints)

> **현재 구현**: `JosephGameController` 만 존재. 4 인물 공통화 필요. *기존 joseph 호환 유지* — `/api/game/joseph/...` 그대로 동작하되 내부적으로 generic flow 호출.

### 인물 ENUM
```
JOSEPH | MOSES | DAVID | JESUS
```

### Scene Type ENUM (인물 간 공통)
```
CINEMATIC        — 자동 진행 영상 (Joseph S1, Moses S6)
PICK_ONE         — 단일 선택지 (Joseph S2 저장량, S4 형제 재회)
DISTRIBUTE       — 다중 자루/카드 분배 (Joseph S3, Moses S3 5카드)
INTERACTION      — 단일 XR 인터랙션 (Moses S2 떨기나무, David S3 갑옷, David S5 sling)
SILENCE          — 의도적 침묵 시간 (Moses S1 광야)
OUTRO            — 회복 메시지
```

### Decision Schema (JSONB)
인물·scene 마다 decision payload 모양이 다름. 통합 schema:

```json
{
  "<character>:<sceneId>": {
    "type": "PICK_ONE" | "DISTRIBUTE" | "INTERACTION_RESULT" | "SILENT_PASS",
    "value": <type-specific>,
    "durationMs": 1234,
    "modeApplied": "EMOTIONAL"
  }
}
```

예시:
```json
// Joseph Scene 2 (저장 결정)
{ "joseph:2": { "type": "PICK_ONE", "value": "save_33" } }

// Moses Scene 3 (5 카드)
{ "moses:3": { "type": "DISTRIBUTE", "value": ["throw","throw","heart","throw","heart"] } }

// David Scene 4 (5돌)
{ "david:4": { "type": "DISTRIBUTE", "value": [
  {"stone":"fear","order":1},
  {"stone":"humiliation","order":3},
  {"stone":"loneliness","order":2},
  {"stone":"trust","order":5},
  {"stone":"prayer","order":4}
]}}
```

### `POST /api/game/{character}/start`

세션 생성 + Scene 1 페이로드.

**Request**
```json
{
  "userId": "uuid (optional — JWT 에서 가져옴)",
  "mode": "EMOTIONAL" | "SPIRITUAL" | "RATIONAL" | "AUTO",
  "safety": { /* §2.4 override, 없으면 user default */ },
  "linkedEmotionLogId": 12345
}
```

**Response 200**
```json
{
  "sessionId": "uuid",
  "character": "MOSES",
  "currentScene": 1,
  "totalScenes": 6,
  "scenePayload": { /* §6 Scene Payload */ },
  "appliedMode": "EMOTIONAL",
  "estimatedDurationSec": 420
}
```

### `POST /api/game/{character}/{sid}/decide`

Scene n 의 사용자 선택 기록 → 다음 scene payload 반환.

**Request**
```json
{
  "sceneId": 3,
  "decision": { /* §6 Decision Schema 의 value */ },
  "decisionDurationMs": 8521,
  "mode": "EMOTIONAL"   // server-side validation: 세션 시작 모드와 일치해야 함
}
```

**Response 200**
```json
{
  "sessionId": "uuid",
  "previousScene": 3,
  "currentScene": 4,
  "scenePayload": { /* §6 */ },
  "branchInfo": {
    "branchId": "moses-s3-mixed",
    "narrationId": "moses.s3.outcome.mixed",
    "monologueText": "내가 떨면서도 한 발 내딛는다..."  // realtimeLlm 인 경우만
  }
}
```

**Idempotency**: 동일 `sceneId` 재호출 시 *결정만 덮어쓰기*, 이미 다음 scene 진행됐으면 *현재 scene 페이로드* 만 반환 (사이드이펙트 없음).

### `POST /api/game/{character}/{sid}/complete`

세션 종료 + 회복 메시지 생성.

**Request**
```json
{
  "finalOutcome": "string (서버가 branchId 누적으로 자동 계산 가능 — optional override)",
  "userReflection": "string (선택, 5000자 이하)"
}
```

**Response 200**
```json
{
  "sessionId": "uuid",
  "completedAt": "...",
  "finalOutcome": "moses-mixed-confession",
  "recoveryMessage": {
    "scriptureRef": "ex-3:12",
    "scriptureText": "내가 반드시 너와 함께 있으리라",
    "supportText": "두려움이 사라진 후에 가는 것이 용기가 아니다...",
    "matchedToEmotion": "ANXIETY"
  },
  "stats": {
    "totalDurationSec": 412,
    "decisionsCount": 6
  }
}
```

### `GET /api/game/{character}/scenarios`

해당 인물이 갖는 scenario 목록 (Phase 2 — *대안 시나리오* 도입 시). MVP 는 인물당 1개 시나리오.

**Response 200**
```json
{
  "character": "JOSEPH",
  "scenarios": [
    { "id": "joseph-mvp-v1", "title": "곡식 7년", "scenes": 5, "estimatedDurationSec": 360 }
  ]
}
```

### `GET /api/game/sessions/{sid}`

세션 단일 조회 (재개 / 분석).

**Response 200**: GameSession 상세 — character, decisions, currentScene, completedAt, finalOutcome, mode.

### `GET /api/game/sessions/recent?character=MOSES&limit=10`

사용자 최근 세션. Phase 2 — 회원 도입 후 의미.

### `DELETE /api/game/sessions/{sid}`

미완료 세션 폐기 (사용자가 emergency exit 했을 때).

**Response 204**

---

## 7. Scripture (3 endpoints)

> **현재 구현**: `ScriptureController` 골격 존재. 미세 확장.

### `GET /api/scripture/{ref}?translation=modern`

단일 본문 조회. `ref` 형식: `gen-45:5` / `ex-3:11~14` / `ps-23` / `1sam-17:40`.

**Translation ENUM**
```
MODERN  — 현대인의 성경 (라이선스 협의 후 정식 출시)
REV     — 개역개정 (대한성서공회 비영리 약관)
KIVL    — Kethibh/Imperative variant (미사용)
```

**Response 200**
```json
{
  "reference": "ex-3:12",
  "translation": "MODERN",
  "book": "EXODUS",
  "chapter": 3,
  "verseStart": 12,
  "verseEnd": 12,
  "text": "내가 반드시 너와 함께 있으리라..."
}
```

**404** — 본문 없음 (예: 라이선스 미보유 번역, 범위 외 책).

### `GET /api/scripture/range?book=EXODUS&chapter=3&from=10&to=15&translation=REV`

범위 조회. 본문 묵상 공간 띄울 때.

### `POST /api/scripture/search`

pgvector 기반 의미 검색. 감정·키워드 → 본문 매칭.

**Request**
```json
{
  "query": "두려움 앞에서 한 발 내딛는",
  "limit": 5,
  "filters": {
    "books": ["EXODUS", "PSALMS"]  // optional
  }
}
```

**Response 200**
```json
{
  "matches": [
    { "reference": "ex-3:12", "similarity": 0.87, "snippet": "..." },
    { "reference": "ps-27:1", "similarity": 0.81, "snippet": "..." }
  ]
}
```

---

## 8. TTS Proxy (2 endpoints)

> **현재 구현**: `tts/app.py` Python 사이드카만 존재. Spring 측 proxy 미구현.

전략: Unity → Spring `/api/tts/synthesize` → Spring 이 캐시 hit 검사 → miss 면 Python 사이드카(`http://lemuel-xr-tts:8000/synthesize`) 호출 → wav 를 R2/PVC 에 저장 → presigned URL 반환.

### `POST /api/tts/synthesize`

**Request**
```json
{
  "text": "내가 반드시 너와 함께 있으리라",
  "voiceId": "narrator-male-low",
  "speakingRate": 0.9,
  "format": "WAV" | "MP3"
}
```

**Response 200**
```json
{
  "cacheKey": "sha256-...",
  "url": "https://r2.lemuel.co.kr/tts/<key>.wav",
  "durationMs": 4500,
  "cached": true,
  "voiceId": "narrator-male-low"
}
```

**캐시 키** = `sha256(text || voiceId || speakingRate)`. 같은 입력은 동일 wav 반환.

### `GET /api/tts/voices`

사용 가능한 voice ID 목록. 인물별 추천 매핑 포함.

**Response 200**
```json
{
  "voices": [
    { "id": "narrator-male-low", "label": "내레이터 (낮음)", "lang": "ko", "recommendedFor": ["moses-narrator","joseph-narrator"] },
    { "id": "goliath-bass", "label": "골리앗", "lang": "ko", "recommendedFor": ["david-s5-goliath"] }
  ]
}
```

---

## 9. LLM — Internal Only (2 endpoints)

> 클라이언트에게 직접 노출 X. Spring 내부 service 가 사용. `decide` 호출 흐름 안에서 호출.

### `POST /api/internal/llm/generate`

**Request**
```json
{
  "promptKey": "joseph.s4.brotherReunion.reveal",
  "variables": { "savePercentage": "1/3", "userMode": "EMOTIONAL" },
  "model": "gemini-1.5-flash",
  "temperature": 0.7,
  "maxTokens": 200
}
```

**Response 200**
```json
{
  "text": "...",
  "cached": false,
  "tokensUsed": 156,
  "latencyMs": 820
}
```

**Auth**: `X-Internal-Token` 헤더 (asat 와 동일 패턴 — `InternalServiceTokenFilter`)
**캐시 키**: `sha256(promptKey || JSON.stringify(variables))` — 동일 입력은 동일 응답.

### `POST /api/internal/llm/cache-warmup`

사전 캐싱용 batch endpoint. 인물별 분기 응답을 미리 생성 후 DB 에 저장.

**Request**
```json
{
  "items": [
    { "promptKey": "joseph.s2.monologue", "variables": { "savePercentage": "1/5" } },
    { "promptKey": "joseph.s2.monologue", "variables": { "savePercentage": "1/3" } }
  ]
}
```

**Response 200**
```json
{ "generated": 2, "cached": 0, "failed": 0, "totalLatencyMs": 1640 }
```

---

## 10. Safety / Accessibility (1 endpoint)

### `POST /api/game/sessions/{sid}/exit`

세션 emergency exit (사용자 정서 보호 — 예: 광야 침묵 Scene 1 에서 *건너뛰기* 한 경우, 트라우마 자극으로 중단).

**Request**
```json
{
  "reason": "TRAUMA_TRIGGER" | "FATIGUE" | "USER_CHOICE" | "TECHNICAL",
  "atSceneId": 3
}
```

**Response 200**
```json
{
  "sessionId": "uuid",
  "exitedAt": "...",
  "gentleMessage": "잠시 멈추셨네요. 다음에 다시 만나요. 시편 23편 음성을 함께 두고 갈게요.",
  "softLandingAudioUrl": "https://r2.lemuel.co.kr/tts/safe-exit.wav"
}
```

세션은 `final_outcome = 'safe_exit'` 으로 종료. 분석상 *부정적 시그널이 아닌 사용자 보호 동작* 으로 분류.

---

## 11. Analytics (2 endpoints)

### `POST /api/analytics/event`

세션 내 미세 이벤트 (XR 상호작용 latency, scene 별 시선 응시, haptic 반응 등).

**Request**
```json
{
  "sessionId": "uuid",
  "events": [
    { "type": "GAZE_DURATION", "sceneId": 2, "value": 4500, "ts": "..." },
    { "type": "GRAB_RELEASE", "sceneId": 3, "value": "stone-1", "ts": "..." }
  ]
}
```

**Response 200**: `{ "accepted": 2 }`

**저장**: `interaction_events` (신규 테이블). MVP 는 elastic 으로 직접 ship 도 옵션.

### `GET /api/users/me/journey?days=30`

본인 *감정 → 추천 → 실행 → 회복* 흐름 시계열. Phase 2 — 회원 도입 후 의미.

**Response 200**
```json
{
  "timeline": [
    {
      "ts": "...",
      "emotion": "ANXIETY",
      "recommendedMissions": ["moses-s4", "david-s4"],
      "chosen": "moses",
      "completed": true,
      "recoveryMatched": "내가 너와 함께"
    }
  ]
}
```

---

## 12. 상태 코드 + 오류 코드

모든 4xx 응답은 `application/problem+json` (RFC 7807) 따름.

```json
{
  "type": "https://lemuel.co.kr/errors/E_SESSION_INVALID",
  "title": "Session no longer active",
  "status": 409,
  "code": "E_SESSION_INVALID",
  "detail": "Session abc123 was completed at 2026-05-21T...",
  "instance": "/api/game/joseph/abc123/decide"
}
```

### 주요 ErrorCode

| Code | HTTP | 의미 |
|---|---:|---|
| `E_SESSION_NOT_FOUND` | 404 | sessionId 무효 |
| `E_SESSION_INVALID` | 409 | 이미 completed 또는 exited 세션에 decide |
| `E_SCENE_OUT_OF_ORDER` | 409 | sceneId 가 현재 scene 보다 작거나 너무 큼 |
| `E_DECISION_MALFORMED` | 400 | decision payload 가 scene type 에 안 맞음 |
| `E_CHARACTER_UNKNOWN` | 404 | character path variable 이 enum 외 |
| `E_MODE_MISMATCH` | 409 | mode 가 세션 시작 모드와 다름 |
| `E_SCRIPTURE_NOT_FOUND` | 404 | scripture ref 없음 (또는 라이선스 미보유 translation) |
| `E_LLM_UPSTREAM_FAIL` | 502 | 사이드카 응답 없음 — 클라이언트는 캐시된 fallback 표시 |
| `E_TTS_UPSTREAM_FAIL` | 502 | TTS 사이드카 실패 |
| `E_INTERNAL_TOKEN_INVALID` | 401 | `/api/internal/*` 인증 실패 |
| `E_RATE_LIMITED` | 429 | 분당/초당 호출 제한 (특히 emotion classify, llm generate) |

---

## 13. 성능 + 캐싱 + 비용 통제

### 13.1 LLM 비용 통제

- 사전 캐시 비율 목표: **80%+**
- `llm_cache.hit_count` 메트릭 모니터링 (Telegram 알림 — hit rate <60% 면 경고)
- 실시간 호출 endpoint: Joseph S4, Moses S3 분기별 monologue, David S4 5돌 조합
- Rate limit: 사용자당 5 calls/min, 글로벌 100 calls/min (Gemini 무료 티어 보호)

### 13.2 응답 시간 SLO

| Endpoint | p50 | p99 |
|---|---:|---:|
| `/api/game/*/start` | 100ms | 400ms |
| `/api/game/*/decide` (cached) | 50ms | 200ms |
| `/api/game/*/decide` (realtime LLM) | 800ms | 2000ms |
| `/api/emotion/classify` | 600ms | 1800ms |
| `/api/tts/synthesize` (cached) | 80ms | 250ms |
| `/api/tts/synthesize` (miss) | 2s | 8s |
| `/api/scripture/*` | 30ms | 100ms |

### 13.3 TTS 캐시

- text+voice+rate 의 sha256 해시로 영구 캐싱
- 사전 생성 batch: 인물별 narration / scripture passages (출시 전 1회)
- R2 또는 PVC 저장. 핫셋(자주 쓰이는 wav)은 nginx 캐시 추가

### 13.4 pgvector 검색

- embedding 차원: 3072 (OpenAI text-embedding-3-large) 또는 768 (서버 자체 모델)
- HNSW 인덱스 (`scripture_passages.embedding`)
- 검색 후 LLM rerank 옵션 (Phase 2)

---

## 14. 데이터 모델 변경 (V3+)

기존 V1·V2 위에 다음 추가 필요:

### V3 — Track A + 안전 설정

```sql
-- Track A 일기
CREATE TABLE journal_entries (
    id              UUID         PRIMARY KEY,
    user_id         UUID         NOT NULL REFERENCES users(id),
    topic_id        INT          NOT NULL,
    text            TEXT         NOT NULL,
    linked_emotion  BIGINT       REFERENCES emotion_logs(id),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- 사용자 안전 설정
ALTER TABLE users
  ADD COLUMN safety_haptic VARCHAR(10) NOT NULL DEFAULT 'LOW',
  ADD COLUMN safety_skip_silence BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN safety_warn_violence BOOLEAN NOT NULL DEFAULT TRUE;
```

### V4 — 세션 모드 + 안전 override

```sql
ALTER TABLE game_sessions
  ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'AUTO',  -- SPIRITUAL/EMOTIONAL/RATIONAL/AUTO
  ADD COLUMN applied_safety JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN exit_reason VARCHAR(30);                   -- TRAUMA_TRIGGER/FATIGUE/...
```

### V5 — Track B 분기·시나리오

```sql
-- scenario 메타 (Phase 2 의 대안 시나리오 도입 대비)
CREATE TABLE scenarios (
    id              VARCHAR(50)  PRIMARY KEY,    -- 'joseph-mvp-v1'
    character       VARCHAR(20)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    scenes_count    INT          NOT NULL,
    estimated_sec   INT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE
);

ALTER TABLE game_sessions
  ADD COLUMN scenario_id VARCHAR(50) REFERENCES scenarios(id);
```

### V6 — pgvector

```sql
CREATE EXTENSION IF NOT EXISTS vector;
ALTER TABLE scripture_passages
  ADD COLUMN embedding vector(3072),
  ADD COLUMN embedding_model VARCHAR(50);
CREATE INDEX idx_scripture_embed ON scripture_passages USING hnsw (embedding vector_cosine_ops);
```

### V7 — Analytics events

```sql
CREATE TABLE interaction_events (
    id           BIGSERIAL    PRIMARY KEY,
    session_id   UUID         NOT NULL REFERENCES game_sessions(id),
    user_id      UUID         NOT NULL,
    type         VARCHAR(50)  NOT NULL,
    scene_id     INT,
    value        TEXT,
    ts           TIMESTAMP    NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_events_session ON interaction_events (session_id, ts);
```

---

## 15. Hexagonal 패키지 구조 (제안)

```
github.lms.lemuel.xr/
├── auth/                    ← (신규) JWT 발급, 게스트 사용자
│   ├── adapter/in/web/AuthController.java
│   ├── adapter/out/persistence/...
│   ├── application/CreateGuestUserUseCase.java
│   └── domain/User.java
├── emotion/                  ← 기존, 응답 확장만
├── content/                  ← (신규) Track A 1~7
│   └── adapter/in/web/ContentController.java
├── game/                     ← 기존, 일반화 필요
│   ├── adapter/in/web/
│   │   ├── GameController.java         ← (신규) /api/game/{character}/*
│   │   └── JosephGameController.java   ← 기존 유지, deprecation note
│   ├── application/
│   │   ├── StartGameSessionUseCase.java
│   │   ├── DecideSceneUseCase.java
│   │   ├── CompleteGameSessionUseCase.java
│   │   └── ScenarioLoader.java         ← yaml → domain mapping
│   └── domain/
│       ├── GameSession.java
│       ├── Scenario.java
│       ├── Scene.java
│       └── Decision.java
├── scripture/                ← 기존, 확장 (range, search)
├── tts/                      ← (신규) Python 사이드카 proxy
│   ├── adapter/in/web/TtsController.java
│   ├── adapter/out/sidecar/PythonTtsClient.java  ← WebClient
│   └── application/SynthesizeTtsUseCase.java
├── llm/                      ← (신규) 내부 service
│   ├── adapter/in/web/InternalLlmController.java
│   ├── adapter/out/sidecar/PythonLlmClient.java
│   └── application/GenerateLlmResponseUseCase.java
├── safety/                   ← (신규) emergency exit
├── analytics/                ← (신규) event collector
└── common/                   ← ErrorCode, ProblemDetail mapper, InternalServiceTokenFilter
```

---

## 16. 보안 + 인증 + 레이트 리밋

### 16.1 인증

- 게스트 JWT: 30일 만료, HMAC-SHA256, secret 은 K8s secret (sops-encrypted)
- 인증 필요 endpoint: `/api/game/*`, `/api/content/journal`, `/api/users/me*`, `/api/emotion/history`
- 인증 불필요: `/api/auth/guest`, `/api/scripture/*`, `/api/content/topics`, `/api/tts/voices`

### 16.2 내부 토큰

- `/api/internal/*` 는 `X-Internal-Token` 헤더 필요 (asat 와 동일 패턴)
- Python 사이드카(ai/, tts/) 에서 callback 시 사용
- Token rotation: 분기별 또는 사고 시. K8s secret 갱신 + pod restart

### 16.3 레이트 리밋

`RateLimitFilter` (asat 와 동일 패턴) — 사용자별 + IP별 버킷.

| 그룹 | 사용자별 | IP별 (글로벌) |
|---|---|---|
| `/api/auth/*` | 10/분 | 100/분 |
| `/api/emotion/classify` | 20/분 | 200/분 |
| `/api/game/*/decide` (realtime LLM) | 30/분 | 200/분 |
| `/api/tts/synthesize` (miss) | 10/분 | 50/분 |
| 기타 | 60/분 | 600/분 |

429 응답에 `Retry-After` 헤더 포함.

---

## 17. 미해결 / 의사결정 필요

1. **OAuth 도입 시점** — Phase 2 의 회원 시스템 (Google/Apple Sign-In). 게스트 → 회원 migration 흐름.
2. **다국어** — 한국어 외 영어 출시 시 scripture 본문은 NIV/KJV 어떻게 라이선스 처리?
3. **Multiplayer (Phase 3)** — *공동체 묵상* 으로 같은 공간에 다중 사용자. WebSocket vs WebRTC 결정.
4. **모드별 자산 분리** — `mode=EMOTIONAL` 일 때 BGM 다른 트랙. 자산 양 N배 증가. CDN 비용 검토.
5. **사용자 일기 → AI 코멘트 (Phase 3)** — 일기 본문을 LLM 에 보낼 때 사용자 동의 명시 필요. 별도 consent flow.
6. **분석/리포트** — 사용자 *journey* 를 정기적으로 묶어 PDF 리포트로 제공할지. asat 의 ReportArtifact 패턴 재사용 가능.
7. **scenarios yaml vs DB** — 현재 `resources/scenarios/joseph.yml` 정적. *동적 시나리오 추가* 필요 시 DB 로 이전 (asat 의 ScenarioLoader 와 같은 패턴).
8. **Spring Boot 4 복귀 시점** — 현재 SB 3.5.4 + JDK 21. Spring AI 1.0 GA 가 4.x 호환 후 다시 올림.

---

## 18. 다음 액션 (구현 합의 후)

순서:

1. **요셉 API 일반화** — `JosephGameController` 의 로직을 `GameController`(`/api/game/{character}/...`) 로 이전. 기존 path 는 alias 로 한 sprint 유지 후 deprecated.
2. **Auth 모듈 추가** — `/api/auth/guest` + JWT 발급 + `RateLimitFilter`.
3. **emotion classify 응답 확장** — recommendations 까지 한 번에.
4. **TTS proxy** — Spring 측 `TtsController` + `PythonTtsClient`.
5. **Track A — `/api/content/*`** — 5 endpoint 구현 + 본문 시드 (시편 23, 잠언 4 등 Topic 1·4·6 우선).
6. **모세 시나리오 yaml + scripture seed** — `resources/scenarios/moses.yml`, V8 출 3~14 시드.
7. **다윗 시나리오 yaml + scripture seed** — V9 삼상 16~17 + 시 23.
8. **pgvector 도입** — V6 마이그레이션 + embedding 생성 batch job.
9. **Analytics** — `interaction_events` + Telegram 봇으로 일일 요약.

각 단계는 별도 PR + 테스트 + STATUS.md 갱신.

---

## 19. 참고

- 기존 `JosephGameController.java` — generic 화 시 deprecation 대상이지만 *동작 호환* 유지 권장
- asat 의 `InternalServiceTokenFilter`, `RateLimitFilter`, `ErrorCode` 패턴 — 그대로 재사용 가능
- `docs/MVP-JOSEPH.md` §4 / `MVP-MOSES.md` §4 / `MVP-DAVID.md` §4 — 인물별 endpoint 메모와 본 문서 동기화 필요 시 본 문서가 진실의 원천 (single source of truth)
