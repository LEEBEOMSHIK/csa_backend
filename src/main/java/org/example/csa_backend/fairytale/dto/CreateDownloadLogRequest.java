package org.example.csa_backend.fairytale.dto;

/**
 * 다운로드(오프라인 저장) 완료 시 클라이언트가 보고하는 요청 본문.
 * targetType으로 경로의 {id}가 큐레이션 Fairytale인지 사용자 생성 AiFairytale인지
 * 구분한다(두 엔티티는 id 시퀀스가 서로 달라 값이 겹칠 수 있으므로 path의 id만으로는
 * 대상 테이블을 판별할 수 없다).
 */
public record CreateDownloadLogRequest(
        String targetType,
        String format
) {
}
