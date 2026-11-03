package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SurveyNotFoundInApplication;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemorySurveyQuotaGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemorySurveyScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetQuotaProgressUseCase")
class GetQuotaProgressUseCaseTest {

  private static final Instant AT = Instant.parse("2026-09-10T10:00:00Z");

  private final InMemorySurveyScopeGateway surveys = new InMemorySurveyScopeGateway();
  private final InMemorySurveyQuotaGateway quotas = new InMemorySurveyQuotaGateway();
  private final InMemorySurveyDisplayRepository displays = new InMemorySurveyDisplayRepository();

  private GetQuotaProgressUseCase useCase;
  private ApplicationId applicationId;
  private SurveyId surveyId;

  @BeforeEach
  void setUp() {
    useCase = new GetQuotaProgressUseCase(surveys, quotas, displays);
    applicationId = ApplicationId.generate();
    surveyId = surveys.aSurveyIn(applicationId);
  }

  private GetQuotaProgressUseCase.Output progress(SurveyId survey) {
    return useCase.execute(
        new GetQuotaProgressUseCase.Input(applicationId.value(), survey.value()));
  }

  @Test
  @DisplayName("Conta só as concluídas, ao lado da cota")
  void conta_as_concluidas() {
    quotas.withQuota(surveyId, 10);
    SurveyDisplayFactory.aDisplay().forSurvey(surveyId).completedAt(AT).buildSavedIn(displays);
    SurveyDisplayFactory.aDisplay().forSurvey(surveyId).completedAt(AT).buildSavedIn(displays);
    SurveyDisplayFactory.aDisplay().forSurvey(surveyId).dismissedAt(AT).buildSavedIn(displays);
    SurveyDisplayFactory.aDisplay().forSurvey(surveyId).buildSavedIn(displays);
    SurveyDisplayFactory.aDisplay()
        .forSurvey(SurveyId.generate())
        .completedAt(AT)
        .buildSavedIn(displays);

    var output = progress(surveyId);

    assertThat(output.responseQuota()).contains(10);
    assertThat(output.completedResponses()).isEqualTo(2);
  }

  @Test
  @DisplayName("Sem cota, a cota vem ausente e a contagem continua valendo")
  void sem_cota() {
    SurveyDisplayFactory.aDisplay().forSurvey(surveyId).completedAt(AT).buildSavedIn(displays);

    var output = progress(surveyId);

    assertThat(output.responseQuota()).isEmpty();
    assertThat(output.completedResponses()).isEqualTo(1);
  }

  @Test
  @DisplayName("Pesquisa de outra aplicação é não encontrada")
  void fora_do_escopo() {
    var deOutra = surveys.aSurveyIn(ApplicationId.generate());

    assertThatThrownBy(() -> progress(deOutra)).isInstanceOf(SurveyNotFoundInApplication.class);
  }
}
