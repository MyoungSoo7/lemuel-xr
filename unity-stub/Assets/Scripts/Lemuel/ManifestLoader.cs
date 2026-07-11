// ManifestLoader.cs — 미션 시작 시 manifest fetch → R2 CDN 에서 에셋 다운로드.
// 모든 다운로드는 코루틴 sequential 또는 병렬 (총 사이즈 표시 + 진척 콜백).

using System;
using System.Collections;
using System.IO;
using UnityEngine;
using UnityEngine.Networking;

namespace Lemuel
{
    public class ManifestLoader
    {
        private readonly LemuelApiClient _api;
        private readonly string _cacheDir;

        public ManifestLoader(LemuelApiClient api)
        {
            _api = api;
            _cacheDir = Path.Combine(Application.persistentDataPath, "lemuel-cache");
            Directory.CreateDirectory(_cacheDir);
        }

        /// <summary>
        /// 1) /api/config/asset-manifest 호출
        /// 2) manifest 안의 models·audio·textures 모두 R2 에서 다운로드
        /// 3) 로컬 캐시에 저장 후 manifest 와 함께 onComplete
        /// </summary>
        public IEnumerator LoadMission(string mission, string device, int sceneNumber,
                                         Action<float> onProgress,
                                         Action<AssetManifest, string> onComplete,
                                         Action<string> onError)
        {
            AssetManifest manifest = null;
            string apiError = null;

            yield return _api.GetAssetManifest(mission, device, sceneNumber,
                ok => manifest = ok,
                err => apiError = err);

            if (apiError != null || manifest == null)
            {
                onError?.Invoke($"manifest 가져오기 실패: {apiError ?? "null response"}");
                yield break;
            }

            long total = manifest.totalSizeBytes;
            long downloaded = 0;
            int errors = 0;

            // models + audio + textures + scripts 다 다운로드
            foreach (var asset in manifest.manifest.models)
            {
                yield return DownloadOne(asset.url, asset.size_bytes,
                    bytes => { downloaded += bytes; onProgress?.Invoke((float)downloaded / total); },
                    err => errors++);
            }
            foreach (var aud in manifest.manifest.audio)
            {
                yield return DownloadOne(aud.url, aud.size_bytes,
                    bytes => { downloaded += bytes; onProgress?.Invoke((float)downloaded / total); },
                    err => errors++);
            }
            foreach (var tex in manifest.manifest.textures)
            {
                yield return DownloadOne(tex.url, tex.size_bytes,
                    bytes => { downloaded += bytes; onProgress?.Invoke((float)downloaded / total); },
                    err => errors++);
            }

            string sceneRoot = Path.Combine(_cacheDir, mission, device, $"scene{sceneNumber}-v{manifest.version}");
            if (errors > 0)
            {
                onError?.Invoke($"{errors}개 에셋 다운로드 실패 (만약 R2 에 실 에셋이 없으면 정상). manifest 는 수신됨.");
            }
            onComplete?.Invoke(manifest, sceneRoot);
        }

        private IEnumerator DownloadOne(string url, long expectedBytes,
                                          Action<long> onOk, Action<string> onErr)
        {
            // 실 stub 에서는 CDN 호출만 시도 — 응답 없으면 onErr.
            using UnityWebRequest req = UnityWebRequest.Get(url);
            req.timeout = 30;
            yield return req.SendWebRequest();

            if (req.result == UnityWebRequest.Result.Success)
            {
                string localPath = LocalPathFor(url);
                Directory.CreateDirectory(Path.GetDirectoryName(localPath));
                File.WriteAllBytes(localPath, req.downloadHandler.data);
                onOk?.Invoke(req.downloadHandler.data.LongLength);
            }
            else
            {
                onErr?.Invoke($"{url}: {req.error}");
            }
        }

        private string LocalPathFor(string url)
        {
            // https://cdn.r2.dev/lemuel-xr/joseph/quest3/v1.0.0/models/x.glb
            //   → {cacheDir}/joseph/quest3/v1.0.0/models/x.glb
            try
            {
                Uri u = new(url);
                return Path.Combine(_cacheDir, u.AbsolutePath.TrimStart('/').Replace("lemuel-xr/", ""));
            }
            catch
            {
                return Path.Combine(_cacheDir, Path.GetRandomFileName());
            }
        }
    }
}
