package org.example.csa_backend.fairytale;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "CATEGORIES")
@Getter
@NoArgsConstructor
public class Category extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "CATEGORY_KEY", nullable = false, unique = true, length = 50)
    private String categoryKey;

    @Column(name = "NAME_KO", nullable = false, length = 100)
    private String nameKo;

    @Column(name = "NAME_JA", nullable = false, length = 100)
    private String nameJa;

    @ManyToMany(mappedBy = "categories")
    private List<Fairytale> fairytales = new ArrayList<>();

    public Category(String categoryKey, String nameKo, String nameJa) {
        this.categoryKey = categoryKey;
        this.nameKo = nameKo;
        this.nameJa = nameJa;
    }
}
