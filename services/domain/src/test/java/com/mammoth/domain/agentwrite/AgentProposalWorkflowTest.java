package com.mammoth.domain.agentwrite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mammoth.domain.config.AgentWriteProperties;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AppUserRepository;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.support.Ids;
import com.mammoth.domain.web.AgentProposalController;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.web.ApiExceptionHandler;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * ADR-0015 P1 acceptance (slice 2): agent propose-only API, idempotency, validation, membership and
 * denial audit. Approval/apply arrive in later slices; this test never expects a business write.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AgentProposalWorkflowTest {

  private static final Path DATA_ROOT = createDataRoot();
  private static final String TEST_TOKEN = "test-agent-write-token";

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-adr0015-propose");
    } catch (Exception error) {
      throw new IllegalStateException(error);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("mammoth.data-root", DATA_ROOT::toString);
  }

  @Autowired
  private UserService userService;

  @Autowired
  private CatalogService catalogService;

  @Autowired
  private ProjectService projectService;

  @Autowired
  private AppUserRepository appUserRepository;

  @Autowired
  private AgentProposalService proposalService;

  @Autowired
  private AgentProposalRepository proposalRepository;

  @Autowired
  private AgentWriteAuditRepository auditRepository;

  @Autowired
  private AgentWriteProperties properties;

  @Autowired
  private ObjectMapper objectMapper;

  private AppUser owner;
  private Project project;
  private String agentId;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser human(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  private AppUser agentUser(String id, String status) {
    return appUserRepository.save(new AppUser(id, owner.getOrgId(), unique("agent"), "Agent", "x",
        status, Ids.nowIso(), null, AppUser.ACCOUNT_AGENT, false));
  }

  private Project project(AppUser owner, String codePrefix) {
    Customer customer = catalogService.createCustomer(owner, unique(codePrefix));
    return projectService.create(owner, customer.getId(), unique("PRJ"), "ADR-0015 project");
  }

  @BeforeEach
  void setUp() {
    owner = human("owner");
    agentId = "agent-" + UUID.randomUUID();
    agentUser(agentId, AppUser.STATUS_ACTIVE);
    project = project(owner, "ACME");
    projectService.addMember(owner, project.getId(), agentId, AuthorizationService.VIEWER);
  }

  @Test
  void proposeCreatesPendingProposalAndProposeAudit() {
    AgentProposal proposal = proposalService.propose(agentId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE,
        "{\"title\":\" 新需求 \",\"summary\":\"s\",\"priority\":\"HIGH\",\"extra\":\"ignored\"}",
        "给评审的摘要", "idem-propose-1", "dsh-session-1");

    assertThat(proposal.getStatus()).isEqualTo(AgentProposal.STATUS_PENDING);
    assertThat(proposal.getRiskLevel()).isEqualTo(AgentProposal.RISK_NORMAL);
    assertThat(proposal.getProposerKind()).isEqualTo(AgentProposal.PROPOSER_AGENT);
    assertThat(proposal.getProposerUserId()).isEqualTo(agentId);
    assertThat(proposal.getPayloadJson())
        .contains("\"title\":\"新需求\"")
        .contains("\"priority\":\"high\"")
        .doesNotContain("extra");

    List<AgentWriteAudit> audit =
        auditRepository.findByProposalIdOrderByAtAscIdAsc(proposal.getId());
    assertThat(audit).hasSize(1);
    assertThat(audit.get(0).getStage()).isEqualTo(AgentWriteAudit.STAGE_PROPOSE);
    assertThat(audit.get(0).getActorKind()).isEqualTo("agent");
    assertThat(audit.get(0).getActorUserId()).isEqualTo(agentId);
    assertThat(audit.get(0).getOutcome()).isEqualTo("ok");
    assertThat(audit.get(0).getPrevHash()).isNull();
    assertThat(audit.get(0).getEntryHash()).hasSize(64);
  }

  @Test
  void idempotentProposeReturnsTheExistingRowAndDoesNotAuditTwice() {
    String payload = "{\"title\":\"幂等需求\"}";
    AgentProposal first = proposalService.propose(agentId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, payload, "s", "idem-same", null);
    AgentProposal second = proposalService.propose(agentId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, payload, "s", "idem-same", null);

    assertThat(second.getId()).isEqualTo(first.getId());
    assertThat(proposalRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())).hasSize(1);
    assertThat(auditRepository.findByProposalIdOrderByAtAscIdAsc(first.getId())).hasSize(1);
  }

  @Test
  void idempotencyKeyReusedForAnotherProjectConflicts() {
    Project other = project(owner, "OTHER");
    projectService.addMember(owner, other.getId(), agentId, AuthorizationService.VIEWER);
    proposalService.propose(agentId, project.getId(), AgentProposalService.TYPE_WORK_ITEM_CREATE,
        "{\"title\":\"a\"}", "s", "idem-cross", null);

    assertThatThrownBy(() -> proposalService.propose(agentId, other.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"title\":\"b\"}", "s", "idem-cross", null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("idempotency key");
  }

  @Test
  void unsupportedTypeAndMalformedPayloadAreRejected() {
    assertThatThrownBy(() -> proposalService.propose(agentId, project.getId(),
        "work_item.delete", "{}", "s", "idem-type", null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unsupported proposal type");

    assertThatThrownBy(() -> proposalService.propose(agentId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"summary\":\"no title\"}", "s",
        "idem-payload", null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("title is required");

    assertThatThrownBy(() -> proposalService.propose(agentId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"title\":42}", "s", "idem-payload-2",
        null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("title must be a string");

    assertThatThrownBy(() -> proposalService.propose(agentId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"title\":\"x\"}", "s", " ", null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("idempotencyKey is required");
  }

  @Test
  void nonMemberAgentIsForbiddenAndInactiveAgentIsUnauthorized() {
    String outsiderId = "agent-outsider-" + UUID.randomUUID();
    agentUser(outsiderId, AppUser.STATUS_ACTIVE);
    assertThatThrownBy(() -> proposalService.propose(outsiderId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"title\":\"x\"}", "s", "idem-outsider",
        null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");

    String disabledId = "agent-disabled-" + UUID.randomUUID();
    agentUser(disabledId, AppUser.STATUS_DISABLED);
    assertThatThrownBy(() -> proposalService.propose(disabledId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"title\":\"x\"}", "s", "idem-disabled",
        null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("agent identity");
  }

  @Test
  void unknownProjectIsNotFound() {
    assertThatThrownBy(() -> proposalService.propose(agentId, "missing-project",
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"title\":\"x\"}", "s", "idem-project",
        null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("project not found");
  }

  @Test
  void highRiskTypesAreDerivedServerSide() {
    assertThat(AgentProposalService.deriveRisk("solution.finalize"))
        .isEqualTo(AgentProposal.RISK_HIGH);
    assertThat(AgentProposalService.deriveRisk(AgentProposalService.TYPE_WORK_ITEM_CREATE))
        .isEqualTo(AgentProposal.RISK_NORMAL);
  }

  @Test
  void denialAuditIsPersistedOutsideTheFailedRequest() {
    proposalService.recordDenied(agentId, null, "invalid-agent-token");

    List<AgentWriteAudit> denials =
        auditRepository.findByProposalIdOrderByAtAscIdAsc("unresolved");
    assertThat(denials).isNotEmpty();
    assertThat(denials.stream()
        .anyMatch(row -> AgentWriteAudit.STAGE_DENY.equals(row.getStage())
            && "denied:invalid-agent-token".equals(row.getOutcome())
            && "agent".equals(row.getActorKind()))).isTrue();
  }

  @Test
  void humanListingRequiresProjectMembership() {
    AgentProposal proposal = proposalService.propose(agentId, project.getId(),
        AgentProposalService.TYPE_WORK_ITEM_CREATE, "{\"title\":\"列表需求\"}", "s", "idem-list",
        null);

    AppUser outsider = human("outsider");
    assertThatThrownBy(() -> proposalService.list(outsider, project.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");

    assertThat(proposalService.list(owner, project.getId()))
        .extracting(AgentProposal::getId)
        .containsExactly(proposal.getId());
  }

  @Test
  void controllerRejectsMissingOrWrongServiceTokenAndAcceptsConfiguredIdentity()
      throws Exception {
    properties.setAgentToken(TEST_TOKEN);
    properties.setAgentUserId(agentId);
    AgentProposalController controller =
        new AgentProposalController(proposalService, properties,
            org.mockito.Mockito.mock(AuthorizationService.class), objectMapper);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new ApiExceptionHandler())
        .build();

    String body = objectMapper.writeValueAsString(java.util.Map.of(
        "projectId", project.getId(),
        "proposalType", AgentProposalService.TYPE_WORK_ITEM_CREATE,
        "payload", java.util.Map.of("title", "来自控制器"),
        "idempotencyKey", "idem-controller-1"));

    mockMvc.perform(post("/api/agent/proposals").contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/agent/proposals").contentType(MediaType.APPLICATION_JSON)
            .header(AgentProposalController.AGENT_TOKEN_HEADER, "wrong-token").content(body))
        .andExpect(status().isUnauthorized());
    mockMvc.perform(post("/api/agent/proposals").contentType(MediaType.APPLICATION_JSON)
            .header(AgentProposalController.AGENT_TOKEN_HEADER, TEST_TOKEN).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(AgentProposal.STATUS_PENDING))
        .andExpect(jsonPath("$.proposerUserId").value(agentId));
  }

  @Test
  void controllerFailsClosedWhenServiceIdentityIsUnconfigured() throws Exception {
    properties.setAgentToken("");
    properties.setAgentUserId("");
    AgentProposalController controller =
        new AgentProposalController(proposalService, properties,
            org.mockito.Mockito.mock(AuthorizationService.class), objectMapper);
    MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new ApiExceptionHandler())
        .build();

    mockMvc.perform(post("/api/agent/proposals").contentType(MediaType.APPLICATION_JSON)
            .content("{\"projectId\":\"" + project.getId() + "\","
                + "\"proposalType\":\"work_item.create\",\"payload\":{\"title\":\"x\"},"
                + "\"idempotencyKey\":\"idem-unconfigured\"}"))
        .andExpect(status().isServiceUnavailable());
  }
}
