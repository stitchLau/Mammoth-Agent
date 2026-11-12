package com.mammoth.domain.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, String> {

  List<Project> findByOrgIdOrderByCreatedAtAsc(String orgId);

  boolean existsByCode(String code);

  Optional<Project> findByCode(String code);
}
