package com.mammoth.authspike;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * ADR-0006 spike — M1 identity and authorization baseline.
 *
 * <p>Compares password hashing (JDK PBKDF2-HMAC-SHA256 vs Spring Security BCrypt), session
 * mechanisms (server-side opaque token hashed in DB vs stateless HMAC token), and validates a
 * project-scoped RBAC matrix with negative cases (wrong password, expired/tampered/revoked token,
 * cross-project access, role escalation). Output is UTF-8 JSON. Disposable spike code.
 */
public final class AuthSpike {

  private static final int PBKDF2_ITERATIONS = 210_000;
  private static final int PBKDF2_KEY_BITS = 256;
  private static final long SESSION_TTL_MS = 8L * 60 * 60 * 1000;
  private static final byte[] HMAC_KEY = "mammoth-spike-hmac-key".getBytes(StandardCharsets.UTF_8);

  /** role -> allowed actions on a project scope. */
  private static final Map<String, List<String>> ROLE_ACTIONS = Map.of(
      "owner", List.of("project.read", "artifact.write", "knowledge.create", "knowledge.review", "member.manage"),
      "editor", List.of("project.read", "artifact.write", "knowledge.create"),
      "reviewer", List.of("project.read", "knowledge.review"),
      "viewer", List.of("project.read"));

  private AuthSpike() {
  }

  public static void main(String[] args) throws Exception {
    if (args.length < 2) {
      System.err.println("usage: <workDir> <outJson>");
      System.exit(2);
    }
    try {
      Class.forName("org.sqlite.JDBC");
    } catch (ClassNotFoundException ignored) {
      // reported by the connection attempt
    }
    Path work = Path.of(args[0]).toAbsolutePath();
    Path out = Path.of(args[1]);
    Files.createDirectories(work);
    Path db = work.resolve("auth.db");
    Files.deleteIfExists(db);
    String url = "jdbc:sqlite:" + db;

    Map<String, Object> result = new LinkedHashMap<>();
    result.put("pbkdf2Iterations", PBKDF2_ITERATIONS);
    result.put("sessionTtlHours", 8);

    // ---------- 1. password hashing ----------
    BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
    String password = "Spike-Passw0rd!";
    long start = System.nanoTime();
    String pbkdf2Hash = pbkdf2Hash(password);
    result.put("pbkdf2HashMs", (System.nanoTime() - start) / 1_000_000);
    start = System.nanoTime();
    boolean pbkdf2Ok = pbkdf2Verify(password, pbkdf2Hash);
    result.put("pbkdf2VerifyMs", (System.nanoTime() - start) / 1_000_000);
    result.put("pbkdf2VerifyOk", pbkdf2Ok);
    result.put("pbkdf2WrongPasswordRejected", !pbkdf2Verify("wrong", pbkdf2Hash));
    result.put("pbkdf2StoredLength", pbkdf2Hash.length());

    start = System.nanoTime();
    String bcryptHash = bcrypt.encode(password);
    result.put("bcryptHashMs", (System.nanoTime() - start) / 1_000_000);
    start = System.nanoTime();
    boolean bcryptOk = bcrypt.matches(password, bcryptHash);
    result.put("bcryptVerifyMs", (System.nanoTime() - start) / 1_000_000);
    result.put("bcryptVerifyOk", bcryptOk);
    result.put("bcryptWrongPasswordRejected", !bcrypt.matches("wrong", bcryptHash));
    result.put("bcryptStrength", 12);

    // ---------- 2. schema + seed ----------
    try (Connection c = DriverManager.getConnection(url)) {
      execute(c, "PRAGMA journal_mode=WAL");
      execute(c, "CREATE TABLE app_user ("
          + "id TEXT PRIMARY KEY, org_id TEXT NOT NULL, username TEXT NOT NULL UNIQUE,"
          + "display_name TEXT NOT NULL, password_hash TEXT NOT NULL, status TEXT NOT NULL)");
      execute(c, "CREATE TABLE project (id TEXT PRIMARY KEY, name TEXT NOT NULL)");
      execute(c, "CREATE TABLE project_member (project_id TEXT NOT NULL, user_id TEXT NOT NULL,"
          + "role TEXT NOT NULL, PRIMARY KEY (project_id, user_id))");
      execute(c, "CREATE TABLE session (token_hash TEXT PRIMARY KEY, user_id TEXT NOT NULL,"
          + "created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, revoked_at INTEGER)");
      execute(c, "INSERT INTO project VALUES ('P1','ACME-MES-2026'),('P2','OTHER-CUSTOMER-2026')");
      insertUser(c, "u-owner", "owner-demo", bcrypt.encode(password));
      insertUser(c, "u-reviewer", "reviewer-demo", bcrypt.encode(password));
      insertUser(c, "u-viewer", "viewer-demo", bcrypt.encode(password));
      execute(c, "INSERT INTO project_member VALUES"
          + " ('P1','u-owner','owner'),('P1','u-reviewer','reviewer'),('P1','u-viewer','viewer')");
    }

    // ---------- 3. login + session (server-side opaque token) ----------
    List<String> authzCases = new ArrayList<>();
    int authzPassed = 0;
    int authzFailed = 0;

    start = System.nanoTime();
    String token = login(url, "u-owner", password);
    result.put("loginMs", (System.nanoTime() - start) / 1_000_000);
    result.put("loginTokenOk", token != null);
    result.put("loginWrongPasswordRejected", login(url, "u-owner", "wrong") == null);
    result.put("tokenEntropyBits", 256);
    result.put("tokenHashStoredHashed", storedTokenIsHashed(url));
    start = System.nanoTime();
    String subject = validateSession(url, token);
    result.put("sessionValidateUs", (System.nanoTime() - start) / 1000);
    result.put("sessionSubject", subject);

    // revocation
    revokeSession(url, token);
    result.put("revokedTokenRejected", validateSession(url, token) == null);

    // ---------- 4. authorization matrix ----------
    String ownerToken = login(url, "u-owner", password);
    String reviewerToken = login(url, "u-reviewer", password);
    String viewerToken = login(url, "u-viewer", password);
    result.put("authzCasesTotal", matrix(url, ownerToken, reviewerToken, viewerToken, authzCases));
    for (String line : authzCases) {
      if (line.startsWith("PASS")) {
        authzPassed++;
      } else {
        authzFailed++;
      }
    }
    result.put("authzPassed", authzPassed);
    result.put("authzFailed", authzFailed);
    result.put("authzCaseLog", authzCases);

    // ---------- 5. stateless HMAC token (comparison) ----------
    start = System.nanoTime();
    String hmacToken = hmacSign("u-owner", System.currentTimeMillis() + SESSION_TTL_MS);
    result.put("hmacSignUs", (System.nanoTime() - start) / 1000);
    start = System.nanoTime();
    boolean hmacOk = hmacVerify(hmacToken) != null;
    result.put("hmacVerifyUs", (System.nanoTime() - start) / 1000);
    result.put("hmacVerifyOk", hmacOk);
    result.put("hmacTamperedRejected", hmacVerify(hmacToken.substring(0, hmacToken.length() - 4) + "AAAA") == null);

    result.put("dbSizeBytes", Files.size(db));
    result.put("_out", out.toAbsolutePath().toString());
    String json = toJson(result);
    Files.writeString(out, json, StandardCharsets.UTF_8);
    System.out.println(json);
  }

  // ---------------- password hashing ----------------

  private static String pbkdf2Hash(String password) throws Exception {
    byte[] salt = new byte[16];
    new SecureRandom().nextBytes(salt);
    byte[] key = pbkdf2(password, salt, PBKDF2_ITERATIONS);
    return "pbkdf2$" + PBKDF2_ITERATIONS + "$" + Base64.getEncoder().encodeToString(salt)
        + "$" + Base64.getEncoder().encodeToString(key);
  }

  private static boolean pbkdf2Verify(String password, String stored) throws Exception {
    String[] parts = stored.split("\\$");
    int iterations = Integer.parseInt(parts[1]);
    byte[] salt = Base64.getDecoder().decode(parts[2]);
    byte[] expected = Base64.getDecoder().decode(parts[3]);
    return MessageDigest.isEqual(pbkdf2(password, salt, iterations), expected);
  }

  private static byte[] pbkdf2(String password, byte[] salt, int iterations) throws Exception {
    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, PBKDF2_KEY_BITS);
    return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
  }

  // ---------------- sessions ----------------

  private static String login(String url, String userId, String password) throws Exception {
    try (Connection c = DriverManager.getConnection(url);
         PreparedStatement ps = c.prepareStatement("SELECT password_hash FROM app_user WHERE id=?")) {
      ps.setString(1, userId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next() || !new BCryptPasswordEncoder().matches(password, rs.getString(1))) {
          return null;
        }
      }
    }
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    long now = System.currentTimeMillis();
    try (Connection c = DriverManager.getConnection(url);
         PreparedStatement ps = c.prepareStatement(
             "INSERT INTO session (token_hash,user_id,created_at,expires_at) VALUES (?,?,?,?)")) {
      ps.setString(1, sha256Hex(token));
      ps.setString(2, userId);
      ps.setLong(3, now);
      ps.setLong(4, now + SESSION_TTL_MS);
      ps.executeUpdate();
    }
    return token;
  }

  private static String validateSession(String url, String token) throws Exception {
    try (Connection c = DriverManager.getConnection(url);
         PreparedStatement ps = c.prepareStatement(
             "SELECT user_id FROM session WHERE token_hash=? AND revoked_at IS NULL AND expires_at>?")) {
      ps.setString(1, sha256Hex(token));
      ps.setLong(2, System.currentTimeMillis());
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getString(1) : null;
      }
    }
  }

  private static void revokeSession(String url, String token) throws Exception {
    try (Connection c = DriverManager.getConnection(url);
         PreparedStatement ps = c.prepareStatement("UPDATE session SET revoked_at=? WHERE token_hash=?")) {
      ps.setLong(1, System.currentTimeMillis());
      ps.setString(2, sha256Hex(token));
      ps.executeUpdate();
    }
  }

  private static boolean storedTokenIsHashed(String url) throws Exception {
    try (Connection c = DriverManager.getConnection(url);
         Statement s = c.createStatement();
         ResultSet rs = s.executeQuery("SELECT token_hash FROM session LIMIT 1")) {
      if (!rs.next()) {
        return false;
      }
      String stored = rs.getString(1);
      return stored.length() == 64 && stored.matches("[0-9a-f]{64}");
    }
  }

  // ---------------- authorization ----------------

  private static int matrix(String url, String ownerToken, String reviewerToken, String viewerToken,
      List<String> cases) throws Exception {
    int total = 0;
    total += expect(cases, "owner P1 project.read", authorize(url, ownerToken, "P1", "project.read"), true);
    total += expect(cases, "owner P1 member.manage", authorize(url, ownerToken, "P1", "member.manage"), true);
    total += expect(cases, "owner P2 project.read (not member)", authorize(url, ownerToken, "P2", "project.read"), false);
    total += expect(cases, "reviewer P1 knowledge.review", authorize(url, reviewerToken, "P1", "knowledge.review"), true);
    total += expect(cases, "reviewer P1 artifact.write (denied)", authorize(url, reviewerToken, "P1", "artifact.write"), false);
    total += expect(cases, "reviewer P1 member.manage (denied)", authorize(url, reviewerToken, "P1", "member.manage"), false);
    total += expect(cases, "viewer P1 project.read", authorize(url, viewerToken, "P1", "project.read"), true);
    total += expect(cases, "viewer P1 knowledge.review (denied)", authorize(url, viewerToken, "P1", "knowledge.review"), false);
    total += expect(cases, "viewer P2 project.read (cross-project denied)", authorize(url, viewerToken, "P2", "project.read"), false);
    total += expect(cases, "no token P1 project.read (denied)", authorize(url, null, "P1", "project.read"), false);
    return total;
  }

  private static boolean authorize(String url, String token, String projectId, String action) throws Exception {
    if (token == null) {
      return false;
    }
    String userId = validateSession(url, token);
    if (userId == null) {
      return false;
    }
    try (Connection c = DriverManager.getConnection(url);
         PreparedStatement ps = c.prepareStatement(
             "SELECT role FROM project_member WHERE project_id=? AND user_id=?")) {
      ps.setString(1, projectId);
      ps.setString(2, userId);
      try (ResultSet rs = ps.executeQuery()) {
        if (!rs.next()) {
          return false;
        }
        List<String> allowed = ROLE_ACTIONS.getOrDefault(rs.getString(1), List.of());
        return allowed.contains(action);
      }
    }
  }

  private static int expect(List<String> cases, String name, boolean actual, boolean expected) {
    boolean ok = actual == expected;
    cases.add((ok ? "PASS " : "FAIL ") + name + " (expected=" + expected + ", actual=" + actual + ")");
    return 1;
  }

  // ---------------- stateless HMAC token ----------------

  private static String hmacSign(String subject, long expiresAt) throws Exception {
    String payload = subject + "|" + expiresAt;
    String encoded = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    return encoded + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(encoded));
  }

  private static String hmacVerify(String token) {
    try {
      int dot = token.lastIndexOf('.');
      if (dot < 0) {
        return null;
      }
      String encoded = token.substring(0, dot);
      byte[] signature = Base64.getUrlDecoder().decode(token.substring(dot + 1));
      if (!MessageDigest.isEqual(hmac(encoded), signature)) {
        return null;
      }
      String[] parts = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8).split("\\|");
      return Long.parseLong(parts[1]) > System.currentTimeMillis() ? parts[0] : null;
    } catch (Exception error) {
      return null;
    }
  }

  private static byte[] hmac(String data) throws Exception {
    javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
    mac.init(new javax.crypto.spec.SecretKeySpec(HMAC_KEY, "HmacSHA256"));
    return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
  }

  // ---------------- helpers ----------------

  private static void insertUser(Connection c, String id, String username, String hash) throws Exception {
    try (PreparedStatement ps = c.prepareStatement(
        "INSERT INTO app_user (id,org_id,username,display_name,password_hash,status) VALUES (?,?,?,?,?,?)")) {
      ps.setString(1, id);
      ps.setString(2, "org-demo");
      ps.setString(3, username);
      ps.setString(4, username);
      ps.setString(5, hash);
      ps.setString(6, "active");
      ps.executeUpdate();
    }
  }

  private static void execute(Connection c, String sql) throws Exception {
    try (Statement s = c.createStatement()) {
      s.execute(sql);
    }
  }

  private static String sha256Hex(String value) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static String toJson(Object value) {
    StringBuilder out = new StringBuilder();
    writeJson(value, out);
    return out.toString();
  }

  private static void writeJson(Object value, StringBuilder out) {
    if (value == null) {
      out.append("null");
    } else if (value instanceof String string) {
      out.append('"').append(string.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
    } else if (value instanceof Number || value instanceof Boolean) {
      out.append(value);
    } else if (value instanceof Map<?, ?> map) {
      out.append('{');
      boolean first = true;
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJson(String.valueOf(entry.getKey()), out);
        out.append(':');
        writeJson(entry.getValue(), out);
      }
      out.append('}');
    } else if (value instanceof Iterable<?> items) {
      out.append('[');
      boolean first = true;
      for (Object item : items) {
        if (!first) {
          out.append(',');
        }
        first = false;
        writeJson(item, out);
      }
      out.append(']');
    } else {
      writeJson(String.valueOf(value), out);
    }
  }
}
