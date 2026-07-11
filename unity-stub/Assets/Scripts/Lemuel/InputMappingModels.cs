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
    }

    [Serializable]
    public class InputAction
    {
        public string source;       // controller | hand | head | eye | eye+hand
        public string binding;      // grip | pinch | raycast | head_direction_dwell ...
        public InputAction fallback; // 일부는 fallback 둠 (예: Quest3 GRAB → grip / fallback pinch)
    }
}
