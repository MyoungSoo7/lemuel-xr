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
}
