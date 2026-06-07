package org.sep490.backend.module.authentication.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
@Table(name = "email_otps")
public class EmailOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String otpCode;
    private LocalDateTime createdAt;
    private LocalDateTime expiryDate;

    public EmailOtp(String email, String otpCode, int expiryMinutes) {
        this.email = email;
        this.otpCode = otpCode;
        this.createdAt = LocalDateTime.now();
        this.expiryDate = LocalDateTime.now().plusMinutes(expiryMinutes);
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(this.expiryDate);
    }
}
