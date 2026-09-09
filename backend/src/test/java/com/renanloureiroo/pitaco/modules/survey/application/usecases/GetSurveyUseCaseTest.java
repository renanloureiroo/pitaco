package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SegmentationRuleOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyDetailOutput;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("GetSurveyUseCase")
class GetSurveyUseCaseTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private GetSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    useCase = new GetSurveyUseCase(surveys, versions);
    applicationId = ApplicationId.generate();
  }

  private GetSurveyUseCase.Input input(String surveyId) {
    return new GetSurveyUseCase.Input(applicationId.value(), surveyId);
  }

  @Test
  @DisplayName("Devolve os dados da pesquisa e o conteúdo do rascunho, com disparo e regras")
  void devolve_a_pesquisa_com_o_conteudo_do_rascunho() {
    var survey =
        SurveyFactory.aSurvey().forApplication(applicationId).withName("NPS").buildSavedIn(surveys);
    var version = SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).build();
    QuestionFactory.aSingleChoiceQuestion().withStatement("Recomendaria?").buildAddedTo(version);
    version.defineTrigger(TriggerFactory.anOpenTrigger().build());
    version.addRule(TriggerFactory.anEqualityRule("plan", "pro"));
    versions.create(version);

    var output = useCase.execute(input(survey.id().value()));

    assertThat(output.survey().id()).isEqualTo(survey.id().value());
    assertThat(output.survey().applicationId()).isEqualTo(applicationId.value());
    assertThat(output.survey().name()).isEqualTo("NPS");
    assertThat(output.survey().state()).isEqualTo(SurveyState.DRAFT);
    assertThat(output.survey().createdAt()).isEqualTo(survey.getCreatedAt());

    var content = output.content().orElseThrow();
    assertThat(content.source()).isEqualTo(SurveyDetailOutput.ContentSource.DRAFT);
    assertThat(content.versionNumber()).isEqualTo(1);
    assertThat(content.questions())
        .extracting(QuestionOutput::statement)
        .containsExactly("Recomendaria?");
    assertThat(content.trigger()).isPresent();
    assertThat(content.trigger().orElseThrow().rules())
        .extracting(SegmentationRuleOutput::attribute)
        .containsExactly("plan");
  }

  @Test
  @DisplayName("Sem rascunho, o conteúdo devolvido é o da versão publicada")
  void devolve_o_conteudo_da_publicada_quando_nao_ha_rascunho() {
    var survey =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);

    var output = useCase.execute(input(survey.id().value()));

    assertThat(output.content().orElseThrow().source())
        .isEqualTo(SurveyDetailOutput.ContentSource.PUBLISHED);
    assertThat(output.survey().state()).isEqualTo(SurveyState.ACTIVE);
    assertThat(output.survey().publishedVersionNumber()).contains(1);
  }

  @Test
  @DisplayName("Publicada com janela futura aparece agendada; com janela fechada, encerrada")
  void o_estado_sai_da_janela_da_versao_publicada() {
    var scheduled =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(scheduled.id())
        .triggeredBy(TriggerFactory.aScheduledTrigger())
        .buildPublishedSavedIn(versions);

    var ended =
        SurveyFactory.aPublishedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(ended.id())
        .triggeredBy(TriggerFactory.aClosedTrigger())
        .buildPublishedSavedIn(versions);

    assertThat(useCase.execute(input(scheduled.id().value())).survey().state())
        .isEqualTo(SurveyState.SCHEDULED);
    assertThat(useCase.execute(input(ended.id().value())).survey().state())
        .isEqualTo(SurveyState.ENDED);
  }

  @Test
  @DisplayName("Pesquisa recém-criada, sem conteúdo nenhum, devolve conteúdo vazio")
  void rascunho_vazio_devolve_conteudo_vazio() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).buildSavedIn(versions);

    var content = useCase.execute(input(survey.id().value())).content().orElseThrow();

    assertThat(content.questions()).isEmpty();
    assertThat(content.trigger()).isEmpty();
  }

  @Test
  @DisplayName("Inexistente, malformada e de outra aplicação recusam do mesmo jeito")
  void recusa_fora_do_escopo() {
    var deOutraAplicacao =
        SurveyFactory.aSurvey().forApplication(ApplicationId.generate()).buildSavedIn(surveys);

    assertThatThrownBy(() -> useCase.execute(input(SurveyId.generate().value())))
        .satisfies(GetSurveyUseCaseTest::naoEncontrada);
    assertThatThrownBy(() -> useCase.execute(input("nao-e-um-id")))
        .satisfies(GetSurveyUseCaseTest::naoEncontrada);
    assertThatThrownBy(() -> useCase.execute(input(deOutraAplicacao.id().value())))
        .satisfies(GetSurveyUseCaseTest::naoEncontrada);
    assertThatThrownBy(
            () ->
                useCase.execute(
                    new GetSurveyUseCase.Input("nao-e-um-id", deOutraAplicacao.id().value())))
        .satisfies(GetSurveyUseCaseTest::naoEncontrada);
  }

  @Test
  @DisplayName("A consulta não escreve nada")
  void consultar_nao_escreve() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    var version = SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).buildSavedIn(versions);
    var perguntasAntes = versions.findAll().getFirst().getQuestions();

    useCase.execute(input(survey.id().value()));

    assertThat(surveys.findAll()).hasSize(1);
    assertThat(versions.findAll()).hasSize(1);
    assertThat(versions.findAll().getFirst().id()).isEqualTo(version.id());
    assertThat(versions.findAll().getFirst().getQuestions()).isEqualTo(perguntasAntes);
    assertThat(perguntasAntes).isEmpty();
  }

  private static void naoEncontrada(Throwable error) {
    assertThat(error).isInstanceOf(SurveyNotFound.class);
    assertThat(((SurveyNotFound) error).code()).isEqualTo("survey.not_found");
  }
}
