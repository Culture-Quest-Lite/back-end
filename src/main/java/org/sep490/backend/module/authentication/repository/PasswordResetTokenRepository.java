package org.sep490.backend.module.authentication.repository;

import org.sep490.backend.module.authentication.entity.PasswordResetToken;
import org.sep490.backend.module.authentication.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    void deleteByUser(User user);
}
