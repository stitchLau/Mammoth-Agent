package com.mammoth.domain.web;

import com.mammoth.domain.notification.AgentHookService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.security.CurrentUser;
import com.mammoth.domain.service.AuthorizationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Generic Agent-task hook ingress; currently owner/editor authenticated callers only. */
@RestController
@RequestMapping("/api/projects/{projectId}/agent-hooks")
public class AgentHookController {

  private final AgentHookService agentHookService;
  private final AuthorizationService authorizationService;

  public AgentHookController(AgentHookService agentHookService,
      AuthorizationService authorizationService) {
    this.agentHookService = agentHookService;
    this.authorizationService = authorizationService;
  }

  public record HookRequest(String eventType, String taskId, String title, String summary,
      String link) {
  }

  public record HookResponse(String eventType, String taskId, int created) {
  }

  @PostMapping
  public HookResponse emit(@PathVariable String projectId, @RequestBody HookRequest request) {
    AppUser actor = CurrentUser.require(authorizationService);
    int created = agentHookService.emit(actor, projectId, request.eventType(), request.taskId(),
        request.title(), request.summary(), request.link());
    return new HookResponse(request.eventType(), request.taskId(), created);
  }
}
