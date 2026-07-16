package github.lms.lemuel.xr.common.web

import github.lms.lemuel.xr.common.AppException
import github.lms.lemuel.xr.common.ErrorCode
import java.util.UUID
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/** JwtAuthFilter 가 request attribute 에 박은 userId/deviceType 을 controller 에서 꺼낼 helper. */
object RequestContext {

    /** 현재 인증된 userId. 미인증이면 E_AUTH_REQUIRED. */
    @JvmStatic
    fun currentUserId(): UUID {
        val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            ?: throw AppException(ErrorCode.E_AUTH_REQUIRED)
        val v = attrs.request.getAttribute("xr.userId")
            ?: throw AppException(ErrorCode.E_AUTH_REQUIRED)
        return v as UUID
    }

    @JvmStatic
    fun currentDeviceType(): String? {
        val attrs = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
            ?: return null
        val v = attrs.request.getAttribute("xr.deviceType")
        return v?.toString()
    }
}
