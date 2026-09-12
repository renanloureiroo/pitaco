package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyVersionJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ConditionDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.DuplicateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionOptionDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Autoria avançada — modelos, condição e duplicação")
class AdvancedAuthoringE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired SurveyVersionJpaRepository versions;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private String applicationId;
  private String otherApplicationId;

  @BeforeEach
  void setUp() {
    database.clean();
    applicationId =
        applications
            .save(ApplicationJpaMapper.toJpa(ApplicationFactory.anApplication().build()))
            .getId();
    otherApplicationId =
        applications
            .save(
                ApplicationJpaMapper.toJpa(
                    ApplicationFactory.anApplication().withSlug("app-transacional").build()))
            .getId();
  }

  private String surveys() {
    return "/applications/" + applicationId + "/surveys";
  }

  private SurveyResponseDTO create(CreateSurveyRequestDTO body) {
    return client
        .post()
        .uri(surveys())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(SurveyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private QuestionResponseDTO add(String surveyId, AddQuestionRequestDTO body) {
    return client
        .post()
        .uri(surveys() + "/" + surveyId + "/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange()
        .expectStatus()
        .isCreated()
        .expectBody(QuestionResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private static AddQuestionRequestDTO choice(String statement) {
    return new AddQuestionRequestDTO(
        statement,
        "single_choice",
        true,
        List.of(new QuestionOptionDTO("Sim", "yes"), new QuestionOptionDTO("Não", "no")),
        null);
  }

  private static AddQuestionRequestDTO followUp(ConditionDTO condition) {
    return new AddQuestionRequestDTO("Por quê?", "free_text", false, null, null, condition);
  }

  private long count(String sql) {
    return jdbc.sql(sql).query(Long.class).single();
  }

  private List<QuestionJpaEntity> draftQuestionsOf(String surveyId) {
    return versions.findDraft(surveyId).orElseThrow().getQuestions().stream()
        .sorted(Comparator.comparingInt(QuestionJpaEntity::getPosition))
        .toList();
  }

  @Test
  @DisplayName("Criar pelo modelo de NPS devolve o modelo e grava a pergunta com escala e rótulos")
  void cria_a_partir_do_modelo() {
    var survey = create(new CreateSurveyRequestDTO("NPS do app", "nps"));

    assertThat(survey.templateKind()).isEqualTo("nps");

    var detail =
        client
            .get()
            .uri(surveys() + "/" + survey.id())
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SurveyDetailResponseDTO.class)
            .returnResult()
            .getResponseBody();
    assertThat(detail.templateKind()).isEqualTo("nps");
    assertThat(detail.content().questions())
        .singleElement()
        .satisfies(
            question -> {
              assertThat(question.type()).isEqualTo("nps");
              assertThat(question.range().min()).isZero();
              assertThat(question.range().max()).isEqualTo(10);
              assertThat(question.range().minLabel()).isEqualTo("Nada provável");
              assertThat(question.range().maxLabel()).isEqualTo("Extremamente provável");
            });

    assertThat(
            jdbc.sql("select template_kind from surveys where id = :id")
                .param("id", survey.id())
                .query(String.class)
                .single())
        .isEqualTo("NPS");
    assertThat(draftQuestionsOf(survey.id()))
        .singleElement()
        .satisfies(question -> assertThat(question.getRangeMinLabel()).isEqualTo("Nada provável"));
  }

  @Test
  void modelo_desconhecido_e_400_sem_gravar() {
    client
        .post()
        .uri(surveys())
        .contentType(MediaType.APPLICATION_JSON)
        .body(Map.of("name", "NPS", "template", "sus"))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid")
        .jsonPath("$.errors.template")
        .isEqualTo("Modelo deve ser nps, csat ou ces");

    assertThat(count("select count(*) from surveys")).isZero();
  }

  @Test
  @DisplayName("Condição válida é gravada, devolvida e aparece no detalhe")
  void condicao_valida() {
    var survey = create(new CreateSurveyRequestDTO("Satisfação"));
    var source = add(survey.id(), choice("Recomendaria?"));

    var conditioned =
        add(survey.id(), followUp(new ConditionDTO(source.key(), "equals", List.of("no"), null, null)));

    assertThat(conditioned.condition().sourceKey()).isEqualTo(source.key());
    assertThat(conditioned.condition().operator()).isEqualTo("equals");
    assertThat(conditioned.condition().values()).containsExactly("no");

    var stored = draftQuestionsOf(survey.id()).get(1);
    assertThat(stored.getConditionSourceKey()).isEqualTo(source.key());
    assertThat(stored.getConditionOperator()).isEqualTo("EQUALS");
    assertThat(
            jdbc.sql("select value from question_condition_values where question_id = :id")
                .param("id", conditioned.id())
                .query(String.class)
                .list())
        .containsExactly("no");
  }

  @Test
  @DisplayName("Condição com opção inexistente é 422 com o código e o campo, sem gravar")
  void condicao_invalida() {
    var survey = create(new CreateSurveyRequestDTO("Satisfação"));
    var source = add(survey.id(), choice("Recomendaria?"));

    client
        .post()
        .uri(surveys() + "/" + survey.id() + "/questions")
        .contentType(MediaType.APPLICATION_JSON)
        .body(followUp(new ConditionDTO(source.key(), "equals", List.of("maybe"), null, null)))
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("question.condition_value_invalid")
        .jsonPath("$.field")
        .isEqualTo("condition.values");

    assertThat(count("select count(*) from questions")).isEqualTo(1);
  }

  @Test
  @DisplayName("Remover a pergunta de origem é 422, apontando a dependente, e nada sai")
  void remover_origem_e_recusado() {
    var survey = create(new CreateSurveyRequestDTO("Satisfação"));
    var source = add(survey.id(), choice("Recomendaria?"));
    var conditioned =
        add(survey.id(), followUp(new ConditionDTO(source.key(), "in", List.of("no"), null, null)));

    client
        .delete()
        .uri(surveys() + "/" + survey.id() + "/questions/" + source.id())
        .exchange()
        .expectStatus()
        .isEqualTo(422)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("question.condition_source_in_use")
        .jsonPath("$.questionKey")
        .isEqualTo(conditioned.key());

    assertThat(count("select count(*) from questions")).isEqualTo(2);
  }

  @Test
  @DisplayName("Duplicar para outra aplicação cria um rascunho novo, com chaves novas e sem histórico")
  void duplica_para_outra_aplicacao() {
    var survey = create(new CreateSurveyRequestDTO("NPS do app", "nps"));
    var source = add(survey.id(), choice("Recomendaria?"));
    add(survey.id(), followUp(new ConditionDTO(source.key(), "equals", List.of("no"), null, null)));
    var originalKeys = draftQuestionsOf(survey.id()).stream().map(QuestionJpaEntity::getQuestionKey).toList();

    var response =
        client
            .post()
            .uri(surveys() + "/" + survey.id() + "/duplicate")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new DuplicateSurveyRequestDTO(otherApplicationId, null))
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(SurveyResponseDTO.class)
            .returnResult();

    var copy = response.getResponseBody();
    assertThat(copy.applicationId()).isEqualTo(otherApplicationId);
    assertThat(copy.name()).isEqualTo("Cópia de NPS do app");
    assertThat(copy.templateKind()).isEqualTo("nps");
    assertThat(copy.state()).isEqualTo("draft");
    assertThat(copy.draftVersionNumber()).isEqualTo(1);
    assertThat(copy.publishedVersionNumber()).isNull();
    assertThat(response.getResponseHeaders().getLocation())
        .asString()
        .endsWith("/api/applications/" + otherApplicationId + "/surveys/" + copy.id());

    assertThat(
            jdbc.sql("select application_id from surveys where id = :id")
                .param("id", copy.id())
                .query(String.class)
                .single())
        .isEqualTo(otherApplicationId);
    var copied = draftQuestionsOf(copy.id());
    assertThat(copied).hasSize(3);
    assertThat(copied).extracting(QuestionJpaEntity::getQuestionKey).doesNotContainAnyElementsOf(originalKeys);
    assertThat(copied.get(2).getConditionSourceKey()).isEqualTo(copied.get(1).getQuestionKey());
    assertThat(
            jdbc.sql("select count(*) from survey_versions where survey_id = :id")
                .param("id", copy.id())
                .query(Long.class)
                .single())
        .isEqualTo(1);
    assertThat(
            jdbc.sql("select count(*) from survey_displays where survey_id = :id")
                .param("id", copy.id())
                .query(Long.class)
                .single())
        .isZero();
    assertThat(draftQuestionsOf(survey.id())).hasSize(3);
  }

  @Test
  void duplicar_sem_corpo_fica_na_mesma_aplicacao() {
    var survey = create(new CreateSurveyRequestDTO("Satisfação"));

    var copy =
        client
            .post()
            .uri(surveys() + "/" + survey.id() + "/duplicate")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(SurveyResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(copy.applicationId()).isEqualTo(applicationId);
    assertThat(copy.name()).isEqualTo("Cópia de Satisfação");
    assertThat(count("select count(*) from surveys")).isEqualTo(2);
  }

  @Test
  void duplicar_para_aplicacao_inexistente_e_404_sem_gravar() {
    var survey = create(new CreateSurveyRequestDTO("Satisfação"));

    client
        .post()
        .uri(surveys() + "/" + survey.id() + "/duplicate")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new DuplicateSurveyRequestDTO(UUID.randomUUID().toString(), null))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.not_found");

    assertThat(count("select count(*) from surveys")).isEqualTo(1);
  }

  @Test
  void duplicar_pesquisa_desconhecida_e_404() {
    client
        .post()
        .uri(surveys() + "/" + UUID.randomUUID() + "/duplicate")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_found");

    assertThat(count("select count(*) from surveys")).isZero();
  }

  @Test
  void duplicar_com_nome_em_branco_e_400_sem_gravar() {
    var survey = create(new CreateSurveyRequestDTO("Satisfação"));

    client
        .post()
        .uri(surveys() + "/" + survey.id() + "/duplicate")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new DuplicateSurveyRequestDTO(null, "   "))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid")
        .jsonPath("$.errors.name")
        .isEqualTo("Nome não pode ser vazio");

    assertThat(count("select count(*) from surveys")).isEqualTo(1);
  }

  @Test
  void duplicar_com_json_malformado_e_400() {
    var survey = create(new CreateSurveyRequestDTO("Satisfação"));

    client
        .post()
        .uri(surveys() + "/" + survey.id() + "/duplicate")
        .contentType(MediaType.APPLICATION_JSON)
        .body("{")
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(count("select count(*) from surveys")).isEqualTo(1);
  }
}
