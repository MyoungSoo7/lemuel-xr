# XR-INTEGRATION — 백엔드 ↔ XR 클라이언트 통합 설계

> **목표**: Backend (Spring + Python AI) 가 *디바이스 무관* 으로 동작. XR 클라이언트 (Vision Pro / Quest 3 / Galaxy XR) 가 *동일 API* 로 연동.

---

## 0. 아키텍처 한눈에

```
┌──────────────────────────────────────────────────────────┐
│ XR 클라이언트 — Unity 6 LTS + OpenXR                      │
│ ├ Quest 3 (Meta XR SDK 빌드)                              │
│ ├ Apple Vision Pro (PolySpatial 빌드)                     │
│ └ Galaxy XR (Android XR + OpenXR 빌드)                    │
└────────────────────────┬─────────────────────────────────┘
                          │ HTTPS REST + WebSocket
                          │ JWT auth
                          ▼
┌──────────────────────────────────────────────────────────┐
│ Backend Gateway (Spring Cloud Gateway)                    │
│  └ /api/* → Spring Boot 4 (헥사고날)                      │
│  └ /ai/*  → Python FastAPI (LangChain)                    │
└────────────────────────┬─────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────┐
│ Storage                                                   │
│  ├ PostgreSQL 17 (게임·일기·시편)                          │
│  ├ Redis (세션·캐시)                                       │
│  ├ R2 (S3) — TTS wav + 이미지                              │
│  └ pgvector — 성경 임베딩                                  │
└──────────────────────────────────────────────────────────┘
```

→ **백엔드는 *XR 디바이스 모름*** — 모든 분기는 클라이언트에서.

---

## 1. 디바이스 별 차이 — 백엔드 무관 영역

| 영역 | Quest 3 | Vision Pro | Galaxy XR | 백엔드 영향 |
|---|---|---|---|---|
| OS | Android (Horizon OS) | visionOS | Android XR | ❌ 무관 |
| OpenXR | ✅ 1.0 | ✅ 1.0 | ✅ 1.0 | ❌ 무관 |
| 입력 | Controller + Hand | Eye + Pinch | Controller + Hand | ❌ 무관 (Unity Input Action 추상화) |
| 햅틱 | Controller 진동 | 없음 (대신 spatial audio) | Controller | 🟡 일부 (clientCapabilities 로 전달) |
| Spatial Audio | 3D Audio | Personal Sound | 3D Audio | ❌ 무관 |
| 해상도 | 2064×2208 | 3660×3142 | TBD | ❌ 무관 |
| 빌드 | Android APK | visionOS .ipa | Android APK | 🟡 같은 코드 다른 빌드 |
| 음성 입력 | ✅ | ✅ | ✅ | ❌ 무관 |
| Persistence | Anchor | World Tracking | Anchor | ❌ 무관 |

→ **백엔드는 *디바이스별 분기 0***. 차이는 클라이언트에서 처리.

---

## 2. 백엔드 API 계약 (Backend ↔ XR Contract)

### 2.1 인증

```
POST /api/auth/guest
Body: { "device_fingerprint": "<해시>", "device_type": "quest3|visionpro|galaxyxr" }
Response: { "access_token": "<jwt>", "user_id": "<uuid>", "expires_in": 86400 }
```

JWT payload:
```json
{
  "sub": "user-uuid",
  "device_type": "quest3",
  "preferred_mode": "balanced",
  "exp": 1234567890
}
```

이후 모든 요청에 `Authorization: Bearer <jwt>` 헤더.

### 2.2 감정 입력 → 추천

```
POST /api/emotion/classify
Body: { "raw_text": "오늘 너무 지쳤어" }
Response: {
  "primary_emotion": "지침",
  "intensity": 7,
  "crisis_level": "none",
  "recommended_content": [
    {
      "type": "psalm", "ref": "42",
      "asset_url": "https://r2.../psalm-42-ko-female.wav",
      "duration_seconds": 180
    },
    {
      "type": "mission", "character": "moses",
      "scene_count": 6, "duration_minutes": 6
    }
  ]
}
```

`asset_url` 은 R2 (CDN) 직접 — 백엔드 트래픽 안 거침.

### 2.3 게임 세션

```
POST /api/game/{character}/start
Body: { "triggered_by_emotion_log_id": 12345, "chosen_dimension": "emotional" }
Response: {
  "game_session_id": "<uuid>",
  "scene_1": {
    "narration_url": "https://r2.../moses-scene1-narration.wav",
    "duration_seconds": 60,
    "interaction_type": "none",
    "next_endpoint": "/api/game/moses/scene/2/start"
  }
}
```

```
POST /api/game/{character}/scene/{n}/decide
Body: { "decision": {"cards": ["throw","heart","throw","throw","heart"]} }
Response: {
  "decision_id": "<uuid>",
  "scene_n+1": { ... },
  "ai_narration_url": "https://r2.../...wav"   # 캐시 hit 이거나 생성 후
}
```

### 2.4 콘텐츠 — 트랙 A

```
GET /api/content/psalm/{ref}?translation=krv
Response: {
  "reference": "ps-23",
  "verses": [
    {"verse":1, "text":"여호와는 나의 목자시니..."},
    ...
  ],
  "audio_urls": {
    "calm_male": "https://r2.../...wav",
    "calm_female": "https://r2.../...wav",
    "lament": "https://r2.../...wav"
  }
}
```

```
POST /api/diary/save
Body: { "body": "오늘은...", "form_type": "free", "emotion_label": "외로움", "intensity": 6 }
Response: { "diary_id": "<uuid>", "saved_at": "...", "encrypted": true }
```

### 2.5 안전 — 위기 키워드 매칭 시

이미 `/api/emotion/classify` 응답에 `crisis_level` 필드. XR 클라이언트가 이 필드 보고 *위기 화면* 표시.

```json
{
  "crisis_level": "critical",
  "crisis_resources": [
    {"name":"자살예방상담전화", "phone":"1577-0199", "url":null},
    ...
  ],
  "recommended_content": []   // 일반 추천 없음
}
```

---

## 3. WebSocket — 실시간 이벤트 (Phase 2~)

게임 진행 중 *백엔드가 클라이언트로 push* 할 일은 *거의 없음* (게임은 self-contained). 다만:

| 이벤트 | 사용 |
|---|---|
| `game.session_timeout` | 5분 비활성 시 자동 저장 안내 |
| `crisis.escalation` | 다른 세션에서 위기 신호 감지 시 동기화 |
| `content.published` | 새 콘텐츠 생성 알림 (옵션) |

→ MVP 는 REST 만. WebSocket 은 Phase 2 도입.

---

## 4. 콘텐츠 자산 (Asset) 전략

### 4.1 자산 종류·크기

| 자산 | 평균 크기 | 총 크기 (MVP) |
|---|---|---|
| TTS wav (한국어 1분) | 1.5 MB | 200 MB (시편 100편 + NPC 대화) |
| 게임 배경 이미지 (3D 환경 X) | 5 MB / png | 100 MB |
| 시편 UI 카드 일러스트 | 200 KB | 20 MB |
| 폰트 (한국어) | 5 MB | 5 MB |
| **총** | | **~325 MB** |

→ 첫 다운로드 시 *대용량*. 점진적 다운로드 필요.

### 4.2 자산 다운로드 전략

**핵심 자산** (앱 설치 시):
- 폰트, 기본 UI, 첫 화면 + 시편 23편
- 약 30 MB

**점진적 자산** (필요 시):
- 사용자가 *시편 42편 선택* → 그때 다운로드
- 캐싱: 디바이스 로컬 저장 (장기)

**Mission 자산** (게임 시작 시):
- 요셉/모세/다윗/예수 미션 *시작 직전* 60 MB 다운로드 + 캐싱
- 게임 시작 *예열 화면* 30초 (다운로드)

### 4.3 R2 (Cloudflare) CDN

- *S3 호환* — Spring 의 AWS SDK 그대로 사용
- *Egress 무료* (Cloudflare R2 의 핵심 장점)
- 캐시 hit rate 80%+ 가정

```java
// Spring 측 — Presigned URL 생성
String presigned = r2Client.presignGetObject(
    GetObjectPresignRequest.builder()
        .bucket("lemuel-assets")
        .key("audio/psalm-23-ko-male.wav")
        .signatureDuration(Duration.ofHours(1))
        .build()
);
// XR 클라이언트가 이 URL 로 다운로드
```

---

## 5. Unity 클라이언트 — 디바이스 추상화

### 5.1 OpenXR + Input Action

```csharp
// 공통 InputAction 정의
public InputAction primaryTrigger;     // 모든 플랫폼
public InputAction primaryGrip;        // 컨트롤러
public InputAction handPinch;          // Vision Pro (눈+핀치)
public InputAction eyeGaze;            // Pro 만

void Awake() {
    primaryTrigger.AddBinding("<XRController>/triggerPressed");
    primaryTrigger.AddBinding("<XRHand>/pinchPressed");  // 손 트래킹
    // ...
}
```

→ 게임 로직은 *어떤 디바이스인지 모름*.

### 5.2 디바이스 capabilities — 백엔드 전달

```
POST /api/auth/guest
Body: {
  "device_fingerprint": "...",
  "device_type": "visionpro",
  "capabilities": {
    "haptic": false,           // Vision Pro 햅틱 없음
    "eye_tracking": true,      // Pro 만
    "hand_tracking": true,
    "controllers": false,      // Pro 는 컨트롤러 없음
    "spatial_audio": "personal"  // 'personal'(Pro) | '3d'(Quest)
  }
}
```

백엔드는 capabilities 기반으로 *적합한 콘텐츠* 반환:
- Quest 3: 햅틱 강한 Scene
- Vision Pro: 햅틱 X → spatial audio 보강

### 5.3 빌드 분기

```
Unity 프로젝트 (단일 codebase)
   │
   ├─ Build Settings: Android
   │     ├─ Meta XR SDK + Android XR Plugin
   │     ├─ Output: lemuel-quest.apk
   │     └─ Sideload to Quest 3
   │
   ├─ Build Settings: visionOS
   │     ├─ PolySpatial + Xcode
   │     ├─ Output: lemuel-vision.ipa
   │     └─ App Store (Vision Pro)
   │
   └─ Build Settings: Android (Galaxy XR)
         ├─ Google Android XR Plugin + OpenXR
         ├─ Output: lemuel-galaxyxr.apk
         └─ Galaxy XR Store / Sideload
```

→ *Unity 6 LTS* 가 세 플랫폼 모두 지원 (2024 말 PolySpatial 발표).

### 5.4 *Galaxy XR* — 가장 새 플랫폼 (2025~)

- Google Android XR (Cardboard·Daydream 후속)
- OpenXR 표준 준수
- Galaxy XR 헤드셋 (삼성, 2025 후반)
- Unity 6 + Google XR Extensions 패키지

→ MVP 는 Quest 3 우선, Vision Pro 다음, Galaxy XR 마지막.

---

## 6. 백엔드 보강 — XR 통합 위한 추가 설계

방금 추가된 요구사항 ("백엔드만, XR 3 디바이스 모두 지원") 위한 보완:

### 6.1 ✅ 추가 필요한 백엔드 영역

| 영역 | 현재 설계 | 추가/보강 |
|---|---|---|
| Asset 전송 | DB-SCHEMA 에 일부 | **`/api/assets/presigned` endpoint 추가** — R2 presigned URL 발급 |
| 자산 버전 관리 | 미설계 | **Asset versioning** — *어떤 사용자에게 어떤 버전* 분배 |
| 디바이스 다양성 | game_sessions 에 character 만 | **device_type, capabilities** 컬럼 추가 |
| 오프라인 동기화 | 미설계 | **`/api/sync/upload`** — XR 오프라인 작업 한꺼번에 업로드 |
| 백엔드 latency | 미정의 | **API p95 < 500ms 목표** (XR 멀미 방지) |
| 자산 사전 로드 | 미설계 | **`/api/game/{c}/prepare`** — 게임 시작 전 자산 목록 + 다운로드 URL |

### 6.2 백엔드 API 보강안

#### A. `/api/assets/manifest` — 디바이스별 자산 목록

```
GET /api/assets/manifest?content_type=mission&character=moses
Headers: device_capabilities (JWT 에 포함)
Response: {
  "manifest_version": "v2.3.1",
  "total_size_bytes": 67854321,
  "assets": [
    {"id":"moses-scene1-narration", "url":"https://r2.../...wav", "size":1500000, "required":true},
    {"id":"moses-scene2-bush-shader", "url":"https://r2.../...glb", "size":3200000, "required":true},
    ...
  ]
}
```

XR 클라이언트가 한꺼번에 다운로드 → 캐시 → 게임 시작.

#### B. `/api/sync/upload` — 오프라인 작업 일괄 업로드

XR 디바이스가 오프라인 동안 게임 진행 → 온라인 시 일괄 sync.

```
POST /api/sync/upload
Body: {
  "device_id": "...",
  "events": [
    {"type":"emotion_log", "timestamp":"...", "data":{...}},
    {"type":"game_decision", "timestamp":"...", "data":{...}},
    {"type":"diary_entry", "timestamp":"...", "data":{...}}
  ]
}
Response: { "synced_count": 23, "conflicts": [] }
```

#### C. `game_sessions` 테이블에 컬럼 추가

```sql
ALTER TABLE game_sessions
  ADD COLUMN device_type VARCHAR(30),       -- 'quest3' | 'visionpro' | 'galaxyxr'
  ADD COLUMN capabilities JSONB,             -- {haptic, eye_tracking, ...}
  ADD COLUMN assets_manifest_version VARCHAR(20);
```

→ *디바이스별 사용 패턴* 분석 가능 (어떤 기기에서 어떤 게임 더 완주하나).

### 6.3 자산 버전 관리

`asset_manifests` 테이블 신설:

```sql
CREATE TABLE asset_manifests (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content_type    VARCHAR(30),   -- 'mission' | 'psalm' | 'theme'
    scope           VARCHAR(50),   -- 'moses' | 'psalm-23'
    version         VARCHAR(20),   -- 'v2.3.1' (semver)

    assets          JSONB,         -- [{id, r2_key, size, required, device_filter}]

    published_at    TIMESTAMPTZ,
    deprecated_at   TIMESTAMPTZ
);
```

XR 클라이언트가 `manifest_version` 캐시 → 다음 진입 시 *변경된 자산만* 다운로드 (Delta update).

---

## 7. 보안 — XR 클라이언트 측

### 7.1 JWT 만료·갱신

- access_token: 24시간
- refresh_token: 30일 (Phase 2 — OAuth 부터)
- MVP 게스트: access_token 만료 시 *자동 재발급* (디바이스 fingerprint 기반)

### 7.2 R2 Presigned URL

- *1시간 유효*
- *user_id 토큰화* 한 키 (다른 사용자가 URL 공유 못 함)

### 7.3 클라이언트 위변조 방어

- 게임 결정 (`/api/game/{c}/scene/{n}/decide`) 서버 검증 — 클라이언트 보낸 *분기 패턴* 이 *허용 범위* 인지 (XSS 방지)
- 사용자 일기 *길이 제한* 10,000자
- API rate limit: 분당 60 요청 (게임 진행 정상 트래픽 충분)

---

## 8. 성능 — XR 의 *낮은 latency* 요구

VR/AR 에선 *모션 sickness* 방지가 핵심. 백엔드 응답 *느림* → 사용자 *멈춤·정지* → 멀미.

### 8.1 Target latency

| 동작 | p95 | p99 | 비고 |
|---|---|---|---|
| API 응답 (LLM 캐시 hit) | < 200ms | < 500ms | 게임 분기 |
| API 응답 (LLM miss) | < 2초 | < 5초 | 사용자에게 *"잠시 생각 중"* UI |
| 자산 다운로드 (R2) | 1MB 당 < 500ms | < 1초 | CDN edge 가까울수록 빠름 |
| WebSocket 메시지 | < 100ms | < 300ms | Phase 2 |

### 8.2 XR 측 대응 — *loading state 자연화*

- LLM 호출 중 *애니메이션* (예: 떨기나무 떨림 강화) — 사용자 *기다리는 줄 모름*
- 자산 다운로드 중 *씬 전환 페이드*
- 백엔드 다운 시 *오프라인 모드* — 로컬 캐시로 진행, 결과는 sync 큐

### 8.3 CDN 전략

- R2 base: Cloudflare global edge
- 사용자 → 가장 가까운 edge 자동 (한국 사용자는 Seoul edge)
- 자산 사전 캐시 워밍 — 자주 사용되는 자산 (시편 23편 등) 사전 push

---

## 9. 운영 — XR 클라이언트 모니터링

### 9.1 클라이언트 → 백엔드 텔레메트리

```
POST /api/telemetry/event
Body: {
  "event": "scene_completed" | "scene_skipped" | "haptic_max_intensity" | "error",
  "scene": "moses:scene_3",
  "duration_ms": 45000,
  "device_type": "quest3",
  "context": {...}
}
```

분석:
- 어느 Scene 에서 *건너뛰기* 가 많나
- 어느 디바이스에서 *완주율 낮나*
- 어느 햅틱이 *불편하나*

### 9.2 클라이언트 에러 보고

```
POST /api/telemetry/error
Body: {
  "error_type": "asset_download_failed" | "openxr_session_lost" | ...,
  "device_type": "...",
  "unity_version": "6.0.f1",
  "stack_trace": "..."
}
```

Grafana 대시보드:
- 디바이스별 에러율
- 자산 다운로드 실패율
- 사용자 *건너뛰기* 패턴

---

## 10. 백엔드 → XR 의 *디바이스 모름* 보장

### 10.1 백엔드 코드 베이스에서

- 헥사고날 도메인 (game/, emotion/) 안에 `device_type` 분기 *금지*
- adapter/in/web 레이어에서만 device_type 처리 (request mapping)
- *디바이스별 다른 로직* 필요하면 *클라이언트 측* 에서

### 10.2 예외 — capabilities 기반 자산 선택

자산 manifest 생성 시:
```python
def get_assets_for(scope, capabilities):
    base_assets = get_base_assets(scope)
    if not capabilities.haptic:
        # 햅틱 의존 자산 대신 audio 보강
        base_assets = swap_haptic_with_audio(base_assets)
    if capabilities.eye_tracking:
        # 시선 응시 시나리오 추가
        base_assets.add_gaze_variants()
    return base_assets
```

→ 이건 *content delivery 만의 분기*. 게임 로직 자체는 *동일*.

---

## 11. 5개 도큐 보완 정리

이번 XR 통합 요구 반영해 다음 *기존 문서 보완 필요*:

| 문서 | 보완 항목 |
|---|---|
| **DB-SCHEMA.md** | `asset_manifests` 테이블 추가, `game_sessions` 에 device_type/capabilities/assets_manifest_version 컬럼 추가 |
| **USER-FLOW.md** | 디바이스 첫 진입 *capabilities 자동 감지* 흐름 1개 추가 |
| **AI-ARCHITECTURE.md** | TTS 자산을 R2 로 deploy 하는 파이프라인 명시 |
| **CONTENT-WORKFLOW.md** | *자산 manifest* 도 검토 워크플로우에 포함 |
| **ETHICS-LEGAL.md** | 디바이스별 데이터 (예: Vision Pro 의 *시선 데이터*) 추가 명시 — *민감 정보* |

→ 각 문서 별 *appendix* 형태로 추가 가능. 이 XR-INTEGRATION.md 가 *중앙 허브* 역할.

---

## 12. 일정 (XR 통합 구현)

| Week | 작업 |
|---|---|
| 1 | Spring `/api/assets/manifest`, `/api/sync/upload` 엔드포인트 |
| 2 | `asset_manifests` Flyway 마이그레이션 + R2 업로드 파이프라인 |
| 3 | Unity 프로젝트 — OpenXR Input Action 추상화 + Quest 3 빌드 |
| 4 | Apple Vision Pro 빌드 (PolySpatial) + capabilities 차이 처리 |
| 5 | Galaxy XR 빌드 시도 (Google Android XR Plugin) |
| 6 | 3 디바이스 통합 테스트 + 성능 측정 |

---

## 13. 의도적으로 *지금 안 다룬* 것

| 항목 | 보류 이유 | 언제 |
|---|---|---|
| WebSocket 실시간 멀티 사용자 | 단일 사용자 게임 우선 | Phase 3 (소셜) |
| AR 공간 anchor 영구화 | 실내 좌표 영속 — 복잡 | Phase 3 |
| Hand co-presence (다른 사람 손) | 소셜 영역 | Phase 3 |
| Pass-through 카메라 사용 | 사생활 부담 | Phase 4 (선택) |
| 외부 게임 컨트롤러 | 추상화 이미 OpenXR 처리 | — |
| 8K texture | 자산 크기 폭증 | V2 (Vision Pro 만) |

---

## 14. 다음 단계

1. **DB-SCHEMA.md 보완** — `asset_manifests`·device_type 컬럼
2. **Unity 6 LTS 프로젝트 부트스트랩** — OpenXR + XR Interaction Toolkit
3. **R2 (Cloudflare) 버킷 생성** — `lemuel-assets`, presigned URL
4. **Spring `/api/assets/manifest` 엔드포인트 작성**
5. **TTS 사전 생성 → R2 업로드 파이프라인** (Python 스크립트)
6. **Quest 3 첫 빌드 + 테스트** (Scene 1 만)

---

> **TL;DR** — 백엔드는 *디바이스 무관* (헥사고날 도메인 격리). XR 클라이언트가 OpenXR Input Action 으로 디바이스 차이 흡수. capabilities 만 백엔드에 전달 → 백엔드가 *자산 선택* 만 분기. 자산은 R2 CDN, 게임은 manifest 기반 사전 다운로드. *낮은 latency (200ms p95)* + *오프라인 sync* 필수.
