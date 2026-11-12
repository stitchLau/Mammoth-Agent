package com.mammoth.domain.service;

import com.mammoth.domain.persistence.Organization;
import com.mammoth.domain.persistence.OrganizationRepository;
import com.mammoth.domain.support.Ids;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single default organization used by the M1 single-machine deployment. Multi-org support
 * is modelled in the schema (org_id columns) but provisioned later (ADR-0006 migration path).
 *
 * <p>{@code @DependsOn("flyway")} guarantees the schema exists before the startup bootstrap reads
 * the organization table.
 */
@Service
@DependsOn("flyway")
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final String defaultOrgName;

  public OrganizationService(
      OrganizationRepository organizationRepository,
      @Value("${mammoth.default-org-name:Mammoth Demo}") String defaultOrgName) {
    this.organizationRepository = organizationRepository;
    this.defaultOrgName = defaultOrgName;
  }

  @PostConstruct
  @Transactional
  public void ensureDefaultOrganization() {
    if (organizationRepository.count() == 0) {
      organizationRepository.save(new Organization("org-default", defaultOrgName, Ids.nowIso()));
    }
  }

  @Transactional(readOnly = true)
  public Organization current() {
    return organizationRepository.findAll().stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("default organization is missing"));
  }

  public String currentOrgId() {
    return current().getId();
  }
}
