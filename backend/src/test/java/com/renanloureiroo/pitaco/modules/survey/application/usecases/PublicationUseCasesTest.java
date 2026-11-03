package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.application.errors.CosmeticDeclarationRefused;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyAlreadyPublished;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotPublishable;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyVersionHasNoChanges;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyVersionNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.PublicationImpedimentOutput;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.QuestionOutput;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.ChangeKind;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.TransitionReason;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyStateTransitionRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Casos de uso de publicação e versão")
class PublicationUseCasesTest {

  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private InMemorySurveyStateTransitionRepository transitions;
  private ApplicationId applicationId;
  private Survey survey;

  private CheckSurveyPublicationUseCase check;
  private PublishSurveyUseCase publish;
  private GetSurveyVersionUseCase getVersion;
  private OpenSurveyVersionUseCase openVersion;
  private DiscardSurveyVersionUseCase discardVersion;
  private ListSurveyVersionsUseCase listVersions;
  private GetVersionComparabilityUseCase comparability;
  private AddQuestionUseCase addQuestion;
  private UpdateQuestionUseCase updateQuestion;
  private RemoveQuestionUseCase removeQuestion;

  @BeforeEach
  void setUp() {
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    transitions = new InMemorySurveyStateTransitionRepository();
    applicationId = ApplicationId.generate();
    survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);

    check = new CheckSurveyPublicationUseCase(surveys, versions);
    publish = new PublishSurveyUseCase(surveys, versions, transitions);
    getVersion = new GetSurveyVersionUseCase(surveys, versions);
    openVersion = new OpenSurveyVersionUseCase(surveys, versions);
    discardVersion = new DiscardSurveyVersionUseCase(surveys, versions);
    listVersions = new ListSurveyVersionsUseCase(surveys, versions);
    comparability = new GetVersionComparabilityUseCase(surveys, versions);
    addQuestion = new AddQuestionUseCase(surveys, versions);
    updateQuestion = new UpdateQuestionUseCase(surveys, versions);
    removeQuestion = new RemoveQuestionUseCase(surveys, versions);
  }

  private SurveyVersion emptyDraft() {
    return SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).buildSavedIn(versions);
  }

  private SurveyVersion publishableDraft(TriggerFactory trigger) {
    var draft = SurveyVersionFactory.anEmptyDraft().forSurvey(survey.id()).build();
    QuestionFactory.aFreeTextQuestion().withStatement("O que achou?").buildAddedTo(draft);
    draft.defineTrigger(trigger.build());
    return versions.create(draft);
  }

  private PublishSurveyUseCase.Input publishInput(ChangeKind kind, String summary) {
    return new PublishSurveyUseCase.Input(
        applicationId.value(),
        survey.id().value(),
        Optional.ofNullable(kind),
        Optional.ofNullable(summary));
  }

  private QuestionOutput addFreeText(String statement) {
    return addQuestion.execute(
        new AddQuestionUseCase.Input(
            applicationId.value(),
            survey.id().value(),
            QuestionFactory.aFreeTextQuestion().withStatement(statement).asInputDraft()));
  }

  @Test
  @DisplayName("Consultar impedimentos devolve a lista completa sem publicar nem escrever")
  void consulta_impedimentos_sem_publicar() {
    emptyDraft();

    var output =
        check.execute(
            new CheckSurveyPublicationUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output)
        .extracting(PublicationImpedimentOutput::code)
        .containsExactlyInAnyOrder("survey.no_questions", "trigger.missing");
    assertThat(versions.findDraft(survey.id()).orElseThrow().getStatus())
        .isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.DRAFT);
    assertThat(transitions.isEmpty()).isTrue();
  }

  @Test
  void rascunho_completo_nao_tem_impedimento() {
    publishableDraft(TriggerFactory.anOpenTrigger());

    var output =
        check.execute(
            new CheckSurveyPublicationUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output).isEmpty();
  }

  @Test
  void consultar_impedimentos_fora_do_escopo_e_nao_encontrada() {
    emptyDraft();

    var input =
        new CheckSurveyPublicationUseCase.Input(
            ApplicationId.generate().value(), survey.id().value());

    assertThatThrownBy(() -> check.execute(input)).isInstanceOf(SurveyNotFound.class);
  }

  @Test
  @DisplayName("Publica a versão 1, marca a pesquisa e grava a transição de publicação")
  void publica_a_versao_um() {
    publishableDraft(TriggerFactory.anOpenTrigger());

    var output = publish.execute(publishInput(null, null));

    assertThat(output.number()).isEqualTo(1);
    assertThat(output.status()).isEqualTo(SurveyVersionStatus.PUBLISHED);
    assertThat(output.publishedAt()).isPresent();
    assertThat(output.comparabilityGroup()).isEqualTo(1);
    assertThat(output.changeKind()).isEmpty();

    var stored = surveys.findAll().getFirst();
    assertThat(stored.getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
    assertThat(stored.publishedVersionNumber()).contains(1);
    assertThat(stored.draftVersionNumber()).isEmpty();

    assertThat(transitions.findAll())
        .singleElement()
        .satisfies(
            transition -> {
              assertThat(transition.getReason()).isEqualTo(TransitionReason.PUBLICATION);
              assertThat(transition.getFrom()).isEqualTo(SurveyState.DRAFT);
              assertThat(transition.getTo()).isEqualTo(SurveyState.ACTIVE);
            });
  }

  @Test
  @DisplayName("Janela futura deixa a pesquisa agendada; janela aberta, ativa")
  void a_janela_decide_o_estado_apos_publicar() {
    publishableDraft(TriggerFactory.aScheduledTrigger());

    publish.execute(publishInput(null, null));

    assertThat(transitions.findAll().getFirst().getTo()).isEqualTo(SurveyState.SCHEDULED);
  }

  @Test
  @DisplayName("Recusa a publicação com todos os impedimentos de uma vez, sem congelar nada")
  void recusa_publicar_com_impedimentos() {
    emptyDraft();

    assertThatThrownBy(() -> publish.execute(publishInput(null, null)))
        .isInstanceOf(SurveyNotPublishable.class)
        .satisfies(
            erro -> {
              var recusa = (SurveyNotPublishable) erro;
              assertThat(recusa.code()).isEqualTo("survey.not_publishable");
              assertThat(recusa.impediments()).hasSize(2);
              assertThat(recusa.extensions()).containsKey("impediments");
            });

    assertThat(surveys.findAll().getFirst().getLifecycle()).isEqualTo(SurveyLifecycle.DRAFT);
    assertThat(transitions.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("Publicar de novo, sem rascunho aberto, é conflito")
  void recusa_publicar_de_novo() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));

    assertThatThrownBy(() -> publish.execute(publishInput(null, null)))
        .isInstanceOf(SurveyAlreadyPublished.class)
        .satisfies(
            erro ->
                assertThat(((SurveyAlreadyPublished) erro).code())
                    .isEqualTo("survey.already_published"));
  }

  @Test
  @DisplayName("Consulta a versão publicada com as perguntas na ordem")
  void consulta_a_versao() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    addFreeText("Segunda");
    publish.execute(publishInput(null, null));

    var output =
        getVersion.execute(
            new GetSurveyVersionUseCase.Input(applicationId.value(), survey.id().value(), 1));

    assertThat(output.version().number()).isEqualTo(1);
    assertThat(output.questions())
        .extracting(QuestionOutput::statement)
        .containsExactly("O que achou?", "Segunda");
    assertThat(output.questions()).extracting(QuestionOutput::position).containsExactly(1, 2);
    assertThat(output.questions())
        .extracting(QuestionOutput::type)
        .containsOnly(QuestionType.FREE_TEXT);
    assertThat(output.trigger()).isPresent();
  }

  @Test
  void recusa_versao_inexistente_e_fora_do_escopo() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));

    assertThatThrownBy(
            () ->
                getVersion.execute(
                    new GetSurveyVersionUseCase.Input(
                        applicationId.value(), survey.id().value(), 99)))
        .isInstanceOf(SurveyVersionNotFound.class);

    assertThatThrownBy(
            () ->
                getVersion.execute(
                    new GetSurveyVersionUseCase.Input(
                        ApplicationId.generate().value(), survey.id().value(), 1)))
        .isInstanceOf(SurveyNotFound.class);
  }

  @Test
  @DisplayName("Abre a v2 com cópia integral, chaves preservadas e identidades novas")
  void abre_a_versao_seguinte() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    var v1 = publish.execute(publishInput(null, null));
    var publicada = versions.findPublished(survey.id()).orElseThrow();

    var draft =
        openVersion.execute(
            new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(draft.number()).isEqualTo(v1.number() + 1);
    assertThat(draft.status()).isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(surveys.findAll().getFirst().draftVersionNumber()).contains(2);

    var aberto = versions.findDraft(survey.id()).orElseThrow();
    assertThat(aberto.getQuestions())
        .extracting(question -> question.getKey().value())
        .containsExactlyElementsOf(
            publicada.getQuestions().stream().map(q -> q.getKey().value()).toList());
    assertThat(aberto.getQuestions())
        .extracting(question -> question.id().value())
        .doesNotContainAnyElementsOf(
            publicada.getQuestions().stream().map(q -> q.id().value()).toList());
  }

  @Test
  void recusa_abrir_dois_rascunhos_de_versao() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    var input = new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value());
    openVersion.execute(input);

    assertThatThrownBy(() -> openVersion.execute(input))
        .satisfies(
            erro ->
                assertThat(
                        ((com.renanloureiroo.pitaco.core.error.ApplicationException) erro).code())
                    .isEqualTo("survey_version.draft_already_open"));
  }

  @Test
  void recusa_abrir_versao_em_pesquisa_nunca_publicada_ou_encerrada() {
    emptyDraft();
    var input = new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value());

    assertThatThrownBy(() -> openVersion.execute(input))
        .satisfies(
            erro ->
                assertThat(
                        ((com.renanloureiroo.pitaco.core.error.ApplicationException) erro).code())
                    .isEqualTo("survey.not_published"));

    var encerrada =
        SurveyFactory.anEndedSurvey().forApplication(applicationId).buildSavedIn(surveys);
    var encerradaInput =
        new OpenSurveyVersionUseCase.Input(applicationId.value(), encerrada.id().value());

    assertThatThrownBy(() -> openVersion.execute(encerradaInput))
        .satisfies(
            erro ->
                assertThat(
                        ((com.renanloureiroo.pitaco.core.error.ApplicationException) erro).code())
                    .isEqualTo("survey.transition_not_allowed"));
  }

  @Test
  @DisplayName("Descartar o rascunho de versão devolve a pesquisa à publicada, intacta")
  void descarta_o_rascunho_de_versao() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));

    discardVersion.execute(
        new DiscardSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(versions.findDraft(survey.id())).isEmpty();
    assertThat(versions.findPublished(survey.id())).isPresent();
    assertThat(surveys.findAll().getFirst().draftVersionNumber()).isEmpty();
  }

  @Test
  void recusa_descartar_versao_quando_nao_ha_rascunho() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    var input = new DiscardSurveyVersionUseCase.Input(applicationId.value(), survey.id().value());

    assertThatThrownBy(() -> discardVersion.execute(input))
        .isInstanceOf(SurveyVersionNotFound.class);
  }

  @Test
  @DisplayName("Cosmética é aceita em correção de enunciado e herda o grupo")
  void publica_versao_cosmetica() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));
    var draft = versions.findDraft(survey.id()).orElseThrow();
    updateQuestion.execute(
        new UpdateQuestionUseCase.Input(
            applicationId.value(),
            survey.id().value(),
            draft.getQuestions().getFirst().id().value(),
            QuestionFactory.aFreeTextQuestion().withStatement("O que você achou?").asInputDraft()));

    var v2 = publish.execute(publishInput(ChangeKind.COSMETIC, "Correção de redação"));

    assertThat(v2.number()).isEqualTo(2);
    assertThat(v2.comparabilityGroup()).isEqualTo(1);
    assertThat(v2.changeKind()).contains(ChangeKind.COSMETIC);
    assertThat(v2.changeSummary()).contains("Correção de redação");
  }

  @Test
  @DisplayName("Cosmética é recusada quando há diferença estrutural, e nomeia a diferença")
  void recusa_cosmetica_com_diferenca_estrutural() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));
    addFreeText("Pergunta nova");

    assertThatThrownBy(() -> publish.execute(publishInput(ChangeKind.COSMETIC, "resumo")))
        .isInstanceOf(CosmeticDeclarationRefused.class)
        .satisfies(
            erro -> {
              var recusa = (CosmeticDeclarationRefused) erro;
              assertThat(recusa.code()).isEqualTo("survey_version.cosmetic_refused");
              assertThat(recusa.differences())
                  .extracting(ChangeClassification.Difference::kind)
                  .containsExactly(ChangeClassification.DifferenceKind.QUESTION_ADDED);
              assertThat(recusa.extensions()).containsKey("differences");
            });
  }

  @Test
  @DisplayName("A mesma mudança é aceita como semântica, e abre o grupo seguinte")
  void semantica_e_sempre_aceita() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));
    addFreeText("Pergunta nova");

    var v2 = publish.execute(publishInput(ChangeKind.SEMANTIC, "Pergunta acrescentada"));

    assertThat(v2.comparabilityGroup()).isEqualTo(2);
  }

  @Test
  @DisplayName("Rascunho idêntico à publicada não tem o que publicar")
  void recusa_versao_sem_mudanca() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));

    assertThatThrownBy(() -> publish.execute(publishInput(ChangeKind.COSMETIC, "nada mudou")))
        .isInstanceOf(SurveyVersionHasNoChanges.class)
        .satisfies(
            erro ->
                assertThat(((SurveyVersionHasNoChanges) erro).code())
                    .isEqualTo("survey_version.no_changes"));
  }

  @Test
  @DisplayName("Remover pergunta também derruba a declaração cosmética")
  void remover_pergunta_derruba_cosmetica() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    addFreeText("Segunda");
    publish.execute(publishInput(null, null));
    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));
    var draft = versions.findDraft(survey.id()).orElseThrow();
    removeQuestion.execute(
        new RemoveQuestionUseCase.Input(
            applicationId.value(),
            survey.id().value(),
            draft.getQuestions().getFirst().id().value()));

    assertThatThrownBy(() -> publish.execute(publishInput(ChangeKind.COSMETIC, "resumo")))
        .isInstanceOf(CosmeticDeclarationRefused.class)
        .satisfies(
            erro ->
                assertThat(((CosmeticDeclarationRefused) erro).differences())
                    .extracting(ChangeClassification.Difference::kind)
                    .containsExactly(ChangeClassification.DifferenceKind.QUESTION_REMOVED));
  }

  @Test
  @DisplayName("A listagem devolve as versões da mais recente para a mais antiga")
  void lista_as_versoes() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));
    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));
    addFreeText("Pergunta nova");
    publish.execute(publishInput(ChangeKind.SEMANTIC, "Pergunta acrescentada"));

    var output =
        listVersions.execute(
            new ListSurveyVersionsUseCase.Input(applicationId.value(), survey.id().value(), 0, 20));

    assertThat(output.items()).extracting(item -> item.number()).containsExactly(2, 1);
    assertThat(output.total()).isEqualTo(2);
    assertThat(output.totalPages()).isEqualTo(1);
    assertThat(output.items().getFirst().changeSummary()).contains("Pergunta acrescentada");
  }

  @Test
  @DisplayName("A comparabilidade agrupa pela coluna gravada, com a transitividade de D-12")
  void agrupa_por_comparabilidade() {
    publishableDraft(TriggerFactory.anOpenTrigger());
    publish.execute(publishInput(null, null));

    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));
    var draft = versions.findDraft(survey.id()).orElseThrow();
    updateQuestion.execute(
        new UpdateQuestionUseCase.Input(
            applicationId.value(),
            survey.id().value(),
            draft.getQuestions().getFirst().id().value(),
            QuestionFactory.aFreeTextQuestion()
                .withStatement("Enunciado revisado")
                .asInputDraft()));
    publish.execute(publishInput(ChangeKind.COSMETIC, "Correção"));

    openVersion.execute(
        new OpenSurveyVersionUseCase.Input(applicationId.value(), survey.id().value()));
    addFreeText("Terceira");
    publish.execute(publishInput(ChangeKind.SEMANTIC, "Pergunta acrescentada"));

    var output =
        comparability.execute(
            new GetVersionComparabilityUseCase.Input(applicationId.value(), survey.id().value()));

    assertThat(output.groups()).hasSize(2);
    assertThat(output.groups().get(0).group()).isEqualTo(1);
    assertThat(output.groups().get(0).versions()).isEqualTo(List.of(1, 2));
    assertThat(output.groups().get(1).group()).isEqualTo(2);
    assertThat(output.groups().get(1).versions()).isEqualTo(List.of(3));
  }
}
