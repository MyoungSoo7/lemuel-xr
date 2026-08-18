// LemuelApiClient.cs — lemuel-xr 백엔드 REST 호출 wrapper.
// Unity 6 LTS / .NET 9 / UnityWebRequest.

using System;
using System.Collections;
using System.Text;
using UnityEngine;
using UnityEngine.Networking;

namespace Lemuel
{
    /// <summary>
    /// /api/config/asset-manifest / /api/config/input-mapping / /api/game/* 호출.
    /// MonoBehaviour 가 아니므로 어디서든 new LemuelApiClient(baseUrl) 로 생성.
    /// 모든 호출은 IEnumerator — StartCoroutine 으로 실행.
    /// </summary>
    public class LemuelApiClient
    {
        private readonly string _baseUrl;
        private readonly int _timeoutSeconds;

        public LemuelApiClient(string baseUrl, int timeoutSeconds = 10)
        {
            _baseUrl = baseUrl.TrimEnd('/');
            _timeoutSeconds = timeoutSeconds;
        }

        /// <param name="mode">"vr" | "ar". 비우면 서버가 vr 로 본다.
        /// 그 미션이 열지 않은 모드면 400 — 폴백하지 않는다.</param>
        public IEnumerator GetAssetManifest(string mission, string device, int? sceneNumber,
                                              Action<AssetManifest> onOk, Action<string> onErr,
                                              string mode = null)
        {
            string url = $"{_baseUrl}/api/config/asset-manifest?mission={mission}&device={device}";
            if (sceneNumber.HasValue) url += $"&scene={sceneNumber.Value}";
            if (!string.IsNullOrEmpty(mode)) url += $"&mode={mode}";
            yield return GetJson<AssetManifest>(url, onOk, onErr);
        }

        public IEnumerator GetInputMapping(string device,
                                             Action<InputMapping> onOk, Action<string> onErr,
                                             string mode = null)
        {
            string url = $"{_baseUrl}/api/config/input-mapping?device={device}";
            if (!string.IsNullOrEmpty(mode)) url += $"&mode={mode}";
            yield return GetJson<InputMapping>(url, onOk, onErr);
        }

        /// 이 미션이 노출하는 몰입 모드 목록. AR 토글을 그릴지 여기서 정한다.
        public IEnumerator GetXrModes(string mission,
                                        Action<XrModes> onOk, Action<string> onErr)
        {
            yield return GetJson<XrModes>($"{_baseUrl}/api/config/xr-modes?mission={mission}", onOk, onErr);
        }

        public IEnumerator StartGameSession(string mission, string userId,
                                              Action<GameSessionStart> onOk, Action<string> onErr)
        {
            string url = $"{_baseUrl}/api/game/{mission}/start";
            string body = $"{{\"user_id\":\"{userId}\"}}";
            yield return PostJson<GameSessionStart>(url, body, onOk, onErr);
        }

        public IEnumerator PostSceneDecision(string mission, int sceneNumber, string bodyJson,
                                               Action<string> onOk, Action<string> onErr)
        {
            string url = $"{_baseUrl}/api/game/{mission}/scene/{sceneNumber}/decide";
            yield return PostJson<string>(url, bodyJson,
                raw => onOk?.Invoke(raw), onErr);
        }

        // ---------- 내부 헬퍼 ----------

        private IEnumerator GetJson<T>(string url, Action<T> onOk, Action<string> onErr)
        {
            using UnityWebRequest req = UnityWebRequest.Get(url);
            req.timeout = _timeoutSeconds;
            yield return req.SendWebRequest();

            if (req.result != UnityWebRequest.Result.Success)
            {
                onErr?.Invoke($"GET {url} 실패: {req.error} ({req.responseCode})");
                yield break;
            }
            try
            {
                T parsed = JsonUtility.FromJson<T>(req.downloadHandler.text);
                onOk?.Invoke(parsed);
            }
            catch (Exception e)
            {
                onErr?.Invoke($"JSON 파싱 실패: {e.Message}");
            }
        }

        private IEnumerator PostJson<T>(string url, string bodyJson,
                                          Action<T> onOk, Action<string> onErr)
        {
            using UnityWebRequest req = new UnityWebRequest(url, "POST");
            byte[] body = Encoding.UTF8.GetBytes(bodyJson);
            req.uploadHandler = new UploadHandlerRaw(body);
            req.downloadHandler = new DownloadHandlerBuffer();
            req.SetRequestHeader("Content-Type", "application/json");
            req.timeout = _timeoutSeconds;
            yield return req.SendWebRequest();

            if (req.result != UnityWebRequest.Result.Success)
            {
                onErr?.Invoke($"POST {url} 실패: {req.error} ({req.responseCode})");
                yield break;
            }
            try
            {
                if (typeof(T) == typeof(string))
                {
                    onOk?.Invoke((T)(object)req.downloadHandler.text);
                }
                else
                {
                    T parsed = JsonUtility.FromJson<T>(req.downloadHandler.text);
                    onOk?.Invoke(parsed);
                }
            }
            catch (Exception e)
            {
                onErr?.Invoke($"JSON 파싱 실패: {e.Message}");
            }
        }
    }
}
