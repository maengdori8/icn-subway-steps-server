package org.skilltransfer.icnsubway.repository;

import java.util.Optional;
import org.skilltransfer.icnsubway.domain.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);
    boolean existsByUsername(String username);
}
