# lemuel-xr / unity

요셉 XR 미션 Unity 6 클라이언트. **Unity Hub 에서 새 프로젝트 생성 후 이 디렉토리에 매핑**.

## 셋업 절차

1. **Unity Hub 설치** (https://unity.com/download)
2. **Unity Editor 6.0 LTS** + 다음 모듈 추가
   - Android Build Support (Quest 3 / Galaxy XR)
   - visionOS Build Support (Apple Vision Pro)
   - WebGL Build Support (선택, Phase 2 의 WebXR fallback)
3. 새 프로젝트 — Template: **Universal 3D**, 경로: `/Users/lms/lemuel-xr/unity`
4. Package Manager 에서 추가
   - `com.unity.xr.openxr` (OpenXR Plugin)
   - `com.unity.xr.interaction.toolkit` (XR Interaction Toolkit)
   - `com.meta.xr.sdk.all` (Meta XR SDK for Quest 3)
   - `com.unity.polyspatial.xr` (Apple PolySpatial — Vision Pro)
5. Project Settings → XR Plug-in Management → 각 플랫폼 OpenXR 활성화

## 디렉토리 구조 (예정)

```
unity/
├── Assets/
│   ├── Scenes/
│   │   ├── 01_Dream.unity        Scene 1 파라오 꿈
│   │   ├── 02_StorageDecision.unity   Scene 2 풍년 저장
│   │   ├── 03_Distribution.unity      Scene 3 흉년 분배 (핵심)
│   │   ├── 04_Reunion.unity           Scene 4 형제 재회
│   │   └── 05_Outro.unity             Scene 5 결말
│   ├── Scripts/
│   │   ├── Api/                  Spring backend HTTP 클라이언트
│   │   ├── Interaction/          XR Grab / Socket / Eye Gaze
│   │   ├── Audio/                TTS 호출 + AudioSource 재생
│   │   └── Game/                 GameStateMachine, SceneFlow
│   └── Audio/                    BGM, 사전 녹음
└── ProjectSettings/
```

## Backend API 연동

| Unity 호출 | Backend |
|---|---|
| `EmotionApi.Classify(text)` | `POST /api/emotion/classify` |
| `JosephGameApi.Start(userId)` | `POST /api/game/joseph/start` |
| `JosephGameApi.Decide(sid, sceneId, decision)` | `POST /api/game/joseph/{sid}/decide` |
| `JosephGameApi.Complete(sid, outcome)` | `POST /api/game/joseph/{sid}/complete` |
| `ScriptureApi.Get(ref)` | `GET /api/scripture/{ref}` |
| `TtsApi.Synthesize(text)` | `POST {TTS_BASE_URL}/tts` |

`TTS_BASE_URL` 은 backend 가 reverse proxy 로 노출하거나, Unity 가 cluster ingress 의 `/tts/` path 로 직접 호출.

## Git LFS

Unity 바이너리 에셋 (`.psd`, `.fbx`, `.wav`, `.png` 등) 은 LFS 로 추적. 루트 `.gitattributes` 참고.
