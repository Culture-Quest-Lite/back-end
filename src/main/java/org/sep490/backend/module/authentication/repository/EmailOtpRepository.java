package org.sep490.backend.module.authentication.repository;

import org.sep490.backend.module.authentication.entity.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {
    Optional<EmailOtp> findFirstByEmailOrderByExpiryDateDesc(String email);
    void deleteByEmail(String email);
}
