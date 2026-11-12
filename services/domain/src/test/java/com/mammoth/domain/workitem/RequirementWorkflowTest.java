package com.mammoth.domain.workitem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.AuditLogRepository;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** M2 S1/S2 acceptance: evidence batches, readiness gate, snapshots and guarded transitions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RequirementWorkflowTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-m2-requirement");
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
  private ArtifactService artifactService;

  @Autowired
  private WorkItemService workItemService;

  @Autowired
  private AuditLogRepository auditLogRepository;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  private Project project(AppUser owner) {
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    return projectService.create(owner, customer.getId(), unique("PRJ"), "requirement project");
  }

  private ArtifactRecord artifact(AppUser owner, Project project, String text) {
    return artifactService.upload(owner, project.getId(), "source", unique("notes") + ".md",
        "text/markdown", null, text.getBytes(StandardCharsets.UTF_8));
  }

  private WorkItem requirement(AppUser owner, Project project) {
    return workItemService.create(owner, project.getId(), "收货流程条码重复处理",
        "客户口头描述了异常场景，尚无验收标准", "high", "客户会议纪要");
  }

  private void assessAllConfirmed(AppUser owner, Project project, String workItemId) {
    for (String dimension : RequirementDimension.ALL) {
      workItemService.assess(owner, project.getId(), workItemId, dimension,
          RequirementDimension.CONFIRMED, "已与客户确认", null, null);
    }
  }

  @Test
  void evidenceMovesDraftToCollectingAndKeepsBatchesImmutable() {
    AppUser owner = user("owner");
    Project project = project(owner);
    ArtifactRecord artifact = artifact(owner, project, "客户口头描述：条码重复时先冻结库存。");

    WorkItem item = requirement(owner, project);
    assertThat(item.getStatus()).isEqualTo(WorkItemStatus.DRAFT);
    assertThat(item.getItemNo()).isEqualTo("REQ-0001");

    RequirementEvidence first = workItemService.addEvidence(owner, project.getId(), item.getId(),
        "conversation", "现场反馈：同一托盘两次扫码都会重复入库。", null, null, null);
    assertThat(first.getBatchNo()).isEqualTo(1);
    assertThat(workItemService.requireItem(project.getId(), item.getId()).getStatus())
        .isEqualTo(WorkItemStatus.COLLECTING);

    RequirementEvidence second = workItemService.addEvidence(owner, project.getId(), item.getId(),
        "artifact", "会议纪要第 2 节记录了异常处理要求。", artifact.getId(), "§2", null);
    assertThat(second.getBatchNo()).isEqualTo(2);

    WorkItemService.Detail detail = workItemService.detail(owner, project.getId(), item.getId());
    assertThat(detail.evidence()).hasSize(2);
    assertThat(detail.evidence().get(1).getArtifactId()).isEqualTo(artifact.getId());

    assertThatThrownBy(() -> workItemService.addEvidence(owner, project.getId(), item.getId(),
        "artifact", "跨项目引用", artifact(owner, project(owner), "另一个项目").getId(), null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("artifact not found");

    assertThat(auditLogRepository.countByAction("work_item.evidence_add")).isPositive();
  }

  @Test
  void readinessGateBlocksCriticalGapsAndAcceptsDocumentedAssumptions() {
    AppUser owner = user("owner2");
    Project project = project(owner);
    WorkItem item = requirement(owner, project);
    workItemService.addEvidence(owner, project.getId(), item.getId(), "note", "首批输入", null, null,
        null);

    assertThatThrownBy(() -> workItemService.confirmReadiness(owner, project.getId(), item.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not ready")
        .hasMessageContaining("background: missing on a critical dimension");

    assessAllConfirmed(owner, project, item.getId());
    // Non-critical dimension with an explicit assumption and decision owner is acceptable.
    workItemService.assess(owner, project.getId(), item.getId(), RequirementDimension.EXCEPTIONS,
        RequirementDimension.MISSING, "客户尚未提供异常清单", "按行业通用异常处理", owner.getId());
    ReadinessService.ReadinessSummary summary =
        workItemService.readiness(owner, project.getId(), item.getId());
    assertThat(summary.ready()).isTrue();
    assertThat(summary.blocking()).isEmpty();

    // A critical dimension can never be waved through with an assumption.
    workItemService.assess(owner, project.getId(), item.getId(), RequirementDimension.ACCEPTANCE,
        RequirementDimension.MISSING, "验收方式待定", "按客户口头标准", owner.getId());
    assertThat(workItemService.readiness(owner, project.getId(), item.getId()).blocking())
        .anyMatch(reason -> reason.startsWith("acceptance: missing"));

    // Conflicts always block, even on a non-critical dimension.
    workItemService.assess(owner, project.getId(), item.getId(), RequirementDimension.ACCEPTANCE,
        RequirementDimension.CONFIRMED, null, null, null);
    workItemService.assess(owner, project.getId(), item.getId(), RequirementDimension.INTEGRATIONS,
        RequirementDimension.CONFLICT, "两份纪要说法冲突", null, null);
    assertThat(workItemService.readiness(owner, project.getId(), item.getId()).blocking())
        .anyMatch(reason -> reason.startsWith("integrations: unresolved conflict"));

    assertThatThrownBy(() -> workItemService.confirmReadiness(owner, project.getId(), item.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unresolved conflict");
  }

  @Test
  void confirmReadinessCreatesSnapshotAndRequiresCollectingState() {
    AppUser owner = user("owner3");
    AppUser reviewer = user("reviewer3");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), reviewer.getId(), AuthorizationService.REVIEWER);
    WorkItem item = requirement(owner, project);
    workItemService.addEvidence(owner, project.getId(), item.getId(), "note", "首批输入", null, null,
        null);
    assessAllConfirmed(owner, project, item.getId());

    ReadinessService.ReadinessSummary confirmed =
        workItemService.confirmReadiness(reviewer, project.getId(), item.getId());
    assertThat(confirmed.ready()).isTrue();
    assertThat(confirmed.confirmedBy()).isEqualTo(reviewer.getId());
    assertThat(confirmed.snapshotId()).isNotBlank();

    WorkItem stored = workItemService.requireItem(project.getId(), item.getId());
    assertThat(stored.getStatus()).isEqualTo(WorkItemStatus.READY_FOR_ANALYSIS);
    assertThat(stored.getContextSnapshotId()).isEqualTo(confirmed.snapshotId());

    WorkItemService.Detail detail = workItemService.detail(owner, project.getId(), item.getId());
    assertThat(detail.snapshots()).hasSize(1);
    assertThat(detail.snapshots().get(0).getVersion()).isEqualTo(1);
    assertThat(detail.snapshots().get(0).getPayloadJson())
        .contains("\"purpose\":\"readiness\"")
        .contains("\"catalogVersion\":1")
        .contains("\"itemNo\":\"REQ-0001\"");
    assertThat(auditLogRepository.countByAction("work_item.readiness_confirm")).isPositive();

    assertThatThrownBy(() -> workItemService.confirmReadiness(owner, project.getId(), item.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("must be in collecting");
  }

  @Test
  void viewerCannotWriteAndOutsiderCannotRead() {
    AppUser owner = user("owner4");
    AppUser viewer = user("viewer4");
    AppUser outsider = user("outsider4");
    Project project = project(owner);
    projectService.addMember(owner, project.getId(), viewer.getId(), AuthorizationService.VIEWER);
    WorkItem item = requirement(owner, project);

    assertThatThrownBy(() -> workItemService.addEvidence(viewer, project.getId(), item.getId(),
        "note", "viewer 不应能追加证据", null, null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");

    assertThatThrownBy(() -> workItemService.confirmReadiness(viewer, project.getId(), item.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");

    assertThatThrownBy(() -> workItemService.detail(outsider, project.getId(), item.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("no access to project");

    // Viewer can still read the requirement.
    assertThat(workItemService.detail(viewer, project.getId(), item.getId()).item().getId())
        .isEqualTo(item.getId());
  }

  @Test
  void transitionsAreGuardedPausedItemsResumeAndTerminalItemsFreeze() {
    AppUser owner = user("owner5");
    Project project = project(owner);
    WorkItem item = requirement(owner, project);
    workItemService.addEvidence(owner, project.getId(), item.getId(), "note", "输入", null, null, null);

    assertThatThrownBy(() -> workItemService.transition(owner, project.getId(), item.getId(),
        WorkItemStatus.ANALYZING, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot change requirement status");

    WorkItem paused = workItemService.transition(owner, project.getId(), item.getId(),
        WorkItemStatus.ON_HOLD, "等待客户排期");
    assertThat(paused.getStatus()).isEqualTo(WorkItemStatus.ON_HOLD);
    assertThat(paused.getPriorStatus()).isEqualTo(WorkItemStatus.COLLECTING);

    assertThatThrownBy(() -> workItemService.transition(owner, project.getId(), item.getId(),
        WorkItemStatus.ANALYZING, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("can only resume to collecting");

    WorkItem resumed = workItemService.transition(owner, project.getId(), item.getId(),
        WorkItemStatus.COLLECTING, null);
    assertThat(resumed.getStatus()).isEqualTo(WorkItemStatus.COLLECTING);
    assertThat(resumed.getPriorStatus()).isNull();

    assertThatThrownBy(() -> workItemService.transition(owner, project.getId(), item.getId(),
        WorkItemStatus.APPROVED, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("dedicated command");

    WorkItem withdrawn = workItemService.transition(owner, project.getId(), item.getId(),
        WorkItemStatus.WITHDRAWN, "客户取消");
    assertThat(withdrawn.getStatus()).isEqualTo(WorkItemStatus.WITHDRAWN);
    assertThatThrownBy(() -> workItemService.update(owner, project.getId(), item.getId(), "新标题",
        null, "normal", null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("can no longer be edited");
  }

  @Test
  void assessmentsRejectUnknownDimensionsAndStates() {
    AppUser owner = user("owner6");
    Project project = project(owner);
    WorkItem item = requirement(owner, project);

    assertThatThrownBy(() -> workItemService.assess(owner, project.getId(), item.getId(),
        "made_up", "confirmed", null, null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unknown readiness dimension");

    assertThatThrownBy(() -> workItemService.assess(owner, project.getId(), item.getId(),
        RequirementDimension.GOALS, "maybe", null, null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unknown readiness state");
  }
}
