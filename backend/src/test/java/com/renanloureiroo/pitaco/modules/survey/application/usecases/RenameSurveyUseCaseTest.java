package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RenameSurveyUseCase")
class RenameSurveyUseCaseTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private RenameSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    useCase = new RenameSurveyUseCase(surveys, versions);
    applicationId = ApplicationId.generate();
  }

  private RenameSurveyUseCase.Input input(String surveyId, String name) {
    return new RenameSurveyUseCase.Input(applicationId.value(), surveyId, name);
  }

  @Test
  @DisplayName("Renomeia o rascunho e grava o novo nome")
  void renomeia_o_rascunho() {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .withName("Nome antigo")
            .buildSavedIn(surveys);

    var output = useCase.execute(input(survey.id().value(), "Nome novo"));

    assertThat(output.name()).isEqualTo("Nome novo");
    assertThat(output.state()).isEqualTo(SurveyState.DRAFT);
    assertThat(surveys.findAll().getFirst().getName().value()).isEqualTo("Nome novo");
  }

  @Test
  @DisplayName("Renomear não muda o ciclo de vida nem o estado derivado")
  void renomear_nao_muda_o_estado() {
    var survey =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);

    var output = useCase.execute(input(survey.id().value(), "Outro nome"));

    assertThat(output.state()).isEqualTo(SurveyState.ACTIVE);
    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
  }

  @Test
  @DisplayName("Nome inválido é recusado pelo value object, e o nome anterior permanece")
  void recusa_nome_invalido() {
    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .withName("Nome antigo")
            .buildSavedIn(surveys);

    assertThatThrownBy(() -> useCase.execute(input(survey.id().value(), "   ")))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> useCase.execute(input(survey.id().value(), "a".repeat(121))))
        .isInstanceOf(DomainException.class);

    assertThat(surveys.findAll().getFirst().getName().value()).isEqualTo("Nome antigo");
  }

  @Test
  @DisplayName("Fora do escopo da aplicação é não encontrada")
  void recusa_fora_do_escopo() {
    var deOutra =
        SurveyFactory.aSurvey().forApplication(ApplicationId.generate()).buildSavedIn(surveys);

    assertThatThrownBy(() -> useCase.execute(input(deOutra.id().value(), "Nome novo")))
        .isInstanceOf(SurveyNotFound.class)
        .satisfies(
            erro -> assertThat(((SurveyNotFound) erro).code()).isEqualTo("survey.not_found"));
    assertThatThrownBy(() -> useCase.execute(input("nao-e-um-id", "Nome novo")))
        .isInstanceOf(SurveyNotFound.class);

    assertThat(surveys.findAll().getFirst().getName().value())
        .isEqualTo(SurveyFactory.DEFAULT_NAME);
  }
}
