package com.renanloureiroo.pitaco.modules.privacy.domain.retention;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RetentionPolicy")
class RetentionPolicyTest {

  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
  private static final ApplicationId APPLICATION = ApplicationId.generate();

  @Test
  @DisplayName("Sem prazo nenhum, não está configurada e nada vence")
  void sem_prazo() {
    var policy = RetentionPolicy.of(APPLICATION, Optional.empty(), Optional.empty());

    assertThat(policy.isConfigured()).isFalse();
    assertThat(policy.answersBefore(NOW)).isEmpty();
    assertThat(policy.textsBefore(NOW)).isEmpty();
  }

  @Test
  @DisplayName("Sem prazo próprio, o texto livre segue o prazo geral")
  void texto_segue_o_geral() {
    var policy = RetentionPolicy.of(APPLICATION, Optional.of(30), Optional.empty());

    assertThat(policy.answersBefore(NOW)).contains(Instant.parse("2026-08-13T12:00:00Z"));
    assertThat(policy.textsBefore(NOW)).contains(Instant.parse("2026-08-13T12:00:00Z"));
  }

  @Test
  @DisplayName("Só o prazo de texto: o texto vence e a resposta fica")
  void so_texto() {
    var policy = RetentionPolicy.of(APPLICATION, Optional.empty(), Optional.of(7));

    assertThat(policy.isConfigured()).isTrue();
    assertThat(policy.answersBefore(NOW)).isEmpty();
    assertThat(policy.textsBefore(NOW)).contains(Instant.parse("2026-09-05T12:00:00Z"));
  }
}
