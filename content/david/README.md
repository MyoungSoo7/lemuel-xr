# david — MVP XR 콘텐츠 (Unity 가 직접 로드)

`docs/MVP-DAVID-CONTENT.md` 의 6개 Scene 을 *Scene yml + AssetManifest JSON* 으로 분해. 디바이스 무관 게임 로직 (yml) + 디바이스별 자산 (manifests/{device}/) 분리.

## 디렉토리 구조

```
content/david/
├── README.md                              ← 본 문서
├── scene1.yml ~ scene6.yml                ← Unity ScriptableObject deserialize
└── manifests/
    ├── quest3/scene{1..6}-v1.0.0.json     ← 기준 (PBR + LOD 3단)
    ├── visionpro/scene{1..6}-v1.0.0.json  ← PBR 4k 업스케일 + eye tracking + spatial_audio personal
    ├── galaxyxr/scene{1..6}-v1.0.0.json   ← LOD 4단 + 멀리 imposter (Scene 1 양 12→6)
    └── web/scene{1..6}-v1.0.0.json        ← 360 image fallback + click+drag
```

## Unity 가 어떻게 로드하는가

1. 부팅 시 Unity 가 `XRDeviceProfile.cs` 로 디바이스 감지 → `device_type` 결정.
2. 게임 진입 시 `MissionLoader.LoadScene(missionId="david", sceneId="david_scene1")` 호출:
   - `Resources/content/david/scene1.yml` → `SceneDefinition` ScriptableObject 로 deserialize (YamlDotNet).
   - `Resources/content/david/manifests/{device_type}/scene1-v1.0.0.json` → `AssetManifest` ScriptableObject 로 deserialize.
3. `AssetDownloader` 가 manifest URL 을 R2 CDN 에서 받아 `Application.persistentDataPath/david/v1.0.0/` 캐싱.
4. `SceneRunner` 가 yml 의 `narration_blocks` / `interactions` / `branches` / `safety_gates` 를 timeline 으로 실행. 분기 결정은 백엔드 (`POST /api/game/david/scene/{n}/decide|armor|stones|throw` 등) 로 전송.
5. `next_scene_id` 가 set 이면 다음 Scene, `transition_to_outro: true` 이면 메인 화면 복귀.

## 백엔드 (V10 asset_manifests) 와의 sync

Joseph 과 동일 — manifest JSON 을 `asset_manifests.manifest` JSONB 컬럼에 그대로 INSERT. `scripts/sync_asset_manifests.py` 의 mission_id 만 `david` 로 호출.

## David 의 차별점 — Scene 4 가 미션 중심

다른 미션과 달리 *Scene 4 (5 돌 선택)* 이 게임 길이의 1/3 (180s) 을 차지하는 *미션 중심* 이다. *5 색 돌 ↔ 5 감정* 매핑 (빨강=분노, 파랑=슬픔, 회색=외로움, 노랑=희망, 검정=두려움) 으로 사용자가 *어느 5 개* 를 *어떤 순서로* 주머니에 넣는가가 핵심 신호. `interaction_meta.stone_selection_order` + `color_distribution` 의 *5돌 패턴 분석* 이 Day 30 리포트 (`v_user_30d_summary.emotion_diversity_index`) 의 *감정 다양성* 지표에 직접 매핑 (잠 4:23 *마음을 지키라* TRACK-A-6 경계 형성). 같은 색 반복도, 4/5 도, 중립 회색만 5개도 정당 (R3).

## 골리앗 모델 — Scene 3 만 풀, Scene 5 는 imposter

`model_goliath` (Quest 3 기준 ~25MB, VisionPro ~30MB) 는 Scene 3 manifest 에서만 다운로드. Scene 5 에서는 거리 12m 의 *imposter* (~1.7MB) 로 대체 — 사용자가 sling 던지기에 집중하고, 골리앗 직접 보기는 Scene 3 의 정체성 게이트에서 끝난다.

## 작성·검수 가이드

- yml 의 텍스트는 `lemuel-xr-theology-tone` + `lemuel-xr-mental-health-safety` 사전 가이드 적용 (R1~R5, D1 인용, *AI 보조 — 본문은 성경 참조* footer 영구, *믿음과 능력은 적이 아니다* 추가 footer Scene 1·3).
- 출판 전 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` agent 사후 검수 필수.
- 가짜 URL·size 는 추정값. 실제 자산 빌드 후 R2 업로드 시 갱신.
