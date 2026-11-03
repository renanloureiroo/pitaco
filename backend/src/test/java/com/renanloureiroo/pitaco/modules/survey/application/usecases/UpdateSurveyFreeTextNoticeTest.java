package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCompletedResponsesGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyStateTransitionRepository;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.usecase.Patch;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.FreeTextNotice;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("UpdateSurveyUseCase — aviso de texto livre")
class UpdateSurveyFreeTextNoticeTest {

  private InMemorySurveyRepository surveys;
  private UpdateSurveyUseCase useCase;
  private ApplicationId applicationId;
  private String surveyId;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    useCase =
        new UpdateSurveyUseCase(
            surveys,
            new InMemorySurveyVersionRepository(),
            new InMemorySurveyStateTransitionRepository(),
            new InMemoryCompletedResponsesGateway());
    applicationId = ApplicationId.generate();
    surveyId =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys).id().value();
  }

  private UpdateSurveyUseCase.Input notice(Optional<Boolean> enabled, Patch<String> text) {
    return new UpdateSurveyUseCase.Input(
        applicationId.value(),
        surveyId,
        Optional.empty(),
        Optional.empty(),
        Patch.absent(),
        Optional.empty(),
        enabled,
        text);
  }

  private FreeTextNotice stored() {
    return surveys.findAll().getFirst().getFreeTextNotice();
  }

  @Test
  @DisplayName("Nasce ligado com o texto padrão, e o PATCH sem os campos não mexe nele")
  void padrao_e_ausente() {
    var output = useCase.execute(notice(Optional.empty(), Patch.absent()));

    assertThat(output.freeTextNotice()).isEqualTo(FreeTextNotice.standard());
    assertThat(stored()).isEqualTo(FreeTextNotice.standard());
  }

  @Test
  @DisplayName("Desliga e define texto próprio, inclusive com a pesquisa no ar")
  void desliga_e_personaliza() {
    useCase.execute(notice(Optional.of(false), Patch.set("Não escreva seu telefone.")));

    assertThat(stored().enabled()).isFalse();
    assertThat(stored().text()).isEqualTo("Não escreva seu telefone.");
  }

  @Test
  @DisplayName("Texto nulo volta ao padrão sem mudar se o aviso está ligado")
  void texto_nulo_volta_ao_padrao() {
    useCase.execute(notice(Optional.empty(), Patch.set("Outro texto")));

    useCase.execute(notice(Optional.empty(), Patch.clear()));

    assertThat(stored()).isEqualTo(FreeTextNotice.standard());
  }

  @Test
  @DisplayName("Texto vazio é recusado e nada muda")
  void texto_vazio() {
    assertThatThrownBy(() -> useCase.execute(notice(Optional.of(false), Patch.set("  "))))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("survey.free_text_notice_invalid");
    assertThat(stored()).isEqualTo(FreeTextNotice.standard());
  }

  @Test
  @DisplayName("A duplicação leva o aviso junto")
  void duplicacao_leva_o_aviso() {
    useCase.execute(notice(Optional.of(false), Patch.set("Texto próprio")));
    var original = surveys.findAll().getFirst();

    var copy =
        original.duplicateInto(
            ApplicationId.generate(),
            com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName.of("Cópia"));

    assertThat(copy.getFreeTextNotice()).isEqualTo(original.getFreeTextNotice());
  }
}
