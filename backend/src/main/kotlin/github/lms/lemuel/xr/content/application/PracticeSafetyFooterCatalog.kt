package github.lms.lemuel.xr.content.application

import org.springframework.stereotype.Component

/**
 * Theme 6/7 실천·성찰 응답에 붙는 R2 피해 상황 보호 footer 카탈로그.
 *
 * 기존 PracticeReflectionController.safetyFooter(topicId) 의 하드코딩 문구를 이관.
 * Theme 6(마음 지킴, 잠 4:23) / Theme 7(사람 두려움, 잠 29:25) 별 문구 (§3.4 / §4.4).
 * 안전선(R2) 문구이므로 완화·제거 금지.
 *
 * 번호 표기 (2026-08-02 교정, 정본 = V20260802031449 시드):
 * - 자살예방 상담은 2024-01-01 자로 **109** 로 통합됐다 (보건복지부). 구 번호 1393 은 정번호가 아니다.
 * - 1577-0199(정신건강상담)는 폐지되지 않았고 종전대로 담당 분야 상담을 수행하므로 그대로 둔다.
 */
@Component
class PracticeSafetyFooterCatalog {

    /** R2 — 피해 상황 사용자 보호 footer. Theme 별 문구 (§3.4 / §4.4). */
    fun forTopic(topicId: Short): String {
        if (topicId.toInt() == 6) {
            return THEME_6
        }
        // topicId == 7
        return THEME_7
    }

    companion object {
        private const val THEME_6 =
            "마음 지킴은 가해자를 보호하는 도구가 아닙니다. 안전이 우선입니다." +
                " 피해 상황에 있는 분도 이 묵상에 안전하게 머물 수 있게 자원을 함께 안내합니다." +
                " (109 자살예방상담 · 1577-0199 정신건강위기상담 · 24시간)"

        private const val THEME_7 =
            "두려움은 실재 위협의 신호일 수 있습니다. 안전 확보가 우선입니다." +
                " 피해 상황에 있는 분도 이 묵상에 안전하게 머물 수 있게 자원을 함께 안내합니다." +
                " (109 자살예방상담 · 1577-0199 정신건강위기상담 · 24시간)"
    }
}
