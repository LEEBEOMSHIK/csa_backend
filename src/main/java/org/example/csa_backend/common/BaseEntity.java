package org.example.csa_backend.common;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
public abstract class BaseEntity {

    @CreatedDate
    @Column(name = "CRE_DT", nullable = false, updatable = false)
    private LocalDateTime creDt;

    @CreatedBy
    @Column(name = "CRE_ID", nullable = false, updatable = false, length = 50)
    private String creId;

    @LastModifiedDate
    @Column(name = "MOD_DT")
    private LocalDateTime modDt;

    @LastModifiedBy
    @Column(name = "MOD_ID", length = 50)
    private String modId;

    @Column(name = "DEL_YN", nullable = false, length = 1)
    private String delYn = "N";
}
