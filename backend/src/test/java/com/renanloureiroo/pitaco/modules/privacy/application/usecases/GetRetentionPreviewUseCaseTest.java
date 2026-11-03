package com.renanloureiroo.pitaco.modules.privacy.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.RetentionRun;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPrivacyApplicationGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryRetentionSchedule;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRetentionRunRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRetentionStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetRetentionPreviewUseCase")
class GetRetentionPreviewUseCaseTest {

  private InMemoryPrivacyApplicationGateway applications;
  private InMemoryRetentionStore store;
  private InMemoryRetentionRunRepository runs;
  private InMemoryRetentionSchedule schedule;
  private GetRetentionPreviewUseCase useCase;

  @BeforeEach
  void setUp() {
    applications = new InMemoryPrivacyApplicationGateway();
    store = new InMemoryRetentionStore();
    runs = new InMemoryRetentionRunRepository();
    schedule = new InMemoryRetentionSchedule().runningAfter(Duration.ofHours(1));
    useCase = new GetRetentionPreviewUseCase(applications, store, runs, schedule);
  }

  private void answer(ApplicationId applicationId, Instant answeredAt, Optional<String> text) {
    store.withAnswer(
        applicationId,
        SurveyId.generate(),
        SurveyVersionId.generate(),
        "d",
        QuestionKey.generate(),
        "ANSWERED",
        Optional.empty(),
        List.of(),
        text,
        answeredAt);
  }

  @Test
  @DisplayName("Sem prazo, não prevê descarte nem próxima execução")
  void sem_prazo() {
    var applicationId = applications.anApplication();

    var output = useCase.execute(new GetRetentionPreviewUseCase.Input(applicationId.value()));

    assertThat(output.configured()).isFalse();
    assertThat(output.nextRunAt()).isEmpty();
    assertThat(output.nextRun().answers()).isZero();
    assertThat(output.nextWeek().texts()).isZero();
    assertThat(output.firstDiscardPending()).isTrue();
  }

  @Test
  @DisplayName("Conta o que sai na próxima execução e o que terá saído uma semana depois")
  void preve_o_descarte() {
    var applicationId = applications.anApplicationRetaining(30, 7);
    var now = Instant.now();
    answer(applicationId, now.minus(Duration.ofDays(30)).plus(Duration.ofMinutes(30)), Optional.empty());
    answer(applicationId, now.minus(Duration.ofDays(27)), Optional.empty());
    answer(applicationId, now.minus(Duration.ofDays(3)), Optional.of("texto"));

    var output = useCase.execute(new GetRetentionPreviewUseCase.Input(applicationId.value()));

    assertThat(output.configured()).isTrue();
    assertThat(output.answerRetentionDays()).contains(30);
    assertThat(output.textRetentionDays()).contains(7);
    assertThat(output.nextRunAt()).isPresent();
    assertThat(output.nextRun().answers()).isEqualTo(1);
    assertThat(output.nextRun().texts()).isZero();
    assertThat(output.nextWeek().answers()).isEqualTo(2);
    assertThat(output.nextWeek().texts()).isEqualTo(1);
  }

  @Test
  @DisplayName("Com o descarte desligado, não há próxima execução, e a prévia usa o agora")
  void descarte_desligado() {
    schedule.disabled();
    var applicationId = applications.anApplicationRetaining(30, null);
    answer(applicationId, Instant.now().minus(Duration.ofDays(31)), Optional.empty());

    var output = useCase.execute(new GetRetentionPreviewUseCase.Input(applicationId.value()));

    assertThat(output.nextRunAt()).isEmpty();
    assertThat(output.nextRun().answers()).isEqualTo(1);
  }

  @Test
  @DisplayName("Depois do primeiro descarte, a prévia diz quando foi")
  void ultimo_descarte() {
    var applicationId = applications.anApplicationRetaining(30, null);
    var ranAt = Instant.parse("2026-09-01T03:47:00Z");
    runs.create(RetentionRun.record(applicationId, 4, 0, ranAt));

    var output = useCase.execute(new GetRetentionPreviewUseCase.Input(applicationId.value()));

    assertThat(output.lastRunAt()).contains(ranAt);
    assertThat(output.firstDiscardPending()).isFalse();
  }

  @Test
  @DisplayName("Aplicação inexistente é 404")
  void aplicacao_inexistente() {
    assertThatThrownBy(
            () -> useCase.execute(new GetRetentionPreviewUseCase.Input(UUID.randomUUID().toString())))
        .isInstanceOf(ApplicationNotFound.class);
  }
}
