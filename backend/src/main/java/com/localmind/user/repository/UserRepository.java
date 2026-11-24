package com.localmind.user.repository;

import com.localmind.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link User} (table {@code users}). Emails are stored lowercased, so callers normalize
 * them before looking one up.
 */
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}
