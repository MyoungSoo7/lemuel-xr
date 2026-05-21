package github.lms.lemuel.xr.common.web;

import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.util.UUID;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** JwtAuthFilter 가 request attribute 에 박은 userId/deviceType 을 controller 에서 꺼낼 helper. */
public final class RequestContext {

    private RequestContext() {}

    /** 현재 인증된 userId. 미인증이면 E_AUTH_REQUIRED. */
    public static UUID currentUserId() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) throw new AppException(ErrorCode.E_AUTH_REQUIRED);
        Object v = attrs.getRequest().getAttribute("xr.userId");
        if (v == null) throw new AppException(ErrorCode.E_AUTH_REQUIRED);
        return (UUID) v;
    }

    public static String currentDeviceType() {
        var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        Object v = attrs.getRequest().getAttribute("xr.deviceType");
        return v == null ? null : v.toString();
    }
}
