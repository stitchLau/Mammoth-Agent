package com.mammoth.domain.web;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Product;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer and product master data; writes require an authenticated active user (session cookie). */
@RestController
@RequestMapping("/api")
public class CatalogController {

  private final CatalogService catalogService;
  private final AuthorizationService authorizationService;

  public CatalogController(CatalogService catalogService, AuthorizationService authorizationService) {
    this.catalogService = catalogService;
    this.authorizationService = authorizationService;
  }

  public record CreateNamedRequest(String name) {
  }

  public record CustomerResponse(String id, String name, String status, String createdAt) {

    static CustomerResponse from(Customer customer) {
      return new CustomerResponse(
          customer.getId(), customer.getName(), customer.getStatus(), customer.getCreatedAt());
    }
  }

  public record ProductResponse(String id, String name, String status, String createdAt) {

    static ProductResponse from(Product product) {
      return new ProductResponse(
          product.getId(), product.getName(), product.getStatus(), product.getCreatedAt());
    }
  }

  @PostMapping("/customers")
  public CustomerResponse createCustomer(@RequestBody CreateNamedRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return CustomerResponse.from(catalogService.createCustomerAsAdmin(actor, request.name()));
  }

  @GetMapping("/customers")
  public List<CustomerResponse> listCustomers() {
    CurrentUser.require(authorizationService);
    return catalogService.listCustomers().stream().map(CustomerResponse::from).toList();
  }

  @PostMapping("/products")
  public ProductResponse createProduct(@RequestBody CreateNamedRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    return ProductResponse.from(catalogService.createProductAsAdmin(actor, request.name()));
  }

  @GetMapping("/products")
  public List<ProductResponse> listProducts() {
    CurrentUser.require(authorizationService);
    return catalogService.listProducts().stream().map(ProductResponse::from).toList();
  }
}
