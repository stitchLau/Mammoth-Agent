package com.mammoth.domain.web;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the built SPA from the Domain Service (ADR-0002: the SPA is deployed with the backend).
 *
 * <p>The path is configurable via {@code mammoth.web.dist}; when unset, common repository-relative
 * locations are probed. When no build exists (for example in backend-only tests or CI), the backend
 * simply serves the API and no static UI.
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SpaWebConfig implements WebMvcConfigurer {

  private static final Logger log = LoggerFactory.getLogger(SpaWebConfig.class);

  private final String distDirectory;

  public SpaWebConfig(@Value("${mammoth.web.dist:}") String configuredDist) {
    this.distDirectory = resolveDist(configuredDist);
    if (distDirectory == null) {
      log.info("SPA dist not found; serving API only (build apps/web to enable the UI)");
    } else {
      log.info("Serving SPA from {}", distDirectory);
    }
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    if (distDirectory == null) {
      return;
    }
    registry.addResourceHandler("/**")
        .addResourceLocations("file:" + distDirectory + "/")
        .setCachePeriod(0);
  }

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    if (distDirectory != null) {
      registry.addViewController("/").setViewName("forward:/index.html");
    }
  }

  /**
   * Resolve the SPA output directory: explicit configuration first, otherwise standard locations
   * relative to the working directory (repo root, services/domain, apps/web).
   */
  static String resolveDist(String configured) {
    List<String> candidates = configured == null || configured.isBlank()
        ? List.of("apps/web/dist", "../apps/web/dist", "../../apps/web/dist")
        : List.of(configured);
    for (String candidate : candidates) {
      Path directory = Path.of(candidate).toAbsolutePath().normalize();
      if (Files.isRegularFile(directory.resolve("index.html"))) {
        return directory.toString();
      }
    }
    return null;
  }

  /** True when the SPA has been built and will be served. */
  public boolean servingUi() {
    return distDirectory != null;
  }

  static boolean hasIndex(String directory) {
    try {
      return Files.isRegularFile(Path.of(directory).resolve("index.html"));
    } catch (RuntimeException error) {
      return false;
    }
  }
}
