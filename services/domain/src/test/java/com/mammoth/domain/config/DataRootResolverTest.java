package com.mammoth.domain.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DataRootResolverTest {

  @Test
  void defaultRootIsOutsideTheSourceCheckout() {
    Path root = DataRootResolver.resolve("");
    assertThat(root.isAbsolute()).isTrue();
    assertThat(root.toString().toLowerCase()).doesNotContain(".dsh");
  }

  @Test
  void explicitRootOutsideTheCheckoutIsAccepted() {
    String outside = Path.of(System.getProperty("java.io.tmpdir"), "mammoth-test-data").toString();
    Path root = DataRootResolver.resolve(outside);
    assertThat(root.isAbsolute()).isTrue();
    assertThat(root.toString()).contains("mammoth-test-data");
  }

  @Test
  void dshHomeIsRejected() {
    String dshPath = Path.of(System.getProperty("user.home"), ".dsh", "mammoth").toString();
    assertThatThrownBy(() -> DataRootResolver.resolve(dshPath))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(".dsh");
  }

  @Test
  void sourceCheckoutIsRejected() {
    Path checkout = Path.of("").toAbsolutePath();
    assertThatThrownBy(() -> DataRootResolver.resolve(checkout.resolve("data").toString()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("source checkout");
  }
}
