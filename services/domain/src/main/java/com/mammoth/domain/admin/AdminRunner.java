package com.mammoth.domain.admin;

import com.mammoth.domain.DomainApplication;
import com.mammoth.domain.support.JsonLite;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Headless administration entrypoint for ADR-0008 export/import and backup/restore:
 *
 * <pre>
 * java -jar mammoth-domain-service.jar --admin-export[=zipPath]
 * java -jar mammoth-domain-service.jar --admin-import=zipPath --admin-username=NAME [--admin-password=PW]
 * java -jar mammoth-domain-service.jar --admin-backup=dir
 * java -jar mammoth-domain-service.jar --admin-restore=backupDir --restore-to=emptyDir
 * </pre>
 *
 * <p>The import password should preferably come from {@code MAMMOTH_IMPORT_ADMIN_PASSWORD}; a
 * {@code --admin-password} argument is visible in the local process list. Admin arguments are
 * filtered out before the Spring environment is built so secrets are not exposed as properties.
 * Restore is a pure file operation and does not start the Spring context (it must not initialize
 * the target data root).
 */
public final class AdminRunner {

  static final String ADMIN_PASSWORD_ENV = "MAMMOTH_IMPORT_ADMIN_PASSWORD";

  private AdminRunner() {
  }

  public static boolean isAdminInvocation(String[] args) {
    for (String arg : args) {
      if (arg.startsWith("--admin-") || arg.startsWith("--restore-to=")) {
        return true;
      }
    }
    return false;
  }

  public static void run(String[] args) {
    Map<String, String> options = parse(args);
    try {
      if (options.containsKey("admin-restore")) {
        RestoreService restore = new RestoreService();
        RestoreService.Result result = restore.restore(
            Path.of(options.get("admin-restore")), Path.of(require(options, "restore-to")));
        print("restore", describe(result));
        return;
      }
      List<String> springArgs = new ArrayList<>();
      for (String arg : args) {
        if (!arg.startsWith("--admin-") && !arg.startsWith("--restore-to=")) {
          springArgs.add(arg);
        }
      }
      try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
          DomainApplication.class)
          .web(WebApplicationType.NONE)
          .properties("mammoth.bootstrap.default-organization=false")
          .run(springArgs.toArray(String[]::new))) {
        Object result;
        if (options.containsKey("admin-export")) {
          ExportService service = context.getBean(ExportService.class);
          String target = blankToNull(options.get("admin-export"));
          result = service.exportPackage(
              target == null ? service.defaultExportPath() : Path.of(target),
              blankToNull(options.get("actor-user-id")));
        } else if (options.containsKey("admin-import")) {
          ImportService service = context.getBean(ImportService.class);
          String password = System.getenv(ADMIN_PASSWORD_ENV);
          if (password == null || password.isBlank()) {
            password = options.get("admin-password");
          }
          if (password == null || password.isBlank()) {
            throw new IllegalArgumentException(
                "import requires " + ADMIN_PASSWORD_ENV
                    + " (preferred) or --admin-password; the target admin password cannot be empty");
          }
          result = service.importPackage(Path.of(options.get("admin-import")),
              options.get("admin-username"), password);
        } else if (options.containsKey("admin-backup")) {
          BackupService service = context.getBean(BackupService.class);
          result = service.backup(Path.of(require(options, "admin-backup")));
        } else {
          throw new IllegalArgumentException("no admin action supplied");
        }
        print(actionName(options), describe(result));
      }
    } catch (RuntimeException error) {
      System.err.println("admin command failed: " + rootMessage(error));
      System.exit(1);
    }
  }

  private static void print(String action, Object result) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("admin", action);
    payload.put("result", result == null ? "ok" : result);
    System.out.println(JsonLite.write(payload));
  }

  private static String actionName(Map<String, String> options) {
    if (options.containsKey("admin-export")) {
      return "export";
    }
    if (options.containsKey("admin-import")) {
      return "import";
    }
    return "backup";
  }

  private static Object describe(Object result) {
    if (result instanceof ExportService.Result export) {
      return JsonLite.object(
          "target", export.zip().toString(),
          "tables", export.tables(),
          "rows", export.rows(),
          "artifacts", export.artifacts(),
          "sizeBytes", export.sizeBytes());
    }
    if (result instanceof ImportService.Result imported) {
      return JsonLite.object(
          "tables", imported.tables(),
          "rows", imported.rows(),
          "artifacts", imported.artifacts(),
          "adminUsername", imported.adminUsername());
    }
    if (result instanceof BackupService.Result backup) {
      return JsonLite.object(
          "directory", backup.directory().toString(),
          "databaseBytes", backup.databaseBytes(),
          "artifacts", backup.artifacts(),
          "schemaVersion", backup.schemaVersion());
    }
    if (result instanceof RestoreService.Result restore) {
      return JsonLite.object(
          "targetRoot", restore.targetRoot().toString(),
          "databaseBytes", restore.databaseBytes(),
          "artifacts", restore.artifacts());
    }
    return String.valueOf(result);
  }

  private static Map<String, String> parse(String[] args) {
    Map<String, String> options = new LinkedHashMap<>();
    for (String arg : args) {
      if (!arg.startsWith("--")) {
        continue;
      }
      String body = arg.substring(2);
      int equals = body.indexOf('=');
      if (equals < 0) {
        options.put(body, "");
      } else {
        options.put(body.substring(0, equals), body.substring(equals + 1));
      }
    }
    return options;
  }

  private static String require(Map<String, String> options, String key) {
    String value = blankToNull(options.get(key));
    if (value == null) {
      throw new IllegalArgumentException("--" + key + " is required");
    }
    return value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  private static String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }
}
