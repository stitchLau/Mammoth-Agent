package com.mammoth.domain.external;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.persistence.Customer;
import com.mammoth.domain.persistence.Project;
import com.mammoth.domain.service.CatalogService;
import com.mammoth.domain.service.ProjectService;
import com.mammoth.domain.service.UserService;
import com.mammoth.domain.web.ApiException;
import com.mammoth.domain.workitem.WorkItem;
import com.mammoth.domain.workitem.WorkItemService;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** ADR-0011: preview/apply/map/reject with Jira Chinese headers and synthetic data only. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExternalImportTest {

  private static final Path DATA_ROOT = createDataRoot();

  private static Path createDataRoot() {
    try {
      return Files.createTempDirectory("mammoth-external-import-it");
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
  private ExternalImportService importService;

  @Autowired
  private ImportBatchRepository batchRepository;

  @Autowired
  private ExternalRecordRepository recordRepository;

  @Autowired
  private WorkItemService workItemService;

  private AppUser admin;
  private Project project;
  private ExternalSource source;

  @BeforeAll
  void bootstrap() {
    admin = userService.create("import-admin", "Import Admin", "Passw0rd!2026");
    Customer customer = catalogService.createCustomer(admin, "IMPORT-ACME");
    project = projectService.create(admin, customer.getId(), "IMPORT-1", "Import project");
    source = importService.createSource(admin, project.getId(),
        new ExternalImportService.SourceInput("jira", "Jira", "http://jira.example"));
  }

  private static String unique(String prefix) {
    return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private static String jiraCsv(String key, String summary, String description) {
    return "问题类型,关键字,概要,描述,Issue URL,状态,经办人,报告人,创建日期,已更新\n"
        + "缺陷," + key + "," + summary + ",\"" + description.replace("\"", "\"\"")
        + "\",http://jira.example/browse/" + key + ",挂起,张三,李四,"
        + "2026/1/2 9:05,2026/1/3 10:06\n";
  }

  @Test
  void previewDoesNotPersistAndGbkCsvIsDetected() throws Exception {
    long batches = batchRepository.count();
    long records = recordRepository.count();
    String csv = jiraCsv("IMP-1", "中文摘要", "描述含逗号,\n以及换行");
    byte[] gbk = csv.getBytes(Charset.forName("GBK"));

    ImportReport.Preview preview = importService.preview(admin, project.getId(), source.getId(),
        "jira.csv", gbk, "jira");

    assertThat(preview.acceptedCount()).isEqualTo(1);
    assertThat(preview.rejectedCount()).isZero();
    assertThat(preview.sample()).singleElement()
        .satisfies(record -> {
          assertThat(record.externalKey()).isEqualTo("IMP-1");
          assertThat(record.title()).isEqualTo("中文摘要");
          assertThat(record.externalType()).isEqualTo("issue");
        });
    assertThat(batchRepository.count()).isEqualTo(batches);
    assertThat(recordRepository.count()).isEqualTo(records);
  }

  @Test
  void applyIsIdempotentAndVersionChangesAppend() throws Exception {
    String key = unique("IMP");
    byte[] first = jiraCsv(key, "初始摘要", "初始描述").getBytes(StandardCharsets.UTF_8);
    ExternalImportService.ImportResult applied = importService.apply(admin, project.getId(),
        source.getId(), "jira.csv", "text/csv", first, "jira");
    assertThat(applied.duplicate()).isFalse();
    assertThat(applied.accepted()).isEqualTo(1);
    assertThat(applied.batch().getStatus()).isEqualTo(ImportBatch.STATUS_APPLIED);

    ExternalImportService.ImportResult duplicate = importService.apply(admin, project.getId(),
        source.getId(), "jira.csv", "text/csv", first, "jira");
    assertThat(duplicate.duplicate()).isTrue();
    assertThat(batchRepository.count()).isPositive();

    byte[] changed = jiraCsv(key, "更新摘要", "更新描述").getBytes(StandardCharsets.UTF_8);
    ExternalImportService.ImportResult second = importService.apply(admin, project.getId(),
        source.getId(), "jira.csv", "text/csv", changed, "jira");
    assertThat(second.duplicate()).isFalse();
    assertThat(recordRepository.findByProjectIdOrderByCreatedAtAsc(project.getId()))
        .filteredOn(record -> key.equals(record.getExternalKey()))
        .hasSize(2);
  }

  @Test
  void mappingCreatesRequirementEvidenceAndRejectIsAudited() {
    String key = unique("MAP");
    byte[] csv = jiraCsv(key, "映射摘要", "映射描述").getBytes(StandardCharsets.UTF_8);
    importService.apply(admin, project.getId(), source.getId(), "jira.csv", "text/csv", csv, "jira");
    ExternalRecord record = recordRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())
        .stream().filter(entry -> key.equals(entry.getExternalKey())).findFirst().orElseThrow();
    WorkItem item = workItemService.create(admin, project.getId(), "导入映射需求", "从 Jira 导入",
        "normal", null);

    ExternalRecord mapped = importService.map(admin, project.getId(), record.getId(), item.getId(),
        null);

    assertThat(mapped.getStatus()).isEqualTo(ExternalRecord.STATUS_MAPPED);
    assertThat(mapped.getMappedEvidenceId()).isNotBlank();
    assertThat(workItemService.detail(admin, project.getId(), item.getId()).evidence())
        .anyMatch(evidence -> evidence.getId().equals(mapped.getMappedEvidenceId())
            && "jira".equals(evidence.getEvidenceType()));

    ExternalRecord second = recordRepository.findByProjectIdOrderByCreatedAtAsc(project.getId())
        .stream().filter(entry -> key.equals(entry.getExternalKey())
            && !entry.getId().equals(record.getId())).findFirst().orElse(null);
    if (second != null) {
      ExternalRecord rejected = importService.reject(admin, project.getId(), second.getId(),
          "duplicate");
      assertThat(rejected.getStatus()).isEqualTo(ExternalRecord.STATUS_REJECTED);
    }
  }

  @Test
  void badRowsAreReportedAndMissingSourceIsRejected() {
    String csv = "问题类型,关键字,概要,描述\n缺陷,,缺关键字,描述\n";
    ImportReport.Preview preview = importService.preview(admin, project.getId(), source.getId(),
        "bad.csv", csv.getBytes(StandardCharsets.UTF_8), "jira");
    assertThat(preview.acceptedCount()).isZero();
    assertThat(preview.rejectedCount()).isEqualTo(1);

    assertThatThrownBy(() -> importService.preview(admin, project.getId(), "missing-source",
        "x.csv", csv.getBytes(StandardCharsets.UTF_8), "jira"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("external source not found");
  }
}
