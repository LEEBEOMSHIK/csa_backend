package org.example.csa_backend.setting;

import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.setting.dto.UpdateSettingsRequest;
import org.example.csa_backend.setting.dto.UserSettingsDto;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserSettingsServiceTest {

    private final UserSettingsRepository userSettingsRepository = mock(UserSettingsRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserSettingsService service = new UserSettingsService(userSettingsRepository, userRepository);

    @Test
    void getSettingsCreatesDefaultWhenMissing() {
        User user = user(5L);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.empty());
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        UserSettingsDto dto = service.getSettings(5L);

        assertThat(dto.locale()).isEqualTo("ko");
        assertThat(dto.textNotiEnabled()).isTrue();
        assertThat(dto.pushNotiEnabled()).isTrue();
        verify(userSettingsRepository).save(any(UserSettings.class));
    }

    @Test
    void getSettingsReturnsExistingWithoutCreating() {
        User user = user(5L);
        UserSettings settings = new UserSettings(user);
        settings.update("ja", false, true);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        UserSettingsDto dto = service.getSettings(5L);

        assertThat(dto.locale()).isEqualTo("ja");
        assertThat(dto.textNotiEnabled()).isFalse();
        assertThat(dto.pushNotiEnabled()).isTrue();
        verify(userSettingsRepository, never()).save(any(UserSettings.class));
    }

    @Test
    void updateSettingsAppliesNewValues() {
        User user = user(5L);
        UserSettings settings = new UserSettings(user);
        when(userRepository.findById(5L)).thenReturn(Optional.of(user));
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(settings));

        UserSettingsDto dto = service.updateSettings(5L,
                new UpdateSettingsRequest("ja", false, true));

        assertThat(dto.locale()).isEqualTo("ja");
        assertThat(dto.textNotiEnabled()).isFalse();
        assertThat(dto.pushNotiEnabled()).isTrue();
        assertThat(settings.getLocale()).isEqualTo("ja");
        assertThat(settings.isTextNotiEnabled()).isFalse();
        assertThat(settings.isPushNotiEnabled()).isTrue();
    }

    @Test
    void updateSettingsRejectsUnsupportedLocale() {
        assertThatThrownBy(() -> service.updateSettings(5L,
                new UpdateSettingsRequest("en", true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void updateSettingsRejectsNullLocale() {
        assertThatThrownBy(() -> service.updateSettings(5L,
                new UpdateSettingsRequest(null, true, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void updateSettingsRejectsNullTextNoti() {
        assertThatThrownBy(() -> service.updateSettings(5L,
                new UpdateSettingsRequest("ko", null, true)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void updateSettingsRejectsNullPushNoti() {
        assertThatThrownBy(() -> service.updateSettings(5L,
                new UpdateSettingsRequest("ko", true, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT);
    }

    @Test
    void getSettingsRejectsUnknownUser() {
        when(userRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getSettings(404L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void createDefaultSettingsSavesWhenAbsent() {
        User user = user(5L);
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.empty());

        service.createDefaultSettings(user);

        verify(userSettingsRepository, times(1)).save(any(UserSettings.class));
    }

    @Test
    void createDefaultSettingsIsIdempotentWhenPresent() {
        User user = user(5L);
        when(userSettingsRepository.findByUser(user)).thenReturn(Optional.of(new UserSettings(user)));

        service.createDefaultSettings(user);

        verify(userSettingsRepository, never()).save(any(UserSettings.class));
    }

    private User user(Long id) {
        User user = new User("user@example.com", "password");
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
