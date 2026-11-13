package com.mammoth.domain.delivery;

import com.mammoth.domain.artifact.ArtifactRecord;
import com.mammoth.domain.artifact.ArtifactService;
import com.mammoth.domain.persistence.AppUser;
import com.mammoth.domain.service.AuditService;
import com.mammoth.domain.support.JsonLite;
import com.mammoth.domain.web.ApiException;
import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BG-01: portable handoff package (zip) for an external coding agent or customer review.
 *
 * <p>The package contains the approved solution renders, tasks, acceptance criteria, baseline,
 * full handoff Markdown, README and SECURITY notes plus a manifest with per-file sha256. It never
 * contains credentials or source code. The zip is stored as an immutable content-addressed artifact
 * and the export is audited.
 */
@Service
public class HandoffPackageService {

  private static final List<Pattern> SECRET_PATTERNS = List.of(
      Pattern.compile("(?i)(password|passwd|pwd)\\s*[=:]\\s*\\S+"),
      Pattern.compile("(?i)(token|secret|api[_-]?key|access[_-]?key)\\s*[=:]\\s*\\S+"),
      Pattern.compile("-----BEGIN (RSA |EC |OPENSSH |PGP )?PRIVATE KEY-----"),
      Pattern.compile("(?i)jdbc:[a-z0-9]+://\\S+"),
      Pattern.compile("(?i)bearer\\s+[A-Za-z0-9._-]{20,}"));

  private final DeliveryService deliveryService;
  private final ArtifactService artifactService;
  private final AuditService auditService;

  public HandoffPackageService(DeliveryService deliveryService, ArtifactService artifactService,
      AuditService auditService) {
    this.deliveryService = deliveryService;
    this.artifactService = artifactService;
    this.auditService = auditService;
  }

  public record PackageView(String artifactId, String originalName, String sha256, long sizeBytes,
      String downloadUrl, String generatedAt, String solutionVersionId, int solutionVersion,
      String baselineId) {
  }

  @Transactional
  public PackageView export(AppUser actor, String projectId, String workItemId) {
    DeliveryService.Generated content = deliveryService.handoff(actor, projectId, workItemId);
    Map<String, byte[]> files = new LinkedHashMap<>();
    files.put("solution-business.md", utf8(content.businessMarkdown()));
    files.put("solution-technical.md", utf8(content.technicalMarkdown()));
    files.put("tasks.md", utf8(content.tasksMarkdown()));
    files.put("acceptance.md", utf8(content.acceptanceMarkdown()));
    files.put("baseline.md", utf8(content.baselineMarkdown()));
    files.put("handoff.md", utf8(content.markdown()));
    files.put("README.md", utf8(readme(content)));
    files.put("SECURITY.md", utf8(security()));

    for (Map.Entry<String, byte[]> entry : files.entrySet()) {
      scanForSecrets(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
    }
    byte[] manifest = manifest(content, files);
    scanForSecrets("manifest.properties", new String(manifest, StandardCharsets.UTF_8));
    byte[] zip = zip(files, manifest);
    String fileName = safeFileName("handoff-" + content.itemNo() + "-v" + content.solutionVersion())
        + ".zip";
    ArtifactRecord record = artifactService.upload(actor, projectId, "handoff_package", fileName,
        "application/zip", "BG-01 handoff package for " + content.itemNo(), zip);
    auditService.record(actor.getId(), "handoff.export", "work_item", workItemId,
        JsonLite.write(JsonLite.object(
            "artifactId", record.getId(),
            "sha256", record.getContentHash(),
            "solutionVersionId", content.solutionVersionId(),
            "baselineId", content.baselineId(),
            "fileCount", files.size())));
    return new PackageView(record.getId(), record.getOriginalName(), record.getContentHash(),
        record.getSizeBytes(), "/api/projects/" + projectId + "/artifacts/" + record.getId()
            + "/content", content.generatedAt(), content.solutionVersionId(),
        content.solutionVersion(), content.baselineId());
  }

  private static byte[] manifest(DeliveryService.Generated content, Map<String, byte[]> files) {
    Properties properties = new Properties();
    properties.setProperty("formatVersion", "1");
    properties.setProperty("generatedAt", content.generatedAt());
    properties.setProperty("workItemId", content.workItemId());
    properties.setProperty("itemNo", content.itemNo());
    properties.setProperty("itemTitle", content.itemTitle());
    properties.setProperty("solutionVersionId", content.solutionVersionId());
    properties.setProperty("solutionVersion", String.valueOf(content.solutionVersion()));
    properties.setProperty("solutionTitle", content.solutionTitle());
    properties.setProperty("baselineId", content.baselineId() == null ? "" : content.baselineId());
    properties.setProperty("contextSnapshotId",
        content.contextSnapshotId() == null ? "" : content.contextSnapshotId());
    properties.setProperty("fileCount", String.valueOf(files.size()));
    int index = 0;
    for (Map.Entry<String, byte[]> entry : files.entrySet()) {
      properties.setProperty("file." + index + ".name", entry.getKey());
      properties.setProperty("file." + index + ".sha256", sha256(entry.getValue()));
      index++;
    }
    StringWriter writer = new StringWriter();
    try {
      properties.store(writer, "Mammoth handoff package manifest");
    } catch (Exception error) {
      throw new IllegalStateException("cannot write handoff manifest", error);
    }
    return utf8(writer.toString());
  }

  private static byte[] zip(Map<String, byte[]> files, byte[] manifest) {
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
         ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
      for (Map.Entry<String, byte[]> entry : files.entrySet()) {
        ZipEntry zipEntry = new ZipEntry(entry.getKey());
        zipEntry.setTime(0);
        zip.putNextEntry(zipEntry);
        zip.write(entry.getValue());
        zip.closeEntry();
      }
      ZipEntry manifestEntry = new ZipEntry("manifest.properties");
      manifestEntry.setTime(0);
      zip.putNextEntry(manifestEntry);
      zip.write(manifest);
      zip.closeEntry();
      zip.finish();
      return bytes.toByteArray();
    } catch (Exception error) {
      throw new IllegalStateException("cannot build handoff zip", error);
    }
  }

  private static void scanForSecrets(String fileName, String text) {
    for (Pattern pattern : SECRET_PATTERNS) {
      if (pattern.matcher(text).find()) {
        throw ApiException.conflict(
            "handoff package rejected: possible secret material in " + fileName);
      }
    }
  }

  private static String readme(DeliveryService.Generated content) {
    return "# Mammoth 开发交接包\n\n"
        + "- 需求：" + content.itemNo() + " " + content.itemTitle() + "\n"
        + "- 批准方案：v" + content.solutionVersion() + " " + content.solutionTitle() + "\n"
        + "- 代码基线：" + (content.baselineId() == null ? "未捕获" : content.baselineId()) + "\n"
        + "- 上下文快照：" + (content.contextSnapshotId() == null ? "无" : content.contextSnapshotId())
        + "\n\n"
        + "## 文件\n\n"
        + "- `solution-business.md` / `solution-technical.md`：批准方案的业务/技术视图\n"
        + "- `tasks.md`：开发任务与完成条件\n"
        + "- `acceptance.md`：验收标准\n"
        + "- `baseline.md`：代码基线（仓库/commit/branch）\n"
        + "- `handoff.md`：完整交接 Markdown\n"
        + "- `manifest.properties`：元数据与每个文件的 sha256\n\n"
        + "## 校验\n\n"
        + "按 `manifest.properties` 中 `file.N.name` / `file.N.sha256` 对每个文件计算 SHA-256；\n"
        + "不一致时不要使用该包。\n\n"
        + "## 约束\n\n"
        + "已批准方案不可修改；Mammoth 不托管代码，实施结果以 commit 为准。\n"
        + "本包不包含生产凭据、密钥、源码正文或数据库连接串。\n";
  }

  private static String security() {
    return "# 安全说明\n\n"
        + "- 本包是 Confidential 资料，只交给已授权的实施方/评审人；不要提交到公共仓库、\n"
        + "  公开网盘或未经批准的外部群。\n"
        + "- 本包不包含生产凭据、API token、SMTP/企微密钥、数据库连接串或客户源码正文。\n"
        + "- 外部 Agent 只能依据本包和只读 MCP 工具工作；不得写回 Mammoth 或生产系统。\n"
        + "- 如发现包内出现疑似凭据，请停止使用并报告项目负责人。\n";
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(
          MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception error) {
      throw new IllegalStateException("SHA-256 unavailable", error);
    }
  }

  private static String safeFileName(String value) {
    return value.replaceAll("[^A-Za-z0-9._-]", "_");
  }
}
