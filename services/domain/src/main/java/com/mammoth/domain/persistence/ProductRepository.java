package com.mammoth.domain.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, String> {

  List<Product> findByOrgIdOrderByCreatedAtAsc(String orgId);

  boolean existsByOrgIdAndName(String orgId, String name);
}
