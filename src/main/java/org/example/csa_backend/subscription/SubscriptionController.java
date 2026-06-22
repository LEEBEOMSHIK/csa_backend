package org.example.csa_backend.subscription;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;
    private final UserRepository userRepository;

    @PostMapping("/verify")
    public ResponseEntity<SubscriptionDto> verify(@RequestBody VerifyReceiptRequest request,
                                                  Authentication authentication) {
        User user = currentUser(authentication);
        SubscriptionDto dto = subscriptionService.verifyAndApply(
                user, request.platform(), request.purchaseToken(), request.productId());
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/me")
    public ResponseEntity<SubscriptionDto> getMySubscription(Authentication authentication) {
        User user = currentUser(authentication);
        return subscriptionService.getMySubscription(user)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private User currentUser(Authentication authentication) {
        Long userId = Long.parseLong(authentication.getName());
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
