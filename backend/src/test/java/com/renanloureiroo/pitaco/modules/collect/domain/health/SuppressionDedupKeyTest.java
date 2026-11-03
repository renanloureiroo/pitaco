package com.renanloureiroo.pitaco.modules.collect.domain.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SuppressionDedupKey")
class SuppressionDedupKeyTest {

  private static final ApplicationId APPLICATION = ApplicationId.generate();

  @Test
  @DisplayName("A mesma identidade na mesma aplicação dá a mesma chave, sem o valor em claro")
  void estavel_e_opaca() {
    var identity = new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, "u-8f1c");

    var first = SuppressionDedupKey.of(APPLICATION, identity);
    var second = SuppressionDedupKey.of(APPLICATION, identity);

    assertThat(first).isEqualTo(second);
    assertThat(first.value()).hasSize(64).doesNotContain("u-8f1c");
  }

  @Test
  @DisplayName("Aplicação, tipo e valor diferentes dão chaves diferentes")
  void distingue() {
    var reference = new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, "abc");
    var device = new RespondentIdentity(RespondentIdentityKind.DEVICE, "abc");

    assertThat(SuppressionDedupKey.of(APPLICATION, reference))
        .isNotEqualTo(SuppressionDedupKey.of(APPLICATION, device))
        .isNotEqualTo(SuppressionDedupKey.of(ApplicationId.generate(), reference));
  }
}
