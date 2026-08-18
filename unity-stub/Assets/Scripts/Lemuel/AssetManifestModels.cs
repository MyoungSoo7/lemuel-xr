// AssetManifestModels.cs — /api/config/asset-manifest 응답 DTO.
// Unity JsonUtility 는 [Serializable] 필드 직렬화만 지원 — Map<string,object> 같은 임의 키는
// 후처리로 string 으로 받아 JsonNode 같이 풀어쓴다.

using System;
using System.Collections.Generic;
using UnityEngine;

namespace Lemuel
{
    [Serializable]
    public class AssetManifest
    {
        public string id;
        public string missionId;
        public int sceneNumber;
        public string deviceType;
        public string xrMode;       // "vr" | "ar" — 요청한 모드가 그대로 돌아온다
        public string version;
        public string cdnBaseUrl;
        public long totalSizeBytes;

        // manifest 본체 — Unity JsonUtility 는 dict 미지원이라
        // 문자열로 받아 SimpleJSON / Newtonsoft 로 파싱하거나
        // 아래 ManifestBody 처럼 알려진 구조로 미리 모델링.
        public ManifestBody manifest;
    }

    [Serializable]
    public class ManifestBody
    {
        public List<ManifestAsset> models = new();
        public List<ManifestAudio> audio = new();
        public List<ManifestAsset> textures = new();
        public List<ManifestAsset> scripts = new();
    }

    [Serializable]
    public class ManifestAsset
    {
        public string id;
        public string url;
        public long size_bytes;
        public string lod;          // models 만 사용
    }

    [Serializable]
    public class ManifestAudio
    {
        public string id;
        public string url;
        public long size_bytes;
        public string voice_id;
        public int duration_ms;
    }
}
