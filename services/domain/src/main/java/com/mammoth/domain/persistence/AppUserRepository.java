package com.mammoth.domain.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, String> {

  Optional<AppUser> findByUsername(String username);

  List<AppUser> findByOrgIdOrderByCreatedAtAsc(String orgId);
}
