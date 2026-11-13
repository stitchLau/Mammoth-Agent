package com.mammoth.domain.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mammoth.domain.artifact.ArtifactStore;
import com.mammoth.domain.artifact.LocalArtifactStore;
import com.mammoth.domain.config.DataRootBootstrap;
import com.mammoth.domain.config.DataRootResolver;
import com.mammoth.domain.web.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteDataSource;

/** ADR-0008 acceptance: export/import package and backup/restore drill at the JDBC level. */
class AdminPackageTest {

  @TempDir
  Path tempDir;

  private record Fixture(Path root, SQLiteDataSource dataSource, ArtifactStore store,
      DataRootBootstrap bootstrap) {
  }

  private Fixture initRoot(String name) throws Exception {
    Path root = tempDir.resolve(name);
    DataRootResolver resolver = new DataRootResolver(root.toString());
    DataRootBootstrap bootstrap = new DataRootBootstrap(resolver);
    bootstrap.initialize();
    String url = "jdbc:sqlite:" + root.resolve("db").resolve("mammoth.db");
    Flyway.configure()
        .dataSource(url, null, null)
        .locations("classpath:db/migration")
        .load()
        .migrate();
    SQLiteConfig config = new SQLiteConfig();
    config.setJournalMode(SQLiteConfig.JournalMode.WAL);
    config.enforceForeignKeys(true);
    config.setBusyTimeout(5_000);
    SQLiteDataSource dataSource = new SQLiteDataSource(config);
    dataSource.setUrl(url);
    return new Fixture(root, dataSource, new LocalArtifactStore(resolver), bootstrap);
  }

  private byte[] seed(Fixture fixture) throws Exception {
    byte[] artifactBytes = "合成资料：扫码重复入库处理".getBytes(StandardCharsets.UTF_8);
    ArtifactStore.StoredArtifact stored = fixture.store().put(artifactBytes);
    try (Connection connection = fixture.dataSource().getConnection();
         Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "INSERT INTO organization(id, name, created_at) VALUES('org-default','Test Org',"
              + "'2026-11-13T00:00:00Z')");
      statement.executeUpdate(
          "INSERT INTO app_user(id, org_id, username, display_name, password_hash, status,"
              + " created_at) VALUES('user-owner','org-default','owner','Owner','real-hash',"
              + "'active','2026-11-13T00:00:00Z')");
      statement.executeUpdate(
          "INSERT INTO app_user(id, org_id, username, display_name, password_hash, status,"
              + " created_at) VALUES('user-other','org-default','other','Other','other-hash',"
              + "'active','2026-11-13T00:00:00Z')");
      statement.executeUpdate(
          "INSERT INTO session(token_hash, user_id, created_at, expires_at)"
              + " VALUES('session-token','user-owner',1,2)");
      statement.executeUpdate(
          "INSERT INTO customer(id, org_id, name, status, created_at)"
              + " VALUES('customer-1','org-default','ACME','active','2026-11-13T00:00:00Z')");
      statement.executeUpdate(
          "INSERT INTO project(id, org_id, customer_id, code, name, status, owner_user_id,"
              + " created_at) VALUES('project-1','org-default','customer-1','DEMO','Demo project',"
              + "'active','user-owner','2026-11-13T00:00:00Z')");
      statement.executeUpdate(
          "INSERT INTO artifact(id, project_id, kind, original_name, content_hash, storage_path,"
              + " size_bytes, mime, source_note, uploaded_by, created_at) VALUES('artifact-1',"
              + "'project-1','source','notes.md','" + stored.sha256() + "','"
              + stored.relativePath().replace("\\", "/") + "'," + stored.sizeBytes()
              + ",'text/markdown','demo','user-owner','2026-11-13T00:00:00Z')");
      statement.executeUpdate(
          "INSERT INTO knowledge_document(id, scope_type, scope_id, doc_type, title, body, status,"
              + " version, created_by, created_at, updated_at) VALUES('knowledge-1','project',"
              + "'project-1','process','收货流程','条码重复时冻结库存。','draft',1,'user-owner',"
              + "'2026-11-13T00:00:00Z','2026-11-13T00:00:00Z')");
      statement.executeUpdate(
          "INSERT INTO audit_log(id, actor_user_id, action, object_type, object_id, at,"
              + " detail_json) VALUES('audit-1','user-owner','project.create','project',"
              + "'project-1','2026-11-13T00:00:00Z','{}')");
    }
    return artifactBytes;
  }

  @Test
  void exportImportRoundTripKeepsDataAndLocksPasswords() throws Exception {
    Fixture source = initRoot("source");
    byte[] artifactBytes = seed(source);
    Path zip = tempDir.resolve("export.zip");

    ExportService.Result exported = new ExportService(source.dataSource(), source.bootstrap(),
        source.store()).exportPackage(zip, "user-owner");
    assertThat(zip).exists();
    assertThat(exported.tables()).isEqualTo(AdminTables.TABLES.size());
    assertThat(exported.artifacts()).isEqualTo(1);

    try (ZipFile archive = new ZipFile(zip.toFile(), StandardCharsets.UTF_8)) {
      Properties manifest = new Properties();
      try (InputStream input = archive.getInputStream(archive.getEntry("manifest.properties"))) {
        manifest.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
      }
      assertThat(manifest.getProperty("formatVersion")).isEqualTo("1");
      assertThat(manifest.getProperty("schemaVersion")).isEqualTo("6");
      String appUsers = new String(
          archive.getInputStream(archive.getEntry("data/app_user.sql")).readAllBytes(),
          StandardCharsets.UTF_8);
      assertThat(appUsers).contains(AdminTables.LOCKED_PASSWORD);
      assertThat(appUsers).doesNotContain("real-hash");
      assertThat(archive.getEntry("data/session.sql")).isNull();
      assertThat(archive.getEntry("artifacts/" + ExportService.sha256(artifactBytes))).isNotNull();
    }

    Fixture target = initRoot("target");
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    ImportService.Result imported = new ImportService(target.dataSource(), target.store(), encoder)
        .importPackage(zip, "owner", "ImportedPass!2026");
    assertThat(imported.artifacts()).isEqualTo(1);
    assertThat(imported.rows()).isPositive();

    try (Connection connection = target.dataSource().getConnection();
         Statement statement = connection.createStatement()) {
      assertThat(count(statement, "app_user")).isEqualTo(2);
      assertThat(count(statement, "project")).isEqualTo(1);
      assertThat(count(statement, "knowledge_document")).isEqualTo(1);
      assertThat(count(statement, "artifact")).isEqualTo(1);
      assertThat(count(statement, "session")).isZero();
      assertThat(count(statement, "audit_log")).isGreaterThanOrEqualTo(2);
      try (ResultSet resultSet = statement.executeQuery(
          "SELECT password_hash FROM app_user WHERE id = 'user-owner'")) {
        assertThat(resultSet.next()).isTrue();
        assertThat(encoder.matches("ImportedPass!2026", resultSet.getString(1))).isTrue();
      }
      try (ResultSet resultSet = statement.executeQuery(
          "SELECT password_hash FROM app_user WHERE id = 'user-other'")) {
        assertThat(resultSet.next()).isTrue();
        assertThat(resultSet.getString(1)).isEqualTo(AdminTables.LOCKED_PASSWORD);
      }
      try (ResultSet resultSet = statement.executeQuery(
          "SELECT action FROM audit_log WHERE action = 'admin.import'")) {
        assertThat(resultSet.next()).isTrue();
      }
    }
    assertThat(target.store().read(ExportService.sha256(artifactBytes)))
        .isEqualTo(artifactBytes);

    assertThatThrownBy(() -> new ImportService(target.dataSource(), target.store(), encoder)
        .importPackage(zip, "owner", "AnotherPass!2026"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("not empty");
  }

  @Test
  void tamperedPackageAndSchemaMismatchAreRejected() throws Exception {
    Fixture source = initRoot("source2");
    seed(source);
    Path zip = tempDir.resolve("export2.zip");
    new ExportService(source.dataSource(), source.bootstrap(), source.store())
        .exportPackage(zip, null);

    Path tampered = tempDir.resolve("tampered.zip");
    try (ZipFile archive = new ZipFile(zip.toFile(), StandardCharsets.UTF_8);
         ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(tampered))) {
      var entries = archive.entries();
      while (entries.hasMoreElements()) {
        ZipEntry entry = entries.nextElement();
        byte[] bytes = archive.getInputStream(entry).readAllBytes();
        if (entry.getName().equals("data/project.sql")) {
          bytes = new String(bytes, StandardCharsets.UTF_8).replace("Demo project", "Hacked")
              .getBytes(StandardCharsets.UTF_8);
        }
        output.putNextEntry(new ZipEntry(entry.getName()));
        output.write(bytes);
        output.closeEntry();
      }
    }
    Fixture target = initRoot("target2");
    BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    assertThatThrownBy(() -> new ImportService(target.dataSource(), target.store(), encoder)
        .importPackage(tampered, "owner", "ImportedPass!2026"))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("checksum mismatch");
  }

  @Test
  void backupAndRestoreRestoresDatabaseAndArtifacts() throws Exception {
    Fixture source = initRoot("backup-source");
    byte[] artifactBytes = seed(source);

    BackupService.Result backup = new BackupService(source.bootstrap(), source.dataSource(),
        source.store()).backup(tempDir.resolve("backups"));
    assertThat(backup.directory()).exists();
    assertThat(backup.schemaVersion()).isEqualTo(6);
    assertThat(backup.artifacts()).isEqualTo(1);
    Path backupDb = backup.directory().resolve("db").resolve("mammoth.db");
    assertThat(backupDb).exists();
    assertThat(BackupService.sha256File(backupDb))
        .isEqualTo(manifest(backup.directory()).getProperty("databaseSha256"));

    Path restoredRoot = tempDir.resolve("restored");
    RestoreService.Result restored = new RestoreService()
        .restore(backup.directory(), restoredRoot);
    assertThat(restored.targetRoot()).isEqualTo(restoredRoot.toAbsolutePath().normalize());
    assertThat(restored.artifacts()).isEqualTo(1);
    assertThat(restoredRoot.resolve("restore.properties")).exists();

    try (Connection connection = DriverManager.getConnection(
        "jdbc:sqlite:" + restoredRoot.resolve("db").resolve("mammoth.db"));
         Statement statement = connection.createStatement()) {
      assertThat(count(statement, "project")).isEqualTo(1);
      assertThat(count(statement, "knowledge_document")).isEqualTo(1);
    }
    Path restoredArtifact = restoredRoot.resolve("artifacts")
        .resolve(ExportService.sha256(artifactBytes).substring(0, 2))
        .resolve(ExportService.sha256(artifactBytes).substring(2, 4))
        .resolve(ExportService.sha256(artifactBytes));
    assertThat(restoredArtifact).exists();
    assertThat(Files.readAllBytes(restoredArtifact)).isEqualTo(artifactBytes);

    assertThatThrownBy(() -> new RestoreService().restore(backup.directory(), restoredRoot))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("empty directory");
  }

  @Test
  void tamperedBackupIsRejected() throws Exception {
    Fixture source = initRoot("backup-source2");
    seed(source);
    BackupService.Result backup = new BackupService(source.bootstrap(), source.dataSource(),
        source.store()).backup(tempDir.resolve("backups2"));
    Path artifactFile;
    try (var stream = Files.walk(backup.directory().resolve("artifacts"))) {
      artifactFile = stream.filter(Files::isRegularFile).findFirst().orElseThrow();
    }
    Files.writeString(artifactFile, "tampered", StandardCharsets.UTF_8);

    assertThatThrownBy(() -> new RestoreService()
        .restore(backup.directory(), tempDir.resolve("restored2")))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("checksum mismatch");
  }

  private static long count(Statement statement, String table) throws Exception {
    try (ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM \"" + table + "\"")) {
      return resultSet.next() ? resultSet.getLong(1) : 0;
    }
  }

  private static Properties manifest(Path backupDirectory) throws IOException {
    Properties properties = new Properties();
    try (InputStream input = Files.newInputStream(backupDirectory.resolve("manifest.properties"))) {
      properties.load(new java.io.InputStreamReader(input, StandardCharsets.UTF_8));
    }
    return properties;
  }
}
