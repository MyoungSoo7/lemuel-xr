package github.lms.lemuel.xr.asset.application.seed

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * manifest json 파일의 타입 세이프 표현. Jackson 이 필드로 바인딩하므로
 * `((Number) doc.get(...))` 류의 언체크 캐스트가 사라진다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class ManifestDocument(
    @param:JsonProperty("mission_id") val missionId: String?,
    @param:JsonProperty("scene_number") val sceneNumber: Short?,
    @param:JsonProperty("device_type") val deviceType: String?,
    @param:JsonProperty("version") val version: String?,
    @param:JsonProperty("capabilities_min") val capabilitiesMin: Map<String, Any?>?,
    @param:JsonProperty("manifest") val manifest: Map<String, Any?>?,
    @param:JsonProperty("audio_locale") val audioLocale: String?,
    @param:JsonProperty("total_size_bytes") val totalSizeBytes: Long?,
    @param:JsonProperty("cdn_base_url") val cdnBaseUrl: String?,
)
