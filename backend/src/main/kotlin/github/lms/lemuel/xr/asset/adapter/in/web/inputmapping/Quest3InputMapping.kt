package github.lms.lemuel.xr.asset.adapter.`in`.web.inputmapping

import org.springframework.stereotype.Component

@Component
class Quest3InputMapping : InputMappingProvider {

    override fun deviceId(): String = "quest3"

    override fun mapping(): Map<String, Any> =
        mapOf(
            "GRAB" to mapOf(
                "source" to "controller",
                "binding" to "grip",
                "fallback" to mapOf("source" to "hand", "binding" to "pinch"),
            ),
            "POINT_AT" to mapOf("source" to "controller", "binding" to "raycast"),
            "GAZE_DURATION" to mapOf("source" to "head", "binding" to "head_direction_dwell"),
        )
}
