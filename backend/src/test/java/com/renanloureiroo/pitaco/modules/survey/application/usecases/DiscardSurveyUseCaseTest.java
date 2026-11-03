package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.PublishedSurveyCannotBeDiscarded;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DiscardSurveyUseCase")
class DiscardSurveyUseCaseTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private DiscardSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    useCase = new DiscardSurveyUseCase(surveys, versions);
    applicationId = ApplicationId.generate();
  }

  private DiscardSurveyUseCase.Input input(String surveyId) {
    return new DiscardSurveyUseCase.Input(applicationId.value(), surveyId);
  }

  @Test
  @DisplayName("Descarta a pesquisa nunca publicada junto do conteúdo montado nela")
  void descarta_a_pesquisa_e_o_conteudo() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).buildSavedIn(versions);

    useCase.execute(input(survey.id().value()));

    assertThat(surveys.isEmpty()).isTrue();
    assertThat(versions.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("Descartar não toca em pesquisa de outra aplicação")
  void descarte_nao_afeta_as_demais() {
    var minha = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.anEmptyDraft().forSurvey(minha.id()).buildSavedIn(versions);
    var outra = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.anEmptyDraft().forSurvey(outra.id()).buildSavedIn(versions);

    useCase.execute(input(minha.id().value()));

    assertThat(surveys.findAll())
        .extracting(survey -> survey.id().value())
        .containsExactly(outra.id().value());
    assertThat(versions.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("Pesquisa publicada não se apaga, se encerra")
  void recusa_descartar_publicada() {
    var survey =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion().forSurvey(survey.id()).buildPublishedSavedIn(versions);

    assertThatThrownBy(() -> useCase.execute(input(survey.id().value())))
        .isInstanceOf(PublishedSurveyCannotBeDiscarded.class)
        .satisfies(
            erro ->
                assertThat(((PublishedSurveyCannotBeDiscarded) erro).code())
                    .isEqualTo("survey.published_cannot_be_discarded"));

    assertThat(surveys.findAll()).hasSize(1);
    assertThat(versions.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("Pesquisa publicada com rascunho de versão aberto também não se apaga")
  void recusa_descartar_publicada_com_rascunho() {
    var survey =
        SurveyFactory.aPublishedSurvey()
            .forApplication(applicationId)
            .withOpenDraft(2)
            .buildSavedIn(surveys);

    assertThatThrownBy(() -> useCase.execute(input(survey.id().value())))
        .isInstanceOf(PublishedSurveyCannotBeDiscarded.class);
  }

  @Test
  void recusa_fora_do_escopo() {
    var deOutra =
        SurveyFactory.aSurvey().forApplication(ApplicationId.generate()).buildSavedIn(surveys);

    assertThatThrownBy(() -> useCase.execute(input(deOutra.id().value())))
        .isInstanceOf(SurveyNotFound.class);
    assertThatThrownBy(() -> useCase.execute(input("nao-e-um-id")))
        .isInstanceOf(SurveyNotFound.class);

    assertThat(surveys.findAll()).hasSize(1);
  }
}
