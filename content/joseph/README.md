# joseph — MVP XR 콘텐츠 (Unity 가 직접 로드)

`docs/MVP-JOSEPH-CONTENT.md` 의 5개 Scene 을 *Scene yml + AssetManifest JSON* 으로 분해. 디바이스 무관 게임 로직 (yml) + 디바이스별 자산 (manifests/{device}/) 분리.

## 디렉토리 구조

```
content/joseph/
├── README.md                              ← 본 문서
├── scene1.yml ~ scene5.yml                ← Unity ScriptableObject deserialize
└── manifests/
    ├── quest3/scene{1..5}-v1.0.0.json     ← 기준 (PBR + LOD 3단)
    ├── visionpro/scene{1..5}-v1.0.0.json  ← PBR 4k 업스케일 + eye tracking + spatial_audio personal
    ├── galaxyxr/scene{1..5}-v1.0.0.json   ← LOD 4단 + 멀리 imposter
    └── web/scene{1..5}-v1.0.0.json        ← 360 image fallback + click+drag
```

## Unity 가 어떻게 로드하는가

1. 부팅 시 Unity 가 `XRDeviceProfile.cs` 로 디바이스 감지 → `device_type` 결정 (`quest3` | `visionpro` | `galaxyxr` | `web`).
2. 게임 진입 시 `MissionLoader.LoadScene(missionId="joseph", sceneId="joseph_scene1")` 호출:
   - `Resources/content/joseph/scene1.yml` → `SceneDefinition` ScriptableObject 로 deserialize (YamlDotNet).
   - 그 다음 `Resources/content/joseph/manifests/{device_type}/scene1-v1.0.0.json` → `AssetManifest` ScriptableObject 로 deserialize.
3. `AssetDownloader` 가 manifest 의 `models/audio/textures/scripts` URL 을 R2 CDN 에서 일괄 받아 `Application.persistentDataPath/joseph/v1.0.0/` 에 캐싱.
4. 캐시 hit 후 `SceneRunner` 가 yml 의 `narration_blocks` / `interactions` / `branches` / `safety_gates` 를 timeline 으로 실행. 분기 결정은 백엔드 (`POST /api/game/joseph/scene/{n}/decide` 등) 로 전송.
5. `next_scene_id` 가 set 이면 같은 절차 반복, `transition_to_outro: true` 이면 메인 화면 복귀.

## 백엔드 (V10 asset_manifests) 와의 sync

manifest JSON 파일은 V10 `asset_manifests.manifest` JSONB 컬럼에 *그대로 INSERT 가능*. sync 절차:

```bash
# 파이프라인 (백엔드 측 — scripts/sync_asset_manifests.py)
for f in content/joseph/manifests/*/*.json; do
  payload=$(cat "$f")
  psql -c "INSERT INTO asset_manifests
           (mission_id, scene_number, device_type, capabilities_min, version,
            manifest, audio_locale, total_size_bytes, cdn_base_url)
           VALUES
           ('joseph',
            ($payload->>'scene_number')::smallint,
            $payload->>'device_type',
            $payload->'capabilities_min',
            $payload->>'version',
            $payload->'manifest',
            $payload->>'audio_locale',
            ($payload->>'total_size_bytes')::bigint,
            $payload->>'cdn_base_url')
           ON CONFLICT DO NOTHING;"
done
```

신 버전 publish 시 `superseded_at = NOW()` 로 기존 row 비활성, 새 row insert. 클라이언트는 `idx_asset_manifests_lookup` 로 `(mission_id, scene_number, device_type, version) WHERE is_active = TRUE` 조회.

## 작성·검수 가이드

- yml 의 텍스트는 `lemuel-xr-theology-tone` + `lemuel-xr-mental-health-safety` 사전 가이드 적용 (R1~R5, J1 인용, "AI 보조 — 본문은 성경 참조" footer 영구).
- 출판 전 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` agent 사후 검수 필수.
- 가짜 URL·size 는 추정값. 실제 자산 빌드 후 R2 업로드 시 갱신.
