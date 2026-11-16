package com.mammoth.domain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ADR-0015 P1/P2: service identities for the controlled-write endpoints.
 *
 * <p>The agent (propose) and approval service (apply) use <b>separate</b> tokens: the Host/agent
 * process only ever holds the propose token, while the apply token lives with the isolated approval
 * service account (P2: OS credential store / service-account-only ACL file). A blank token disables
 * that endpoint (fail-closed); tokens are never logged or stored in the database.
 */
@Component
@ConfigurationProperties(prefix = "mammoth.agent-write")
public class AgentWriteProperties {

  private String agentToken = "";
  private String applyToken = "";
  private String agentUserId = "";

  public boolean configuredForPropose() {
    return agentToken != null && !agentToken.isBlank()
        && agentUserId != null && !agentUserId.isBlank();
  }

  public boolean configuredForApply() {
    return applyToken != null && !applyToken.isBlank();
  }

  public String getAgentToken() {
    return agentToken;
  }

  public void setAgentToken(String agentToken) {
    this.agentToken = agentToken;
  }

  public String getApplyToken() {
    return applyToken;
  }

  public void setApplyToken(String applyToken) {
    this.applyToken = applyToken;
  }

  public String getAgentUserId() {
    return agentUserId;
  }

  public void setAgentUserId(String agentUserId) {
    this.agentUserId = agentUserId;
  }
}
