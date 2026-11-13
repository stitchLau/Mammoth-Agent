package com.mammoth.domain.service;

import com.mammoth.domain.persistence.Organization;
import com.mammoth.domain.persistence.OrganizationRepository;
import com.mammoth.domain.support.Ids;
import jakarta.annotation.PostConstruct;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the single default organization used by the M1 single-machine deployment. Multi-org support
 * is modelled in the schema (org_id columns) but provisioned later (ADR-0006 migration path).
 *
 * <p>The constructor resolves the optional {@link Flyway} bean so writer startup runs migrations
 * before {@link #ensureDefaultOrganization()} reads the organization table. Read-only entrypoints
 * (ADR-0008) have no Flyway bean: their schema is validated by {@code DataSourceConfig} instead.
 */
@Service
public class OrganizationService {

  private final OrganizationRepository organizationRepository;
  private final String defaultOrgName;
  private final boolean readOnly;

  public OrganizationService(
      OrganizationRepository organizationRepository,
      @Value("${mammoth.default-org-name:Mammoth Demo}") String defaultOrgName,
      ObjectProvider<Flyway> flywayProvider,
      @Value("${mammoth.data-root.read-only:false}") boolean readOnly) {
    this.organizationRepository = organizationRepository;
    this.defaultOrgName = defaultOrgName;
    this.readOnly = readOnly;
    // Forces migration before the @PostConstruct bootstrap in writer mode; no-op in read-only mode.
    flywayProvider.getIfAvailable();
  }

  @PostConstruct
  @Transactional
  public void ensureDefaultOrganization() {
    if (readOnly) {
      if (organizationRepository.count() == 0) {
        throw new IllegalStateException(
            "Mammoth data root is not initialized (missing default organization). Start the"
                + " web/management entrypoint once before using a read-only entrypoint.");
      }
      return;
    }
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
