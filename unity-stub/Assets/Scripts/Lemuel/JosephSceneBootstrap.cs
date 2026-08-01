// JosephSceneBootstrap.cs — Scene 1 만 띄우는 최소 entrypoint.
// 빈 Scene 의 GameObject 에 attach 하고 baseUrl 만 입력하면 동작.
// 작동:
//   1. /api/config/input-mapping?device=quest3   호출 → 입력 매핑 log
//   2. /api/config/asset-manifest?mission=joseph&device=quest3&scene=1 호출
//   3. manifest 안의 에셋들 R2 다운로드 시도 → 진척 + 에러 log
//   4. R4 동의 카드 (단순 console / OnGUI button) 노출

using UnityEngine;

namespace Lemuel
{
    public class JosephSceneBootstrap : MonoBehaviour
    {
        [Header("백엔드 설정")]
        [Tooltip("lemuel-xr 백엔드 base URL — 예: http://localhost:8080 또는 https://api.lemuel.lan")]
        public string baseUrl = "http://localhost:8080";

        [Tooltip("디바이스 — quest3 | visionpro | galaxyxr | web")]
        public string device = "quest3";

        [Tooltip("미션")]
        public string mission = "joseph";

        [Tooltip("Scene 번호 (1..5)")]
        public int sceneNumber = 1;

        private LemuelApiClient _api;
        private ManifestLoader _loader;

        private string _status = "초기화 대기";
        private float _progress;
        private bool _consentGranted;
        private bool _consentRendered;
        private AssetManifest _manifest;

        void Start()
        {
            _api = new LemuelApiClient(baseUrl);
            _loader = new ManifestLoader(_api);

            // 1) input-mapping 먼저
            StartCoroutine(_api.GetInputMapping(device,
                mapping =>
                {
                    Debug.Log($"[Lemuel] input-mapping ok: GRAB.source={mapping.GRAB?.source} binding={mapping.GRAB?.binding}");
                    _status = "input-mapping 수신 — R4 동의 카드 대기";
                },
                err =>
                {
                    Debug.LogError($"[Lemuel] input-mapping err: {err}");
                    _status = "input-mapping 실패: " + err;
                }));
        }

        void OnGUI()
        {
            GUILayout.Space(10);
            GUILayout.Label($"<b>lemuel-xr stub</b> — {mission}/{device}/scene{sceneNumber}");
            GUILayout.Label($"status: {_status}");
            GUILayout.Label($"progress: {(_progress * 100f):F1}%");

            // R4 동의 카드 — 매우 단순화한 버전 (실 콘텐츠는 XR Canvas).
            if (!_consentGranted)
            {
                GUILayout.Space(10);
                GUILayout.Label("이 미션은 가족 갈등·이별·재회 표현을 포함합니다. (Pre-Scene 0 — R4)");
                GUILayout.BeginHorizontal();
                if (GUILayout.Button("건너뛰기 — 다른 미션 보기"))
                {
                    _status = "사용자가 건너뛰기 선택 — 종료";
                }
                if (GUILayout.Button("지금 시작"))
                {
                    _consentGranted = true;
                    _consentRendered = true;
                    StartCoroutine(_loader.LoadMission(mission, device, sceneNumber,
                        p => _progress = p,
                        (m, root) =>
                        {
                            _manifest = m;
                            _status = $"manifest+에셋 로드 완료 — {m.manifest.models.Count} models, {m.manifest.audio.Count} audio, {m.totalSizeBytes:N0} bytes";
                        },
                        err => _status = "다운로드 일부 실패 (R2 미배포면 정상): " + err));
                }
                GUILayout.EndHorizontal();
                GUILayout.Label("위기 시 109 자살예방 · 1577-0199 정신건강상담 · 24시간");
            }
        }
    }
}
