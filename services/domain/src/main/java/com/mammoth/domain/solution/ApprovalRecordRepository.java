package com.mammoth.domain.solution;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, String> {

  List<ApprovalRecord> findByObjectTypeAndObjectIdOrderByAtAsc(String objectType, String objectId);
}
