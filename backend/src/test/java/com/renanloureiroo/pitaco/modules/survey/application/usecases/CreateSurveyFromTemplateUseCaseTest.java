package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CreateSurveyUseCase — a partir de um modelo")
class CreateSurveyFromTemplateUseCaseTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private CreateSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    var applications = new InMemoryApplicationScopeGateway();
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    useCase = new CreateSurveyUseCase(applications, surveys, versions);
    applicationId = applications.anActiveApplication();
  }

  private void create(SurveyTemplate template) {
    useCase.execute(
        new CreateSurveyUseCase.Input(applicationId.value(), "Satisfação", Optional.of(template)));
  }

  @Test
  @DisplayName("O rascunho nasce com a pergunta do NPS e a pesquisa lembra do modelo")
  void nps() {
    var output =
        useCase.execute(
            new CreateSurveyUseCase.Input(
                applicationId.value(), "NPS do app", Optional.of(SurveyTemplate.NPS)));

    assertThat(output.template()).contains(SurveyTemplate.NPS);
    assertThat(surveys.findAll().getFirst().template()).contains(SurveyTemplate.NPS);

    var draft = versions.findAll().getFirst();
    assertThat(draft.isEditable()).isTrue();
    assertThat(draft.trigger()).isEmpty();
    assertThat(draft.getQuestions())
        .singleElement()
        .satisfies(
            question -> {
              assertThat(question.getType()).isEqualTo(QuestionType.NPS);
              assertThat(question.range()).contains(ScaleRange.NPS);
              assertThat(question.getLabels().min()).contains("Nada provável");
            });
  }

  @Test
  void csat_e_ces() {
    create(SurveyTemplate.CSAT);
    create(SurveyTemplate.CES);

    assertThat(versions.findAll())
        .extracting(version -> version.getQuestions().getFirst().getType())
        .containsExactlyInAnyOrder(QuestionType.RATING, QuestionType.SCALE);
  }

  @Test
  @DisplayName("Sem modelo, nasce vazio e sem modelo registrado")
  void sem_modelo() {
    var output = useCase.execute(new CreateSurveyUseCase.Input(applicationId.value(), "Em branco"));

    assertThat(output.template()).isEmpty();
    assertThat(versions.findAll().getFirst().getQuestions()).isEmpty();
  }
}
