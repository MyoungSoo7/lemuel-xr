// JosephSceneBootstrap.cs — Scene 1 만 띄우는 최소 entrypoint.
// 빈 Scene 의 GameObject 에 attach 하고 baseUrl 만 입력하면 동작.
// 작동:
//   1. /api/config/xr-modes?mission=joseph        호출 → AR 토글을 그릴지 결정
//   2. /api/config/input-mapping?device=quest3&mode=... 호출 → 입력 매핑 log
//   3. /api/config/asset-manifest?mission=joseph&device=quest3&scene=1&mode=... 호출
//   4. manifest 안의 에셋들 R2 다운로드 시도 → 진척 + 에러 log
//   5. R4 동의 카드 (단순 console / OnGUI button) 노출
//
// 요셉만 AR 이 열려 있다 — 다른 미션에 mode=ar 로 물으면 백엔드가 400 으로 막는다.

using System;
using System.Collections;
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

        [Tooltip("몰입 모드 — vr | ar. AR 은 요셉에서만 열린다.")]
        public string xrMode = "vr";

        private LemuelApiClient _api;
        private ManifestLoader _loader;

        private string _status = "초기화 대기";
        private float _progress;
        private bool _consentGranted;
        private bool _consentRendered;
        private AssetManifest _manifest;
        private bool _arAvailable;

        void Start()
        {
            _api = new LemuelApiClient(baseUrl);
            _loader = new ManifestLoader(_api);

            // 1) 이 미션이 AR 을 여는지 먼저 묻는다. 임의로 mode=ar 을 던지지 않는다 —
            //    닫힌 미션이면 400 이고, 그 400 을 UI 로 번역하느니 애초에 안 그리는 게 낫다.
            StartCoroutine(_api.GetXrModes(mission,
                modes =>
                {
                    _arAvailable = modes.modes != null && Array.IndexOf(modes.modes, "ar") >= 0;
                    if (!_arAvailable && xrMode == "ar")
                    {
                        Debug.LogWarning($"[Lemuel] {mission} 은 AR 을 열지 않는다 — VR 로 되돌림");
                        xrMode = "vr";
                    }
                    Debug.Log($"[Lemuel] xr-modes: {string.Join(",", modes.modes ?? new string[0])}");
                    StartCoroutine(FetchInputMapping());
                },
                err =>
                {
                    // 구버전 백엔드엔 이 엔드포인트가 없다 — VR 로 진행한다.
                    Debug.LogWarning($"[Lemuel] xr-modes err ({err}) — VR 로 진행");
                    _arAvailable = false;
                    xrMode = "vr";
                    StartCoroutine(FetchInputMapping());
                }));
        }

        private IEnumerator FetchInputMapping()
        {
            return _api.GetInputMapping(device,
                mapping =>
                {
                    Debug.Log($"[Lemuel] input-mapping ok ({xrMode}): GRAB.source={mapping.GRAB?.source} binding={mapping.GRAB?.binding}");
                    if (xrMode == "ar")
                    {
                        Debug.Log($"[Lemuel] AR 배치: {mapping.PLACE_ON_SURFACE?.source}/{mapping.PLACE_ON_SURFACE?.binding}");
                    }
                    _status = "input-mapping 수신 — R4 동의 카드 대기";
                },
                err =>
                {
                    Debug.LogError($"[Lemuel] input-mapping err: {err}");
                    _status = "input-mapping 실패: " + err;
                },
                xrMode);
        }

        void OnGUI()
        {
            GUILayout.Space(10);
            GUILayout.Label($"<b>lemuel-xr stub</b> — {mission}/{device}/scene{sceneNumber} [{xrMode}]");
            GUILayout.Label($"status: {_status}");
            GUILayout.Label($"progress: {(_progress * 100f):F1}%");

            // R4 동의 카드 — 매우 단순화한 버전 (실 콘텐츠는 XR Canvas).
            if (!_consentGranted)
            {
                // AR 토글은 백엔드가 그 미션에 AR 을 열었을 때만 그린다.
                if (_arAvailable)
                {
                    bool ar = GUILayout.Toggle(xrMode == "ar", "AR — 내 방을 배경으로 (패스스루)");
                    xrMode = ar ? "ar" : "vr";
                }

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
                            _status = $"manifest+에셋 로드 완료 [{m.xrMode}] — {m.manifest.models.Count} models, {m.manifest.audio.Count} audio, {m.totalSizeBytes:N0} bytes";
                        },
                        err => _status = "다운로드 일부 실패 (R2 미배포면 정상): " + err,
                        xrMode));
                }
                GUILayout.EndHorizontal();
                GUILayout.Label("위기 시 109 자살예방 · 1577-0199 정신건강상담 · 24시간");
            }
        }
    }
}
