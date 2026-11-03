package com.renanloureiroo.pitaco.core.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("SdkVersion")
class SdkVersionTest {

  @Test
  @DisplayName("Lê semver com pré-lançamento e descarta o metadado de build")
  void le_semver() {
    var version = SdkVersion.parse(" 1.4.2-beta.1+sha.9f ").orElseThrow();

    assertThat(version.major()).isEqualTo(1);
    assertThat(version.minor()).isEqualTo(4);
    assertThat(version.patch()).isEqualTo(2);
    assertThat(version.preRelease()).contains("beta.1");
    assertThat(version.value()).isEqualTo("1.4.2-beta.1");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(
      strings = {"1.0", "v1.0.0", "01.0.0", "1.0.0-", "1.0.0-ção", "abc", "1.0.0.0", "1.2.3 4"})
  @DisplayName("Fora do formato é ausência, nunca erro")
  void fora_do_formato(String raw) {
    assertThat(SdkVersion.parse(raw)).isEmpty();
  }

  @Test
  @DisplayName("Texto acima do limite é ausência")
  void acima_do_limite() {
    assertThat(SdkVersion.parse("1.0.0-" + "a".repeat(40))).isEmpty();
  }

  @Test
  @DisplayName("A forma estrita recusa com code estável")
  void forma_estrita() {
    assertThatThrownBy(() -> SdkVersion.of("1.0"))
        .isInstanceOfSatisfying(
            DomainException.class,
            error -> assertThat(error.code()).isEqualTo("sdk_version.invalid"));
  }

  @Test
  @DisplayName("Ordena como o semver: número, depois pré-lançamento antes do lançamento")
  void ordena_como_semver() {
    var ordered =
        List.of(
            "1.0.0-alpha",
            "1.0.0-alpha.1",
            "1.0.0-alpha.beta",
            "1.0.0-beta",
            "1.0.0-beta.2",
            "1.0.0-beta.11",
            "1.0.0-rc.1",
            "1.0.0",
            "1.0.1",
            "1.2.0",
            "1.10.0",
            "2.0.0");

    var parsed = ordered.stream().map(SdkVersion::of).toList();
    var sorted = parsed.stream().sorted().toList();

    assertThat(sorted).containsExactlyElementsOf(parsed);
  }

  @Test
  @DisplayName("Versão igual com e sem metadado de build é a mesma")
  void metadado_nao_distingue() {
    assertThat(SdkVersion.of("1.2.3+abc")).isEqualTo(SdkVersion.of("1.2.3"));
    assertThat(SdkVersion.of("1.2.3+abc")).isEqualByComparingTo(SdkVersion.of("1.2.3"));
  }

  @Test
  @DisplayName("isAtLeast compara pela ordem semver")
  void is_at_least() {
    assertThat(SdkVersion.of("1.2.0").isAtLeast(SdkVersion.of("1.1.9"))).isTrue();
    assertThat(SdkVersion.of("1.2.0").isAtLeast(SdkVersion.of("1.2.0"))).isTrue();
    assertThat(SdkVersion.of("1.2.0-rc.1").isAtLeast(SdkVersion.of("1.2.0"))).isFalse();
  }

  @Test
  @DisplayName("Pré-lançamento ausente vira Optional vazio")
  void pre_lancamento_ausente() {
    assertThat(new SdkVersion(1, 0, 0, null).preRelease()).isEqualTo(Optional.empty());
  }
}
