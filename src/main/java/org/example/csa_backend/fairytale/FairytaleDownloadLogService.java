package org.example.csa_backend.fairytale;

import lombok.RequiredArgsConstructor;
import org.example.csa_backend.common.exception.BusinessException;
import org.example.csa_backend.common.exception.ErrorCode;
import org.example.csa_backend.fairytale.dto.CreateDownloadLogRequest;
import org.example.csa_backend.user.User;
import org.example.csa_backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FairytaleDownloadLogService {

    private final FairytaleDownloadLogRepository downloadLogRepository;
    private final FairytaleRepository fairytaleRepository;
    private final AiFairytaleRepository aiFairytaleRepository;
    private final UserRepository userRepository;

    @Transactional
    public void logDownload(Long userId, Long targetId, CreateDownloadLogRequest request) {
        String format = normalizeFormat(request.format());
        String targetType = normalizeTargetType(request.targetType());

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "사용자를 찾을 수 없습니다."));

        FairytaleDownloadLog log = switch (targetType) {
            case FairytaleDownloadLog.TARGET_TYPE_FAIRYTALE -> {
                Fairytale fairytale = fairytaleRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "동화를 찾을 수 없습니다."));
                yield FairytaleDownloadLog.forFairytale(user, fairytale, format);
            }
            case FairytaleDownloadLog.TARGET_TYPE_AI_FAIRYTALE -> {
                AiFairytale aiFairytale = aiFairytaleRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "동화를 찾을 수 없습니다."));
                yield FairytaleDownloadLog.forAiFairytale(user, aiFairytale, format);
            }
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT, "지원하지 않는 targetType입니다.");
        };

        downloadLogRepository.save(log);
    }

    private String normalizeFormat(String format) {
        if (format == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "format은 필수입니다.");
        }
        String normalized = format.trim().toLowerCase();
        if (!FairytaleDownloadLog.FORMAT_SLIDE.equals(normalized)
                && !FairytaleDownloadLog.FORMAT_VIDEO.equals(normalized)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "format은 slide 또는 video여야 합니다.");
        }
        return normalized;
    }

    private String normalizeTargetType(String targetType) {
        if (targetType == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "targetType은 필수입니다.");
        }
        return targetType.trim().toUpperCase();
    }
}
