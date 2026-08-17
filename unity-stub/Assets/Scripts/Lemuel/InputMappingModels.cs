// InputMappingModels.cs — /api/config/input-mapping 응답 DTO.
// 응답이 임의 키 (GRAB / POINT_AT / GAZE_DURATION ...) 의 dict 라
// Unity JsonUtility 의 한계로 *device 별 known 키만* 모델링.

using System;
using UnityEngine;

namespace Lemuel
{
    [Serializable]
    public class InputMapping
    {
        public InputAction GRAB;
        public InputAction POINT_AT;
        public InputAction GAZE_DURATION;

        // AR 오버레이 (mode=ar 일 때만 채워짐) — 실제 방을 배경으로 쓰는 데 필요한 액션.
        public InputAction PLACE_ON_SURFACE;   // 평면 히트테스트로 소품 배치
        public InputAction RECENTER_ANCHOR;    // 앵커 재설정
        public InputAction LOCOMOTION;         // AR 에선 physical_walk — 인공 이동 없음
    }

    /// /api/config/xr-modes 응답 — 이 미션이 여는 몰입 모드.
    [Serializable]
    public class XrModes
    {
        public string missionId;
        public string[] modes;      // 예: ["vr","ar"] (요셉) / ["vr"] (그 외)
    }

    [Serializable]
    public class InputAction
    {
        public string source;       // controller | hand | head | eye | eye+hand
        public string binding;      // grip | pinch | raycast | head_direction_dwell ...
        public InputAction fallback; // 일부는 fallback 둠 (예: Quest3 GRAB → grip / fallback pinch)
        public InputAction confirm;  // Vision Pro AR 배치: 시선으로 겨냥 → 핀치로 확정
    }
}
