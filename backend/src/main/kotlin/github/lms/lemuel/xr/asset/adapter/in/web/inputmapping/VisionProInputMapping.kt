package github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping

import org.springframework.stereotype.Component

@Component
class VisionProInputMapping : InputMappingProvider {

    override fun deviceId(): String = "visionpro"

    override fun mapping(): Map<String, Any> =
        mapOf(
            "GRAB" to mapOf("source" to "hand", "binding" to "pinch"),
            "POINT_AT" to mapOf("source" to "eye+hand", "binding" to "look_and_pinch"),
            "GAZE_DURATION" to mapOf("source" to "eye", "binding" to "eye_dwell"),
        )

    /** Vision Pro 는 컨트롤러가 없다 — 시선으로 겨냥하고 핀치로 확정한다. */
    override fun arOverlay(): Map<String, Any> =
        mapOf(
            "PLACE_ON_SURFACE" to mapOf(
                "source" to "eye",
                "binding" to "gaze_plane_hittest",
                "confirm" to mapOf("source" to "hand", "binding" to "pinch"),
            ),
            "RECENTER_ANCHOR" to mapOf("source" to "hand", "binding" to "double_pinch_hold"),
            "LOCOMOTION" to mapOf("source" to "room_scale", "binding" to "physical_walk"),
        )
}
