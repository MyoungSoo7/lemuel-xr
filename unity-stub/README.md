# lemuel-xr — Unity Sample Stub

> **목적**: lemuel-xr 백엔드 (`/api/config/asset-manifest` + `/api/config/input-mapping`) 와 R2 CDN 다운로드 *계약 (contract)* 을 Unity 쪽에서 검증하기 위한 *최소 스크립트* 모음.
> **상태**: Unity 6 LTS / XR Interaction Toolkit 미사용 / `OnGUI` 만으로 동작.
> **실 작동 한계**: R2 CDN 에 실 에셋이 아직 없어서 다운로드 단계는 4xx/5xx 가 정상. *manifest 수신·파싱·진척 콜백* 까지가 검증 범위.

---

## 파일 구조

```
unity-stub/
└── Assets/Scripts/Lemuel/
    ├── LemuelApiClient.cs       — UnityWebRequest GET/POST wrapper
    ├── AssetManifestModels.cs   — /api/config/asset-manifest DTO
    ├── InputMappingModels.cs    — /api/config/input-mapping DTO
    ├── ManifestLoader.cs        — manifest fetch → R2 다운로드 코루틴
    └── JosephSceneBootstrap.cs  — Scene 1 entrypoint (OnGUI 로 R4 동의 카드)
```

---

## Unity 에 import 하기

1. Unity 6 LTS 새 프로젝트 생성 (template: 3D / URP 어느 쪽이든)
2. `unity-stub/Assets/Scripts/Lemuel/` 를 프로젝트 `Assets/Scripts/Lemuel/` 로 복사
3. 빈 Scene 의 빈 GameObject 에 `JosephSceneBootstrap.cs` 컴포넌트 attach
4. Inspector 에서 설정:
   - `baseUrl` — `http://localhost:8080` (로컬) 또는 lemuel 서버 도메인
   - `device` — `quest3`
   - `mission` — `joseph`
   - `sceneNumber` — `1`
   - `xrMode` — `vr` (기본) 또는 `ar`
5. Play 누르면 Game View 좌상단에 status / progress 표시

> AR 토글은 백엔드가 그 미션에 AR 을 열었을 때만 그려진다. 지금은 요셉·모세·다윗이
> 열려 있고, `mission` 을 `jesus` 로 바꾸면 토글이 사라진다 — 스텁이 임의로 `mode=ar` 을
> 던져서 400 을 받는 대신, 먼저 `/api/config/xr-modes` 로 물어보기 때문이다.

---

## 검증 시나리오

| 단계 | 기대 동작 | 실패 신호 |
|---|---|---|
| 1. 부팅 | `input-mapping ok: GRAB.source=controller binding=grip` 콘솔 로그 | `input-mapping err: ...` |
| 2. R4 동의 카드 | OnGUI 에 "건너뛰기" + "지금 시작" 버튼 | 카드가 안 보이면 OnGUI 잘못 attach |
| 3. "지금 시작" 클릭 | `manifest+에셋 로드 완료 — 5 models, 6 audio, ~12MB` | manifest 만 받고 다운로드 0% — 정상 (R2 빔) |
| 4. progress 바 | 0% → 일부 % 로 진행 후 stuck (R2 빔) | 0% 도 못 가면 manifest 파싱 실패 |
| 5. AR 토글 (요셉) | 토글 켜면 `[ar]`, 로드 후 models 4개·환경 모델 없음 | 토글이 안 보이면 xr-modes 실패 |
| 6. AR 토글 (예수) | 토글이 아예 안 보임 — 에셋 없는 미션 | 보이면 게이트가 뚫린 것 |

---

## 백엔드 응답 예시 (확인용)

### `GET /api/config/input-mapping?device=quest3`
```json
{
  "GRAB": {"source":"controller","binding":"grip","fallback":{"source":"hand","binding":"pinch"}},
  "POINT_AT": {"source":"controller","binding":"raycast"},
  "GAZE_DURATION": {"source":"head","binding":"head_direction_dwell"}
}
```

### `GET /api/config/xr-modes?mission=joseph`
```json
{ "missionId": "joseph", "modes": ["vr", "ar"] }
```
예수·룻 등 에셋 없는 미션은 `["vr"]` 만 돌아온다.

### `GET /api/config/input-mapping?device=quest3&mode=ar`
```json
{
  "GRAB": {"source":"controller","binding":"grip","fallback":{"source":"hand","binding":"pinch"}},
  "POINT_AT": {"source":"controller","binding":"raycast"},
  "GAZE_DURATION": {"source":"head","binding":"head_direction_dwell"},
  "PLACE_ON_SURFACE": {"source":"controller","binding":"raycast_plane_hittest","fallback":{"source":"hand","binding":"pinch_plane_hittest"}},
  "RECENTER_ANCHOR": {"source":"controller","binding":"menu_long_press"},
  "LOCOMOTION": {"source":"room_scale","binding":"physical_walk"}
}
```
VR 매핑 위에 AR 오버레이가 얹힌 형태다.

### `GET /api/config/asset-manifest?mission=joseph&device=quest3&scene=1`
```json
{
  "id": "...uuid...",
  "missionId": "joseph",
  "sceneNumber": 1,
  "deviceType": "quest3",
  "xrMode": "vr",
  "version": "1.0.0",
  "cdnBaseUrl": "https://cdn.r2.dev/lemuel-xr/",
  "totalSizeBytes": 12345678,
  "manifest": {
    "models": [{"id":"...","url":"https://cdn.r2.dev/...","size_bytes":...,"lod":"lod3_pbr"}, ...],
    "audio":  [...],
    "textures": [...]
  }
}
```

`&mode=ar` 를 붙이면 같은 씬의 AR manifest 가 온다 — 환경 모델(`env_*`)과 원경
임포스터(`*_far`)가 빠지고 `script_ar_anchor_placement` 가 붙어, scene 1 기준
24.8MB → 11.6MB 다. AR 이 열리지 않은 미션에 `mode=ar` 를 던지면 400 이다.

---

## 한계 + 다음 단계

- **JsonUtility 한계**: 임의 키 dict (capabilities_min 같은) 는 모델링 안 함. 운영용으로는 Newtonsoft.Json 또는 SimpleJSON 도입.
- **R4 카드**: 현재 `OnGUI` — 실 XR 콘텐츠는 World Space Canvas + XR Interaction Toolkit Raycaster.
- **다운로드 캐싱**: `Application.persistentDataPath` 에 저장. 정합성 (hash·size 검증) 미구현.
- **에셋 적용**: 다운로드된 .glb 를 실제 GameObject 로 instantiate 하는 단계 없음 — glTFast 패키지 필요.
- **R1 자해 키워드 게이트**: 클라이언트 음성 입력 → 백엔드 `safety` endpoint 호출 흐름은 stub 외부 (Phase 2).

---

*이 stub 은 lemuel-xr 백엔드 contract 검증용. 실 Unity XR 콘텐츠는 별도 Unity 프로젝트로 진행.*
