package org.example.csa_backend.setting;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.dto.UpdateSettingsRequest;
import org.example.csa_backend.setting.dto.UserSettingsDto;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;
    private final UserRepository userRepository;

    @Transactional
    public UserSettingsDto getSettings(Long userId) {
        return UserSettingsDto.from(getOrCreateSettings(userId));
    }

    @Transactional
    public UserSettingsDto updateSettings(Long userId, UpdateSettingsRequest request) {
        validate(request);
        UserSettings settings = getOrCreateSettings(userId);
        settings.update(request.locale(), request.textNotiEnabled(), request.pushNotiEnabled());
        return UserSettingsDto.from(settings);
    }

    private void validate(UpdateSettingsRequest request) {
        if (request.locale() == null || !("ko".equals(request.locale()) || "ja".equals(request.locale()))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 언어입니다.");
        }
        if (request.textNotiEnabled() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "문자 알림 설정은 필수입니다.");
        }
        if (request.pushNotiEnabled() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "푸시 알림 설정은 필수입니다.");
        }
    }

    @Transactional
    public void createDefaultSettings(User user) {
        if (userSettingsRepository.findByUser(user).isEmpty()) {
            userSettingsRepository.save(new UserSettings(user));
        }
    }

    private UserSettings getOrCreateSettings(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
        return userSettingsRepository.findByUser(user)
                .orElseGet(() -> userSettingsRepository.save(new UserSettings(user)));
    }
}
