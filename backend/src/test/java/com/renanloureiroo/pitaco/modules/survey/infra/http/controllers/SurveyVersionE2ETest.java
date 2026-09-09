package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyVersionJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DefineTriggerRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.PublishSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionOptionDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyVersionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.UpdateQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.VersionComparabilityResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("/applications/{applicationId}/surveys/{surveyId}/versions")
class SurveyVersionE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyJpaRepository surveys;
  @Autowired SurveyVersionJpaRepository versions;
  @Autowired DatabaseCleaner database;

  private ApplicationJpaEntity application;
  private SurveyResponseDTO survey;

  @BeforeEach
  void setUp() {
    database.clean();
    application =
        applications.save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()));
    survey =
        client
            .post()
            .uri("/applications/" + application.getId() + "/surveys")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new CreateSurveyRequestDTO("NPS pós-checkout"))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(SurveyResponseDTO.class)
            .returnResult()
            .getResponseBody();
  }

  private String uri() {
    return "/applications/" + application.getId() + "/surveys/" + survey.id();
  }

  private QuestionResponseDTO addQuestion(AddQuestionRequestDTO request) {
    return client
        .post()
        .uri(uri() + "/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(request)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(QuestionResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private static AddQuestionRequestDTO freeText(String statement) {
    return new AddQuestionRequestDTO(statement, "free_text", true, null, null);
  }

  private SurveyVersionResponseDTO publish(String kind, String summary) {
    return client
        .post()
        .uri(uri() + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new PublishSurveyRequestDTO(kind, summary))
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SurveyVersionResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private SurveyVersionResponseDTO openVersion() {
    return client
        .post()
        .uri(uri() + "/versions")
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SurveyVersionResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private SurveyVersionDetailResponseDTO version(int number) {
    return client
        .get()
        .uri(uri() + "/versions/" + number)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyVersionDetailResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private void publishFirstVersion() {
    addQuestion(freeText("O que achou?"));
    client
        .put()
        .uri(uri() + "/trigger")
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new DefineTriggerRequestDTO(
                "checkout.completed", Instant.now().minus(1, ChronoUnit.HOURS), null, 0.25))
        .exchange()
        .expectStatus()
        .isOk();
    publish(null, null);
  }

  private QuestionResponseDTO firstQuestionOfDraft() {
    var draft = versions.findDraft(survey.id()).orElseThrow();
    var question =
        draft.getQuestions().stream()
            .min(java.util.Comparator.comparingInt(q -> q.getPosition()))
            .orElseThrow();
    return new QuestionResponseDTO(
        question.getId(),
        question.getQuestionKey(),
        question.getStatement(),
        "free_text",
        question.getPosition(),
        question.isRequired(),
        List.of(),
        null);
  }

  private void expect422(String code, String kind, String summary) {
    client
        .post()
        .uri(uri() + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new PublishSurveyRequestDTO(kind, summary))
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo(code);
  }

  @Test
  @DisplayName("Abre a v2 com as mesmas chaves e identidades novas")
  void abre_a_versao_seguinte() {
    publishFirstVersion();
    var v1 = version(1);

    var draft = openVersion();

    assertThat(draft.number()).isEqualTo(2);
    assertThat(draft.status()).isEqualTo("draft");
    assertThat(surveys.findById(survey.id()).orElseThrow().getDraftVersionNumber()).isEqualTo(2);

    var aberto = versions.findDraft(survey.id()).orElseThrow();
    assertThat(aberto.getQuestions())
        .extracting(question -> question.getQuestionKey())
        .containsExactlyElementsOf(v1.questions().stream().map(QuestionResponseDTO::key).toList());
    assertThat(aberto.getQuestions())
        .extracting(question -> question.getId())
        .doesNotContainAnyElementsOf(v1.questions().stream().map(QuestionResponseDTO::id).toList());
    assertThat(aberto.getTriggerEventName()).isEqualTo("checkout.completed");
  }

  @Test
  @DisplayName("Abrir dois rascunhos de versão devolve 409")
  void recusa_dois_rascunhos_de_versao() {
    publishFirstVersion();
    openVersion();

    client
        .post()
        .uri(uri() + "/versions")
        .exchange()
        .expectStatus()
        .isEqualTo(409)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey_version.draft_already_open");
  }

  @Test
  @DisplayName("Publicar cosmética preserva a v1 e mantém o grupo de comparabilidade")
  void publica_cosmetica() {
    publishFirstVersion();
    var textoOriginal = version(1).questions().getFirst().statement();
    openVersion();
    var alvo = firstQuestionOfDraft();
    client
        .put()
        .uri(uri() + "/questions/" + alvo.id())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new UpdateQuestionRequestDTO("O que você achou?", "free_text", true, null, null))
        .exchange()
        .expectStatus()
        .isOk();

    var v2 = publish("cosmetic", "Correção de redação");

    assertThat(v2.number()).isEqualTo(2);
    assertThat(v2.changeKind()).isEqualTo("cosmetic");
    assertThat(v2.comparabilityGroup()).isEqualTo(1);
    assertThat(version(1).questions().getFirst().statement()).isEqualTo(textoOriginal);
    assertThat(version(2).questions().getFirst().statement()).isEqualTo("O que você achou?");
    assertThat(version(1).questions().getFirst().key())
        .isEqualTo(version(2).questions().getFirst().key());
  }

  @Test
  @DisplayName("As quatro diferenças estruturais derrubam a declaração cosmética")
  void recusa_cosmetica_nas_quatro_diferencas() {
    publishFirstVersion();

    // pergunta acrescentada
    openVersion();
    addQuestion(freeText("Pergunta nova"));
    expectCosmeticRefused("question_added");
    publish("semantic", "acrescentada");

    // pergunta removida
    openVersion();
    var paraRemover = firstQuestionOfDraft();
    client
        .delete()
        .uri(uri() + "/questions/" + paraRemover.id())
        .exchange()
        .expectStatus()
        .isNoContent();
    expectCosmeticRefused("question_removed");
    publish("semantic", "removida");

    // pergunta retipada
    openVersion();
    var paraRetipar = firstQuestionOfDraft();
    client
        .put()
        .uri(uri() + "/questions/" + paraRetipar.id())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new UpdateQuestionRequestDTO(
                "Recomendaria?",
                "single_choice",
                true,
                List.of(new QuestionOptionDTO("Sim", "yes")),
                null))
        .exchange()
        .expectStatus()
        .isOk();
    expectCosmeticRefused("type_changed");
    publish("semantic", "retipada");

    // conjunto de opções alterado
    openVersion();
    var paraTrocarOpcoes = firstQuestionOfDraft();
    client
        .put()
        .uri(uri() + "/questions/" + paraTrocarOpcoes.id())
        .contentType(MediaType.APPLICATION_JSON)
        .body(
            new UpdateQuestionRequestDTO(
                "Recomendaria?",
                "single_choice",
                true,
                List.of(new QuestionOptionDTO("Sim", "yes"), new QuestionOptionDTO("Não", "no")),
                null))
        .exchange()
        .expectStatus()
        .isOk();
    expectCosmeticRefused("options_changed");
  }

  private void expectCosmeticRefused(String difference) {
    client
        .post()
        .uri(uri() + "/publication")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new PublishSurveyRequestDTO("cosmetic", "tentativa"))
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey_version.cosmetic_refused")
        .jsonPath("$.differences[0].difference")
        .isEqualTo(difference)
        .jsonPath("$.differences[0].questionKey")
        .exists();
  }

  @Test
  @DisplayName("A mesma mudança é aceita como semântica, com o grupo incrementado")
  void semantica_incrementa_o_grupo() {
    publishFirstVersion();
    openVersion();
    addQuestion(freeText("Pergunta nova"));

    var v2 = publish("semantic", "Pergunta acrescentada");

    assertThat(v2.comparabilityGroup()).isEqualTo(2);
  }

  @Test
  @DisplayName("Rascunho idêntico à publicada devolve 422")
  void recusa_versao_sem_mudanca() {
    publishFirstVersion();
    openVersion();

    expect422("survey_version.no_changes", "cosmetic", "nada mudou");
  }

  @Test
  @DisplayName("Descartar o rascunho de versão devolve a pesquisa à publicada, intacta")
  void descarta_o_rascunho_de_versao() {
    publishFirstVersion();
    openVersion();
    addQuestion(freeText("Pergunta que será descartada"));

    client.delete().uri(uri() + "/versions/draft").exchange().expectStatus().isNoContent();

    assertThat(versions.findDraft(survey.id())).isEmpty();
    assertThat(surveys.findById(survey.id()).orElseThrow().getDraftVersionNumber()).isNull();
    assertThat(version(1).questions()).hasSize(1);
  }

  @Test
  @DisplayName("Descartar sem rascunho de versão aberto devolve 404")
  void recusa_descartar_sem_rascunho() {
    publishFirstVersion();

    client
        .delete()
        .uri(uri() + "/versions/draft")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey_version.not_found");
  }

  @Test
  @DisplayName("Uma pergunta atravessa três versões reconhecível pela chave")
  void a_chave_atravessa_tres_versoes() {
    publishFirstVersion();
    var chave = version(1).questions().getFirst().key();

    openVersion();
    var alvoV2 = firstQuestionOfDraft();
    client
        .put()
        .uri(uri() + "/questions/" + alvoV2.id())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new UpdateQuestionRequestDTO("Segunda redação", "free_text", true, null, null))
        .exchange()
        .expectStatus()
        .isOk();
    publish("cosmetic", "v2");

    openVersion();
    var alvoV3 = firstQuestionOfDraft();
    client
        .put()
        .uri(uri() + "/questions/" + alvoV3.id())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new UpdateQuestionRequestDTO("Terceira redação", "free_text", true, null, null))
        .exchange()
        .expectStatus()
        .isOk();
    publish("cosmetic", "v3");

    assertThat(version(1).questions().getFirst().key()).isEqualTo(chave);
    assertThat(version(2).questions().getFirst().key()).isEqualTo(chave);
    assertThat(version(3).questions().getFirst().key()).isEqualTo(chave);
    assertThat(
            List.of(
                version(1).questions().getFirst().statement(),
                version(2).questions().getFirst().statement(),
                version(3).questions().getFirst().statement()))
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("A listagem de versões percorre as páginas sem repetir nem omitir")
  void lista_as_versoes() {
    publishFirstVersion();
    for (var index = 2; index <= 4; index++) {
      openVersion();
      addQuestion(freeText("Pergunta " + index));
      publish("semantic", "versão " + index);
    }

    var first = listVersions("?page=0&size=2");
    var second = listVersions("?page=1&size=2");

    assertThat(first.total()).isEqualTo(4);
    assertThat(first.totalPages()).isEqualTo(2);
    assertThat(first.items()).extracting(SurveyVersionResponseDTO::number).containsExactly(4, 3);
    assertThat(second.items()).extracting(SurveyVersionResponseDTO::number).containsExactly(2, 1);
    assertThat(first.items().getFirst().changeSummary()).isEqualTo("versão 4");
  }

  @Test
  @DisplayName("A versão devolve o próprio identificador, igual na listagem e no detalhe")
  void devolve_o_identificador_da_versao() {
    publishFirstVersion();
    openVersion();
    addQuestion(freeText("Outra pergunta"));
    publish("semantic", "versão 2");

    var listed = listVersions("").items();

    assertThat(listed).extracting(SurveyVersionResponseDTO::id).doesNotContainNull();
    assertThat(listed).extracting(SurveyVersionResponseDTO::id).doesNotHaveDuplicates();

    for (var item : listed) {
      // Quem identifica a versão na tela é o número; o identificador existe para casar com o
      // que a exibição carrega, e por isso as duas leituras precisam concordar.
      assertThat(version(item.number()).id()).isEqualTo(item.id());
    }
  }

  @Test
  @DisplayName("A comparabilidade agrupa v1 e v2 cosméticas, e v3 semântica abre grupo novo")
  void agrupa_a_comparabilidade() {
    publishFirstVersion();

    openVersion();
    var alvo = firstQuestionOfDraft();
    client
        .put()
        .uri(uri() + "/questions/" + alvo.id())
        .contentType(MediaType.APPLICATION_JSON)
        .body(new UpdateQuestionRequestDTO("Redação revisada", "free_text", true, null, null))
        .exchange()
        .expectStatus()
        .isOk();
    publish("cosmetic", "v2");

    openVersion();
    addQuestion(freeText("Pergunta nova"));
    publish("semantic", "v3");

    var groups =
        client
            .get()
            .uri(uri() + "/versions/comparability")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(VersionComparabilityResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(groups.groups()).hasSize(2);
    assertThat(groups.groups().get(0).group()).isEqualTo(1);
    assertThat(groups.groups().get(0).versions()).containsExactly(1, 2);
    assertThat(groups.groups().get(1).group()).isEqualTo(2);
    assertThat(groups.groups().get(1).versions()).containsExactly(3);
  }

  @Test
  @DisplayName("Abrir versão em pesquisa nunca publicada devolve 422")
  void recusa_abrir_versao_de_rascunho() {
    client
        .post()
        .uri(uri() + "/versions")
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_published");
  }

  @Test
  @DisplayName("Fora do escopo da aplicação é 404 em toda a família de versões")
  void recusa_fora_do_escopo() {
    publishFirstVersion();
    var outra =
        applications.save(
            ApplicationJpaMapper.toJpa(
                ApplicationFactory.anApplication().withSlug("outra-app").build()));
    var alheia = "/applications/" + outra.getId() + "/surveys/" + survey.id() + "/versions";

    client.get().uri(alheia).exchange().expectStatus().isNotFound();
    client.post().uri(alheia).exchange().expectStatus().isNotFound();
    client.get().uri(alheia + "/1").exchange().expectStatus().isNotFound();
    client.get().uri(alheia + "/comparability").exchange().expectStatus().isNotFound();
    client.delete().uri(alheia + "/draft").exchange().expectStatus().isNotFound();
  }

  private PageResponseDTO<SurveyVersionResponseDTO> listVersions(String query) {
    return client
        .get()
        .uri(uri() + "/versions" + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageResponseDTO<SurveyVersionResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }
}
