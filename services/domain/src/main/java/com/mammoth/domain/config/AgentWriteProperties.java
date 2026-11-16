package com.mammoth.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ADR-0015 P1: service identity for the agent proposal endpoint.
 *
 * <p>P1 uses a machine-local service token plus the configured agent account. P2 replaces the
 * token with an isolated OS identity and credential store. A blank token disables the endpoint
 * (fail-closed); the token is never logged or stored in the database.
 */
@Component
@ConfigurationProperties(prefix = "mammoth.agent-write")
public class AgentWriteProperties {

  private String agentToken = "";
  private String agentUserId = "";

  public boolean configured() {
    return agentToken != null && !agentToken.isBlank()
        && agentUserId != null && !agentUserId.isBlank();
  }

  public String getAgentToken() {
    return agentToken;
  }

  public void setAgentToken(String agentToken) {
    this.agentToken = agentToken;
  }

  public String getAgentUserId() {
    return agentUserId;
  }

  public void setAgentUserId(String agentUserId) {
    this.agentUserId = agentUserId;
  }
}
