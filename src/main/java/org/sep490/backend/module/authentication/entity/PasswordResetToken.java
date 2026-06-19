package org.sep490.backend.module.authentication.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "password_reset_tokens")
@Data
@NoArgsConstructor
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = false, name = "user_id")
    private User user;

    @Column(nullable = false)
    private LocalDateTime expiryDate;

    public PasswordResetToken(String token, User user) {
        this.token = token;
        this.user = user;
        this.expiryDate = LocalDateTime.now().plusMinutes(15);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
