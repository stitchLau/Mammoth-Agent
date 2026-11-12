package com.mammoth.domain.knowledge;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface KnowledgeChunkRepository extends JpaRepository<KnowledgeChunk, String> {

  List<KnowledgeChunk> findByKnowledgeDocumentIdOrderByOrdinalAsc(String knowledgeDocumentId);

  long countByKnowledgeDocumentId(String knowledgeDocumentId);

  @Modifying
  @Transactional
  @Query("delete from KnowledgeChunk chunk where chunk.knowledgeDocumentId = :documentId")
  void deleteByDocumentId(@Param("documentId") String documentId);
}
