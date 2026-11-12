package com.mammoth.domain.knowledge;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, String> {

  List<KnowledgeDocument> findByScopeTypeAndScopeIdOrderByCreatedAtAsc(String scopeType, String scopeId);

  List<KnowledgeDocument> findByScopeTypeAndScopeIdAndStatusOrderByCreatedAtAsc(
      String scopeType, String scopeId, String status);

  Optional<KnowledgeDocument> findByIdAndScopeTypeAndScopeId(
      String id, String scopeType, String scopeId);
}
