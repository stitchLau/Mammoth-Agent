package com.mammoth.domain.solution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.AuthorizationService;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.RequirementDimension;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import com.mammoth.domain.workitem.WorkItemStatus;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** M2 S3–S5 acceptance: consistency gate, dual views, review, approval and immutable versions. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SolutionWorkflowTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-m2-solution");
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
  private WorkItemService workItemService;

  @Autowired
  private SolutionService solutionService;

  private String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private AppUser user(String prefix) {
    return userService.create(unique(prefix), "User", "Passw0rd!2026");
  }

  private record Fixture(AppUser owner, AppUser reviewer, Project project, WorkItem item) {
  }

  private Fixture readyRequirement() {
    AppUser owner = user("owner");
    AppUser reviewer = user("reviewer");
    Customer customer = catalogService.createCustomer(owner, unique("ACME"));
    Project project = projectService.create(owner, customer.getId(), unique("PRJ"),
        "solution project");
    projectService.addMember(owner, project.getId(), reviewer.getId(), AuthorizationService.REVIEWER);
    WorkItem item = workItemService.create(owner, project.getId(), "收货扫码重复入库",
        "客户只给了口头描述", "normal", null);
    workItemService.addEvidence(owner, project.getId(), item.getId(), "conversation",
        "现场反馈：同一托盘重复扫码会重复入库。", null, null, null);
    for (String dimension : RequirementDimension.ALL) {
      workItemService.assess(owner, project.getId(), item.getId(), dimension,
          RequirementDimension.CONFIRMED, "已确认", null, null);
    }
    workItemService.confirmReadiness(reviewer, project.getId(), item.getId());
    return new Fixture(owner, reviewer, project, item);
  }

  /** Builds a minimal consistent structure and returns the created ids. */
  private record Structure(String businessId, String technicalId, String sharedId,
      String criterionId, String taskId) {
  }

  private Structure buildStructure(AppUser owner, Project project, WorkItem item, String versionId) {
    String business = solutionService.addDesignItem(owner, project.getId(), item.getId(), versionId,
        "business", null, "收货扫码流程", "扫码重复时冻结库存并转人工复核。").getId();
    String technical = solutionService.addDesignItem(owner, project.getId(), item.getId(), versionId,
        "technical", business, "扫码服务改造", "在扫码服务中增加重复校验。").getId();
    String shared = solutionService.addDesignItem(owner, project.getId(), item.getId(), versionId,
        "shared", null, "数据口径", "条码 + 托盘号唯一。").getId();
    String criterion = solutionService.addCriterion(owner, project.getId(), item.getId(), versionId,
        business, "重复扫码被拦截且当天闭环").getId();
    String task = solutionService.addTask(owner, project.getId(), item.getId(), versionId,
        technical, criterion, "实现扫码重复校验", "在扫码接口增加幂等判断",
        "重复扫码返回可读提示", null).getId();
    return new Structure(business, technical, shared, criterion, task);
  }

  private SolutionVersion submitStructure(Fixture fixture) {
    WorkItem item = fixture.item();
    SolutionVersion version = solutionService.createVersion(fixture.owner(), fixture.project().getId(),
        item.getId(), "收货扫码异常处理方案", "初版");
    buildStructure(fixture.owner(), fixture.project(), item, version.getId());
    solutionService.submit(fixture.owner(), fixture.project().getId(), item.getId(),
        version.getId());
    return version;
  }

  @Test
  void consistencyGateBlocksIncompleteModelAndAcceptsCompleteOne() {
    Fixture fixture = readyRequirement();
    WorkItem item = fixture.item();
    SolutionVersion version = solutionService.createVersion(fixture.owner(),
        fixture.project().getId(), item.getId(), "收货扫码异常处理方案", "初版");
    assertThat(version.getVersion()).isEqualTo(1);
    assertThat(workItemService.requireItem(fixture.project().getId(), item.getId()).getStatus())
        .isEqualTo(WorkItemStatus.ANALYZING);

    assertThatThrownBy(() -> solutionService.submit(fixture.owner(), fixture.project().getId(),
        item.getId(), version.getId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not consistent")
        .hasMessageContaining("required_item_missing");

    buildStructure(fixture.owner(), fixture.project(), item, version.getId());
    SolutionService.Detail detail = solutionService.detail(fixture.owner(),
        fixture.project().getId(), item.getId(), version.getId());
    assertThat(detail.report().ok()).isTrue();
    assertThat(detail.report().counts().businessItems()).isEqualTo(1);
    assertThat(detail.report().counts().technicalItems()).isEqualTo(1);
    assertThat(detail.report().counts().acceptanceCriteria()).isEqualTo(1);
    assertThat(detail.report().counts().tasks()).isEqualTo(1);

    SolutionVersion submitted = solutionService.submit(fixture.owner(), fixture.project().getId(),
        item.getId(), version.getId());
    assertThat(submitted.getStatus()).isEqualTo(SolutionVersion.IN_REVIEW);
    assertThat(submitted.getContextSnapshotId()).isNotBlank();
    assertThat(workItemService.requireItem(fixture.project().getId(), item.getId()).getStatus())
        .isEqualTo(WorkItemStatus.SOLUTION_REVIEW);

    WorkItemService.Detail afterSubmit = workItemService.detail(fixture.owner(),
        fixture.project().getId(), item.getId());
    assertThat(afterSubmit.snapshots()).hasSize(2);
    assertThat(afterSubmit.snapshots().get(1).getPayloadJson())
        .contains("\"purpose\":\"solution_submit\"");
    assertThat(solutionService.detail(fixture.owner(), fixture.project().getId(), item.getId(),
        version.getId()).approvals())
        .extracting(ApprovalRecord::getAction)
        .containsExactly(ApprovalRecord.ACTION_SUBMIT);
  }

  @Test
  void dualViewsRenderFromTheSameModelAndTraceabilityIsDerived() {
    Fixture fixture = readyRequirement();
    WorkItem item = fixture.item();
    SolutionVersion version = solutionService.createVersion(fixture.owner(),
        fixture.project().getId(), item.getId(), "收货扫码异常处理方案", "初版");
    buildStructure(fixture.owner(), fixture.project(), item, version.getId());

    SolutionRenderer.RenderResult business = solutionService.render(fixture.owner(),
        fixture.project().getId(), item.getId(), version.getId(), "business");
    assertThat(business.view()).isEqualTo("business");
    assertThat(business.markdown()).contains("# 业务方案：收货扫码异常处理方案");
    assertThat(business.markdown()).contains("## 1. 业务设计");
    assertThat(business.markdown()).doesNotContain("## 1. 技术设计");
    assertThat(business.markdown()).doesNotContain("## 3. 开发任务");
    assertThat(business.markdown()).contains("收货扫码流程");
    assertThat(business.markdown()).contains("数据口径");
    assertThat(business.markdown()).contains("## 3. 追溯矩阵");
    assertThat(business.designItemCount()).isEqualTo(2);
    assertThat(business.taskCount()).isZero();
    assertThat(business.traceability()).hasSize(1);
    assertThat(business.traceability().get(0).goal()).isEqualTo("收货扫码流程");
    assertThat(business.traceability().get(0).tasks()).contains("实现扫码重复校验");
    assertThat(business.traceability().get(0).criteria()).contains("重复扫码被拦截且当天闭环");

    SolutionRenderer.RenderResult technical = solutionService.render(fixture.owner(),
        fixture.project().getId(), item.getId(), version.getId(), "technical");
    assertThat(technical.view()).isEqualTo("technical");
    assertThat(technical.markdown()).contains("# 技术方案：收货扫码异常处理方案");
    assertThat(technical.markdown()).contains("## 1. 技术设计");
    assertThat(technical.markdown()).doesNotContain("## 1. 业务设计");
    assertThat(technical.markdown()).contains("扫码服务改造");
    assertThat(technical.markdown()).contains("数据口径");
    assertThat(technical.markdown()).contains("实现扫码重复校验");
    assertThat(technical.markdown()).contains("## 4. 追溯矩阵");
    assertThat(technical.designItemCount()).isEqualTo(2);
    assertThat(technical.taskCount()).isEqualTo(1);

    assertThatThrownBy(() -> solutionService.render(fixture.owner(), fixture.project().getId(),
        item.getId(), version.getId(), "marketing"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("unsupported solution view");
  }

  @Test
  void goalWithoutAcceptanceIsReportedAsIssue() {
    Fixture fixture = readyRequirement();
    WorkItem item = fixture.item();
    SolutionVersion version = solutionService.createVersion(fixture.owner(),
        fixture.project().getId(), item.getId(), "方案", null);
    buildStructure(fixture.owner(), fixture.project(), item, version.getId());
    String orphanGoal = solutionService.addDesignItem(fixture.owner(), fixture.project().getId(),
        item.getId(), version.getId(), "business", null, "未覆盖的业务目标", "没有验收标准").getId();

    SolutionService.Detail detail = solutionService.detail(fixture.owner(),
        fixture.project().getId(), item.getId(), version.getId());
    assertThat(detail.report().ok()).isFalse();
    assertThat(detail.report().issues())
        .anyMatch(issue -> issue.code().equals(SolutionConsistency.GOAL_WITHOUT_ACCEPTANCE)
            && orphanGoal.equals(issue.objectId()));

    // Attaching a criterion to the orphan goal clears the issue.
    solutionService.addCriterion(fixture.owner(), fixture.project().getId(), item.getId(),
        version.getId(), orphanGoal, "该目标可验收");
    assertThat(solutionService.detail(fixture.owner(), fixture.project().getId(), item.getId(),
        version.getId()).report().ok()).isTrue();
  }

  @Test
  void authorCannotApproveAndApprovedVersionsAreImmutable() {
    Fixture fixture = readyRequirement();
    WorkItem item = fixture.item();
    SolutionVersion version = submitStructure(fixture);

    assertThatThrownBy(() -> solutionService.approve(fixture.owner(), fixture.project().getId(),
        item.getId(), version.getId(), "自批"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot approve their own solution");

    SolutionVersion approved = solutionService.approve(fixture.reviewer(),
        fixture.project().getId(), item.getId(), version.getId(), "同意");
    assertThat(approved.getStatus()).isEqualTo(SolutionVersion.APPROVED);
    assertThat(approved.getApproverUserId()).isEqualTo(fixture.reviewer().getId());
    WorkItem stored = workItemService.requireItem(fixture.project().getId(), item.getId());
    assertThat(stored.getStatus()).isEqualTo(WorkItemStatus.APPROVED);
    assertThat(stored.getApprovedSolutionVersionId()).isEqualTo(version.getId());

    assertThatThrownBy(() -> solutionService.addDesignItem(fixture.owner(),
        fixture.project().getId(), item.getId(), version.getId(), "business", null, "改方案", "x"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("can no longer be edited");

    // A new version copies the structure and reopens the requirement for revision.
    SolutionVersion revision = solutionService.newVersion(fixture.owner(), fixture.project().getId(),
        item.getId(), version.getId(), "客户补充异常清单");
    assertThat(revision.getVersion()).isEqualTo(2);
    assertThat(revision.getStatus()).isEqualTo(SolutionVersion.DRAFT);
    assertThat(workItemService.requireItem(fixture.project().getId(), item.getId()).getStatus())
        .isEqualTo(WorkItemStatus.ANALYZING);
    assertThat(solutionService.detail(fixture.owner(), fixture.project().getId(), item.getId(),
        revision.getId()).designItems()).hasSize(3);

    solutionService.submit(fixture.owner(), fixture.project().getId(), item.getId(),
        revision.getId());
    solutionService.approve(fixture.reviewer(), fixture.project().getId(), item.getId(),
        revision.getId(), "同意修订");
    assertThat(solutionService.listVersions(fixture.owner(), fixture.project().getId(), item.getId()))
        .extracting(SolutionVersion::getStatus)
        .containsExactly(SolutionVersion.SUPERSEDED, SolutionVersion.APPROVED);
  }

  @Test
  void requestChangesRequiresCommentAndReopensTheDraft() {
    Fixture fixture = readyRequirement();
    WorkItem item = fixture.item();
    SolutionVersion version = submitStructure(fixture);

    assertThatThrownBy(() -> solutionService.requestChanges(fixture.reviewer(),
        fixture.project().getId(), item.getId(), version.getId(), null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("requires a comment");

    SolutionVersion reopened = solutionService.requestChanges(fixture.reviewer(),
        fixture.project().getId(), item.getId(), version.getId(), "缺少异常清单");
    assertThat(reopened.getStatus()).isEqualTo(SolutionVersion.DRAFT);
    assertThat(workItemService.requireItem(fixture.project().getId(), item.getId()).getStatus())
        .isEqualTo(WorkItemStatus.ANALYZING);
    assertThat(solutionService.detail(fixture.owner(), fixture.project().getId(), item.getId(),
        version.getId()).approvals())
        .extracting(ApprovalRecord::getAction)
        .contains(ApprovalRecord.ACTION_REQUEST_CHANGES);
  }

  @Test
  void structureGuardsRejectCrossVersionAndUntracedData() {
    Fixture fixture = readyRequirement();
    WorkItem item = fixture.item();
    SolutionVersion first = solutionService.createVersion(fixture.owner(),
        fixture.project().getId(), item.getId(), "方案 v1", null);
    Structure structure = buildStructure(fixture.owner(), fixture.project(), item, first.getId());

    assertThatThrownBy(() -> solutionService.addTask(fixture.owner(), fixture.project().getId(),
        item.getId(), first.getId(), null, null, "无追溯任务", null, null, null))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("must trace to a design item or an acceptance criterion");

    SolutionVersion second = solutionService.createVersion(fixture.owner(),
        fixture.project().getId(), item.getId(), "方案 v2", null);
    String foreignItem = solutionService.addDesignItem(fixture.owner(), fixture.project().getId(),
        item.getId(), second.getId(), "business", null, "v2 目标", "").getId();

    assertThatThrownBy(() -> solutionService.addCriterion(fixture.owner(),
        fixture.project().getId(), item.getId(), first.getId(), foreignItem, "跨版本验收"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("design item not found in this solution version");

    assertThatThrownBy(() -> solutionService.deleteDesignItem(fixture.owner(),
        fixture.project().getId(), item.getId(), first.getId(), structure.businessId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("child items");

    solutionService.addCriterion(fixture.owner(), fixture.project().getId(), item.getId(),
        first.getId(), structure.sharedId(), "数据口径可验收");
    assertThatThrownBy(() -> solutionService.deleteDesignItem(fixture.owner(),
        fixture.project().getId(), item.getId(), first.getId(), structure.sharedId()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("referenced by acceptance criteria");

    assertThatThrownBy(() -> solutionService.addTask(fixture.owner(), fixture.project().getId(),
        item.getId(), first.getId(), structure.technicalId(), null, "依赖不存在", null, null,
        UUID.randomUUID().toString()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("task not found in this solution version");
  }

  @Test
  void viewerCannotEditSolution() {
    Fixture fixture = readyRequirement();
    AppUser viewer = user("viewer");
    projectService.addMember(fixture.owner(), fixture.project().getId(), viewer.getId(),
        AuthorizationService.VIEWER);
    SolutionVersion version = solutionService.createVersion(fixture.owner(),
        fixture.project().getId(), fixture.item().getId(), "方案", null);

    assertThatThrownBy(() -> solutionService.addDesignItem(viewer, fixture.project().getId(),
        fixture.item().getId(), version.getId(), "business", null, "越权", "x"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("cannot perform this action");

    // A viewer can read the version list.
    assertThat(solutionService.listVersions(viewer, fixture.project().getId(),
        fixture.item().getId())).hasSize(1);
  }

}
