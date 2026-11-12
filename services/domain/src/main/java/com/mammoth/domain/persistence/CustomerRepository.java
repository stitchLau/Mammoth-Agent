package com.mammoth.domain.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, String> {

  List<Customer> findByOrgIdOrderByCreatedAtAsc(String orgId);

  boolean existsByOrgIdAndName(String orgId, String name);
}
