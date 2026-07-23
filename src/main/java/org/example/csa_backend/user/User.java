package org.example.csa_backend.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "provider_id"}))
@Getter
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String email;

    @Column
    private String password;

    @Column
    private String provider;

    @Column(name = "provider_id")
    private String providerId;

    @Column
    private String name;

    @Column
    private String locale;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false, length = 20)
    private String role = "USER";

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public User(String email, String provider, String providerId, String name, String locale) {
        this.email = email;
        this.provider = provider;
        this.providerId = providerId;
        this.name = name;
        this.locale = locale;
    }

    public void changeRole(String role) {
        this.role = role;
    }

    public void suspend() {
        this.status = "SUSPENDED";
    }

    public void activate() {
        this.status = "ACTIVE";
    }
}
