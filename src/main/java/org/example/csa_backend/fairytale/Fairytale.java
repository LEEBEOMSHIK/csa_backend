package org.example.csa_backend.fairytale;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.csa_backend.common.BaseEntity;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "FAIRYTALES")
@Getter
@NoArgsConstructor
public class Fairytale extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TITLE", nullable = false, length = 200)
    private String title;

    @Column(name = "TITLE_JA", length = 200)
    private String titleJa;

    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "DESCRIPTION_JA", length = 500)
    private String descriptionJa;

    @Column(name = "RATING")
    private Double rating;

    @Column(name = "COLOR_HEX", length = 7)
    private String colorHex;

    @Column(name = "THEME_TAG", length = 50)
    private String themeTag;

    @Column(name = "IS_THEME", nullable = false, length = 1)
    private String isTheme = "N";

    @Column(name = "IS_NEW", nullable = false, length = 1)
    private String isNew = "N";

    @Column(name = "IS_RECOMMENDED", nullable = false, length = 1)
    private String isRecommended = "N";

    @ManyToMany
    @JoinTable(
        name = "FAIRYTALE_CATEGORIES",
        joinColumns = @JoinColumn(name = "FAIRYTALE_ID"),
        inverseJoinColumns = @JoinColumn(name = "CATEGORY_ID")
    )
    private List<Category> categories = new ArrayList<>();

    public Fairytale(String title, String titleJa, String description, String descriptionJa,
                     Double rating, String colorHex, String themeTag,
                     String isTheme, String isNew, String isRecommended) {
        this.title = title;
        this.titleJa = titleJa;
        this.description = description;
        this.descriptionJa = descriptionJa;
        this.rating = rating;
        this.colorHex = colorHex;
        this.themeTag = themeTag;
        this.isTheme = isTheme;
        this.isNew = isNew;
        this.isRecommended = isRecommended;
    }

    public void addCategory(Category category) {
        this.categories.add(category);
    }
}
