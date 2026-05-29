package org.example.csa_backend.character;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;
import org.example.csa_backend.user.User;

@Entity
@Table(name = "CHARACTERS")
@Getter
@NoArgsConstructor
public class Character extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "NAME", nullable = false, length = 50)
    private String name;

    // 파츠 변형 인덱스 목록을 CSV로 저장 (예: "0,0,0,0,0,1,1,1,1")
    @Column(name = "VARIANTS", nullable = false, length = 100)
    private String variants;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "USER_ID", nullable = false)
    private User owner;

    public Character(User owner, String name, String variants) {
        this.owner = owner;
        this.name = name;
        this.variants = variants;
    }

    public void update(String name, String variants) {
        this.name = name;
        this.variants = variants;
    }

    public boolean isOwnedBy(Long userId) {
        return owner != null && owner.getId().equals(userId);
    }
}
