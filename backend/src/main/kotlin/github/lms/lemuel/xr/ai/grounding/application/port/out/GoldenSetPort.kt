package github.lms.lemuel.xr.ai.grounding.application.port.out

import github.lms.lemuel.xr.ai.grounding.eval.GoldenSet

/**
 * 골든셋 조회 out-port. 저장 위치(클래스패스·파일시스템·원격)는 use-case 의 관심사가 아니다.
 */
interface GoldenSetPort {
    fun load(version: String): GoldenSet.Loaded
}
