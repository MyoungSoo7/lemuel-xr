package github.lms.lemuel.xr.ai.grounding.application

/**
 * 한국어 인식 문장 분리 — 결정론적. 문장 종결부호(. ! ? 。) 뒤 공백 또는 개행에서 자른다.
 * 휴리스틱이므로 병리적 문장부호는 오분할될 수 있다(섀도우 프로토타입 허용 범위).
 */
object SentenceSplitter {
    private val BOUNDARY = Regex("(?<=[.!?。])\\s+|\\n+")

    fun split(text: String): List<String> =
        text.split(BOUNDARY)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
