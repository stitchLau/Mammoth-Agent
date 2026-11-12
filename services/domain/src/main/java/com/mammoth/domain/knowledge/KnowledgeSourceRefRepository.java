package com.mammoth.domain.knowledge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeSourceRefRepository extends JpaRepository<KnowledgeSourceRef, String> {

  List<KnowledgeSourceRef> findByKnowledgeDocumentIdOrderByIdAsc(String knowledgeDocumentId);

  long countByKnowledgeDocumentId(String knowledgeDocumentId);
}
