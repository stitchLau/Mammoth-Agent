package com.mammoth.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Jira Data Center read-only connector settings. The token is read from env/OS and is never logged,
 * stored in the database or included in an export.
 */
@Component
@ConfigurationProperties(prefix = "mammoth.jira")
public class JiraProperties {

  private String baseUrl = "http://119.145.133.146:18009";
  private String token = "";
  private String jql = "updated >= -30d ORDER BY updated DESC";
  private int maxResults = 100;
  private int pageSize = 50;

  public boolean configured() {
    return token != null && !token.isBlank() && baseUrl != null && !baseUrl.isBlank();
  }

  public String getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public String getJql() {
    return jql;
  }

  public void setJql(String jql) {
    this.jql = jql;
  }

  public int getMaxResults() {
    return maxResults;
  }

  public void setMaxResults(int maxResults) {
    this.maxResults = maxResults;
  }

  public int getPageSize() {
    return pageSize;
  }

  public void setPageSize(int pageSize) {
    this.pageSize = pageSize;
  }
}
