package org.example.csa_backend.admin;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.admin.dto.AdminUserDetailDto;
import org.example.csa_backend.admin.dto.AdminUserDto;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.common.response.PageResponse;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PageResponse<AdminUserDto> getUsers(String q, Pageable pageable) {
        Page<User> users = StringUtils.hasText(q)
                ? userRepository.findByEmailContainingIgnoreCaseOrNameContainingIgnoreCase(q, q, pageable)
                : userRepository.findAll(pageable);
        return PageResponse.from(users.map(AdminUserDto::from));
    }

    @Transactional(readOnly = true)
    public AdminUserDetailDto getUser(Long id) {
        User user = findUser(id);
        return AdminUserDetailDto.from(user);
    }

    @Transactional
    public AdminUserDto updateStatus(Long id, String status) {
        if (!("ACTIVE".equals(status) || "SUSPENDED".equals(status))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 사용자 상태입니다.");
        }
        User user = findUser(id);
        if ("SUSPENDED".equals(status)) {
            user.suspend();
        } else {
            user.activate();
        }
        return AdminUserDto.from(user);
    }

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }
}
