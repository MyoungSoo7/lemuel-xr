# moses — MVP XR 콘텐츠 (Unity 가 직접 로드)

`docs/MVP-MOSES-CONTENT.md` 의 6개 Scene 을 *Scene yml + AssetManifest JSON* 으로 분해. 디바이스 무관 게임 로직 (yml) + 디바이스별 자산 (manifests/{device}/) 분리. 컨벤션은 `content/joseph/` 와 동일.

## 디렉토리 구조

```
content/moses/
├── README.md                              ← 본 문서
├── scene1.yml ~ scene6.yml                ← Unity ScriptableObject deserialize
└── manifests/
    ├── quest3/scene{1..6}-v1.0.0.json     ← 기준 (PBR + LOD 3단)
    ├── visionpro/scene{1..6}-v1.0.0.json  ← PBR 4k 업스케일 + eye tracking + spatial_audio personal
    ├── galaxyxr/scene{1..6}-v1.0.0.json   ← LOD 4단 + 멀리 imposter
    └── web/scene{1..6}-v1.0.0.json        ← 360 image fallback + click+drag
```

## Joseph 와 다른 점

- *Scene 3 (5 변명 카드)* 이 미션의 **중심** — 분기·LLM 호출이 가장 복잡. 카드 5 × 2 행위(놓기/치우기) × 3 faith_tone = 30 사전 캐싱 + 패턴 분류기 (1/2~3/5/0장 4종) → Scene 6 lookup_key 확장. `interactions` 4개 (grab / place_at_anchor / set_aside / skip).
- *Scene 4 (표적)* 진입 직전 R4 2차 동의 카드 강제 노출 — 시각화 *건너뛰기* 시 narration_only_path 로 진행.
- *Scene 2 (떨기나무)* 신발 벗기 1.5초 dwell + 10초 후 버튼 폴백 → `capabilities_min.scene_specific_hand_tracking_required_for_shoes_gesture: true`.
- TTS voice_id = `bush_v1` (떨기나무, 절제된 내레이터 톤) — Joseph 의 `pharaoh_v1`/`joseph_v1` 와 분리.

## Unity 가 어떻게 로드하는가

1. 부팅 시 `XRDeviceProfile.cs` 로 `device_type` 결정 (`quest3` | `visionpro` | `galaxyxr` | `web`).
2. `MissionLoader.LoadScene(missionId="moses", sceneId="moses_scene1")` → `scene1.yml` → `SceneDefinition` (YamlDotNet) + `manifests/{device_type}/scene1-v1.0.0.json` → `AssetManifest`.
3. `AssetDownloader` 가 manifest URL 을 R2 CDN 에서 받아 `Application.persistentDataPath/moses/v1.0.0/` 캐싱. `SceneRunner` 가 `narration_blocks` / `interactions` / `branches` / `safety_gates` 를 timeline 으로 실행 — 분기는 백엔드 (`POST /api/game/moses/scene/{n}/decide|card|consent|step|complete`) 로 전송. `next_scene_id` set 이면 반복, `transition_to_outro: true` 이면 메인.

## 백엔드 (V10 asset_manifests) 와의 sync

manifest JSON 은 V10 `asset_manifests.manifest` JSONB 컬럼에 *그대로 INSERT 가능*. Joseph 과 동일 (`scripts/sync_asset_manifests.py` 의 mission_id 만 `moses` 로):

```bash
for f in content/moses/manifests/*/*.json; do
  psql -c "INSERT INTO asset_manifests (mission_id, scene_number, device_type, capabilities_min, version, manifest, audio_locale, total_size_bytes, cdn_base_url) VALUES ('moses', (\$1->>'scene_number')::smallint, \$1->>'device_type', \$1->'capabilities_min', \$1->>'version', \$1->'manifest', \$1->>'audio_locale', (\$1->>'total_size_bytes')::bigint, \$1->>'cdn_base_url') ON CONFLICT DO NOTHING;" -v "1=$(cat $f)"
done
```

신 버전 publish 시 `superseded_at = NOW()` 로 기존 row 비활성, 새 row insert. 클라이언트는 `idx_asset_manifests_lookup` 으로 `(mission_id, scene_number, device_type, version) WHERE is_active = TRUE` 조회.

## 작성·검수 가이드

- yml 텍스트는 `lemuel-xr-theology-tone` + `lemuel-xr-mental-health-safety` 사전 가이드 적용 (R1~R5, M1·M2 인용, "AI 보조 — 본문은 성경 참조" footer 영구). R4 두 곳 강제: Pre-Scene 0 1차 + Scene 4 2차. disputed_points: 출 3:14 (Young vs Brueggemann) / 출 4:14 (진노 회피) / 출 4:24~26 (범위 밖) 세 곳.
- 출판 전 `lemuel-theology-reviewer` + `lemuel-mental-health-safety` agent 사후 검수 필수 (특히 Scene 3 카드 ②·⑤). 가짜 URL·size 추정값.
