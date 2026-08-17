package github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping

import org.springframework.stereotype.Component

@Component
class GalaxyXrInputMapping : InputMappingProvider {

    override fun deviceId(): String = "galaxyxr"

    override fun mapping(): Map<String, Any> =
        mapOf(
            "GRAB" to mapOf("source" to "hand", "binding" to "pinch"),
            "POINT_AT" to mapOf("source" to "eye+hand", "binding" to "look_and_pinch"),
            "GAZE_DURATION" to mapOf("source" to "eye", "binding" to "eye_dwell"),
        )

    /** Galaxy XR 은 패스스루 + 핸드트래킹 기준. 컨트롤러는 선택 사양이라 손을 1순위로 둔다. */
    override fun arOverlay(): Map<String, Any> =
        mapOf(
            "PLACE_ON_SURFACE" to mapOf(
                "source" to "hand",
                "binding" to "pinch_plane_hittest",
                "fallback" to mapOf("source" to "eye", "binding" to "gaze_plane_hittest"),
            ),
            "RECENTER_ANCHOR" to mapOf("source" to "hand", "binding" to "palm_up_hold"),
            "LOCOMOTION" to mapOf("source" to "room_scale", "binding" to "physical_walk"),
        )
}
