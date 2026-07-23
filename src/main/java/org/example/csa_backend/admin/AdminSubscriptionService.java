package org.example.csa_backend.admin;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.admin.dto.AdminSubscriptionDto;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.common.response.PageResponse;
import org.example.csa_backend.subscription.Platform;
import org.example.csa_backend.subscription.Subscription;
import org.example.csa_backend.subscription.SubscriptionRepository;
import org.example.csa_backend.subscription.SubscriptionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminSubscriptionService {

    private final SubscriptionRepository subscriptionRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminSubscriptionDto> getSubscriptions(String q, String platform, String status, Pageable pageable) {
        Platform platformFilter = parseEnum(Platform.class, platform, "platform");
        SubscriptionStatus statusFilter = parseEnum(SubscriptionStatus.class, status, "status");

        Page<Subscription> subscriptions = subscriptionRepository.searchForAdmin(
                StringUtils.hasText(q) ? q : null,
                platformFilter,
                statusFilter,
                pageable);

        return PageResponse.from(subscriptions.map(AdminSubscriptionDto::from));
    }

    @Transactional(readOnly = true)
    public AdminSubscriptionDto getSubscription(Long id) {
        Subscription subscription = subscriptionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "구독 정보를 찾을 수 없습니다."));
        return AdminSubscriptionDto.from(subscription);
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumType, String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 " + fieldName + " 값입니다.");
        }
    }
}
