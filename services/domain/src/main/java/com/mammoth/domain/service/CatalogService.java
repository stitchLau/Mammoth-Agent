package com.mammoth.domain.service;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.CustomerRepository;
import com.mammoth.domain.persistence.Product;
import com.mammoth.domain.persistence.ProductRepository;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.ApiException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customer and product master data for the M1 slice. */
@Service
public class CatalogService {

  private final CustomerRepository customerRepository;
  private final ProductRepository productRepository;
  private final OrganizationService organizationService;
  private final AuditService auditService;

  public CatalogService(
      CustomerRepository customerRepository,
      ProductRepository productRepository,
      OrganizationService organizationService,
      AuditService auditService) {
    this.customerRepository = customerRepository;
    this.productRepository = productRepository;
    this.organizationService = organizationService;
    this.auditService = auditService;
  }

  @Transactional
  public Customer createCustomer(AppUser actor, String name) {
    String normalized = requireName(name);
    String orgId = organizationService.currentOrgId();
    if (customerRepository.existsByOrgIdAndName(orgId, normalized)) {
      throw ApiException.conflict("customer already exists: " + normalized);
    }
    Customer customer = new Customer(Ids.newId(), orgId, normalized, "active", Ids.nowIso());
    customerRepository.save(customer);
    auditService.record(actor.getId(), "customer.create", "customer", customer.getId(), null);
    return customer;
  }

  @Transactional
  public Product createProduct(AppUser actor, String name) {
    String normalized = requireName(name);
    String orgId = organizationService.currentOrgId();
    if (productRepository.existsByOrgIdAndName(orgId, normalized)) {
      throw ApiException.conflict("product already exists: " + normalized);
    }
    Product product = new Product(Ids.newId(), orgId, normalized, "active", Ids.nowIso());
    productRepository.save(product);
    auditService.record(actor.getId(), "product.create", "product", product.getId(), null);
    return product;
  }

  @Transactional(readOnly = true)
  public List<Customer> listCustomers() {
    return customerRepository.findByOrgIdOrderByCreatedAtAsc(organizationService.currentOrgId());
  }

  @Transactional(readOnly = true)
  public List<Product> listProducts() {
    return productRepository.findByOrgIdOrderByCreatedAtAsc(organizationService.currentOrgId());
  }

  @Transactional(readOnly = true)
  public Customer requireCustomer(String customerId) {
    return customerRepository.findById(customerId)
        .orElseThrow(() -> ApiException.notFound("customer not found: " + customerId));
  }

  private static String requireName(String name) {
    String normalized = name == null ? "" : name.trim();
    if (normalized.isEmpty()) {
      throw ApiException.badRequest("name must not be empty");
    }
    return normalized;
  }
}
