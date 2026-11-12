package com.mammoth.domain.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.Key> {

  List<ProjectMember> findByProjectIdOrderByCreatedAtAsc(String projectId);

  List<ProjectMember> findByUserId(String userId);

  Optional<ProjectMember> findByProjectIdAndUserId(String projectId, String userId);
}
