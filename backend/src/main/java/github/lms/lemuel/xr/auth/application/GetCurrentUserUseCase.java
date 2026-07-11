package github.lms.lemuel.xr.auth.application;

import github.lms.lemuel.xr.auth.adapter.out.persistence.UserJpaEntity;
import github.lms.lemuel.xr.auth.adapter.out.persistence.UserRepository;
import github.lms.lemuel.xr.common.AppException;
import github.lms.lemuel.xr.common.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetCurrentUserUseCase {

    private final UserRepository users;

    @Transactional(readOnly = true)
    public UserJpaEntity execute(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.E_AUTH_REQUIRED, "User not found"));
    }
}
