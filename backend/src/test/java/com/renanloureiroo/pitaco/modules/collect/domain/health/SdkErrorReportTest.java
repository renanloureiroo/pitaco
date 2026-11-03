package com.renanloureiroo.pitaco.modules.collect.domain.health;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SdkErrorReport")
class SdkErrorReportTest {

  private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
  private static final ApplicationId APPLICATION = ApplicationId.generate();

  private static SdkErrorReport report(String message, Optional<Instant> occurredAt) {
    return SdkErrorReport.create(
        APPLICATION,
        Optional.of(SdkVersion.of("1.0.0")),
        SdkErrorKind.RENDER_ERROR,
        message,
        SdkErrorContext.empty(),
        occurredAt,
        NOW);
  }

  @Test
  @DisplayName("Mensagem longa é cortada no limite")
  void corta_mensagem() {
    assertThat(report("x".repeat(900), Optional.empty()).getMessage())
        .hasSize(SdkErrorReport.MAX_MESSAGE_LENGTH);
  }

  @Test
  @DisplayName("E-mail e número longo na mensagem são mascarados")
  void mascara_dado_pessoal() {
    var message = report("falhou para ana@exemplo.com.br tel 11987654321", Optional.empty());

    assertThat(message.getMessage())
        .isEqualTo("falhou para [email] tel [número]")
        .doesNotContain("ana@", "987654321");
  }

  @Test
  @DisplayName("Quebras de linha e controles viram espaço")
  void achata_controles() {
    assertThat(report("linha 1\n\tlinha 2", Optional.empty()).getMessage())
        .isEqualTo("linha 1 linha 2");
  }

  @Test
  @DisplayName("Sem instante informado, vale o do recebimento")
  void sem_instante() {
    var created = report("x", Optional.empty());

    assertThat(created.getOccurredAt()).isEqualTo(NOW);
    assertThat(created.getReceivedAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Instante do passado é preservado: a fila do SDK entrega depois")
  void preserva_o_passado() {
    var earlier = NOW.minus(Duration.ofHours(6));

    assertThat(report("x", Optional.of(earlier)).getOccurredAt()).isEqualTo(earlier);
  }

  @Test
  @DisplayName("Instante no futuro além da tolerância vira o do recebimento")
  void futuro_vira_recebimento() {
    var future = NOW.plus(Duration.ofHours(2));
    var slightly = NOW.plus(Duration.ofMinutes(2));

    assertThat(report("x", Optional.of(future)).getOccurredAt()).isEqualTo(NOW);
    assertThat(report("x", Optional.of(slightly)).getOccurredAt()).isEqualTo(slightly);
  }

  @Test
  @DisplayName("Mensagem ausente vira texto vazio")
  void mensagem_ausente() {
    assertThat(report(null, Optional.empty()).getMessage()).isEmpty();
  }
}
