package com.mammoth.domain.persistence;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleAssignmentRepository extends JpaRepository<RoleAssignment, String> {

  Optional<RoleAssignment> findByUserIdAndRoleAndScopeTypeAndScopeId(
      String userId, String role, String scopeType, String scopeId);

  boolean existsByUserIdAndRoleAndScopeTypeAndScopeId(
      String userId, String role, String scopeType, String scopeId);

  List<RoleAssignment> findByRoleAndScopeTypeAndScopeId(
      String role, String scopeType, String scopeId);
}
