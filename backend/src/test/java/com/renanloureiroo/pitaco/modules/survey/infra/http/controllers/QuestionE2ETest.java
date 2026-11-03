package com.renanloureiroo.pitaco.modules.survey.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.entities.QuestionJpaEntity;
import com.renanloureiroo.pitaco.modules.survey.infra.database.jpa.repositories.SurveyVersionJpaRepository;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.AddQuestionRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.CreateSurveyRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionOptionDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.QuestionResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ReorderQuestionsRequestDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.ScaleRangeDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.UpdateQuestionRequestDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("/applications/{applicationId}/surveys/{surveyId}/questions")
class QuestionE2ETest {

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
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
    return "/applications/" + application.getId() + "/surveys/" + survey.id() + "/questions";
  }

  private QuestionResponseDTO add(AddQuestionRequestDTO request) {
    return client
        .post()
        .uri(uri())
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

  private static AddQuestionRequestDTO choice(String statement, String... values) {
    var options =
        java.util.Arrays.stream(values)
            .map(value -> new QuestionOptionDTO("Rótulo " + value, value))
            .toList();
    return new AddQuestionRequestDTO(statement, "single_choice", true, options, null);
  }

  private List<QuestionJpaEntity> storedQuestions() {
    return versions.findDraft(survey.id()).orElseThrow().getQuestions().stream()
        .sorted(Comparator.comparingInt(QuestionJpaEntity::getPosition))
        .toList();
  }

  @Test
  @DisplayName("Aceita uma pergunta de cada um dos seis tipos, relidas do banco na ordem")
  void aceita_os_seis_tipos() {
    add(freeText("Comentário livre"));
    add(choice("Recomendaria?", "yes", "no"));
    add(
        new AddQuestionRequestDTO(
            "Por onde prefere falar?",
            "multiple_choice",
            false,
            List.of(
                new QuestionOptionDTO("E-mail", "email"), new QuestionOptionDTO("Chat", "chat")),
            null));
    add(new AddQuestionRequestDTO("Avalie", "rating", true, null, new ScaleRangeDTO(1, 5)));
    add(new AddQuestionRequestDTO("Concorda?", "scale", true, null, new ScaleRangeDTO(1, 7)));
    add(new AddQuestionRequestDTO("De 0 a 10", "nps", true, null, new ScaleRangeDTO(0, 10)));

    var stored = storedQuestions();

    assertThat(stored)
        .extracting(QuestionJpaEntity::getType)
        .containsExactly("FREE_TEXT", "SINGLE_CHOICE", "MULTIPLE_CHOICE", "RATING", "SCALE", "NPS");
    assertThat(stored).extracting(QuestionJpaEntity::getPosition).containsExactly(1, 2, 3, 4, 5, 6);
    assertThat(stored.get(1).getOptions()).hasSize(2);
    assertThat(stored.get(5).getRangeMin()).isZero();
    assertThat(stored.get(5).getRangeMax()).isEqualTo(10);
    assertThat(stored).extracting(QuestionJpaEntity::getQuestionKey).doesNotHaveDuplicates();
  }

  @Test
  @DisplayName("Escolha sem nenhuma opção é aceita no rascunho")
  void aceita_escolha_sem_opcao() {
    var created =
        add(new AddQuestionRequestDTO("Recomendaria?", "single_choice", true, List.of(), null));

    assertThat(created.options()).isEmpty();
    assertThat(storedQuestions())
        .singleElement()
        .satisfies(question -> assertThat(question.getOptions()).isEmpty());
  }

  @Test
  @DisplayName("Cada incoerência devolve 400 e nada é gravado")
  void recusa_conteudo_incoerente() {
    var casos =
        List.of(
            freeText("   "),
            new AddQuestionRequestDTO(
                "Comentário",
                "free_text",
                true,
                List.of(new QuestionOptionDTO("Sim", "yes")),
                null),
            choice("Repetidas", "yes", "yes"),
            new AddQuestionRequestDTO("Escala", "scale", true, null, new ScaleRangeDTO(5, 1)),
            new AddQuestionRequestDTO("Escala sem faixa", "scale", true, null, null),
            new AddQuestionRequestDTO("NPS torto", "nps", true, null, new ScaleRangeDTO(1, 5)));

    for (var caso : casos) {
      client
          .post()
          .uri(uri())
          .contentType(MediaType.APPLICATION_JSON)
          .body(caso)
          .exchange()
          .expectStatus()
          .isBadRequest();
    }

    assertThat(storedQuestions()).isEmpty();
  }

  @Test
  @DisplayName("Reescrever preserva a chave estável e a posição")
  void reescreve_a_pergunta() {
    var created = add(freeText("Primeira"));

    var updated =
        client
            .put()
            .uri(uri() + "/" + created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                new UpdateQuestionRequestDTO(
                    "Primeira, corrigida",
                    "single_choice",
                    false,
                    List.of(new QuestionOptionDTO("Sim", "yes")),
                    null))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(QuestionResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(updated.key()).isEqualTo(created.key());
    assertThat(updated.id()).isEqualTo(created.id());
    assertThat(updated.position()).isEqualTo(1);
    assertThat(updated.statement()).isEqualTo("Primeira, corrigida");
    assertThat(updated.type()).isEqualTo("single_choice");
    assertThat(updated.required()).isFalse();
    assertThat(storedQuestions().getFirst().getQuestionKey()).isEqualTo(created.key());
  }

  @Test
  @DisplayName("Remover a do meio recompacta as posições, sem buraco nem repetição")
  void remove_recompactando() {
    var first = add(freeText("Primeira"));
    var second = add(freeText("Segunda"));
    var third = add(freeText("Terceira"));

    client.delete().uri(uri() + "/" + second.id()).exchange().expectStatus().isNoContent();

    assertThat(storedQuestions()).extracting(QuestionJpaEntity::getPosition).containsExactly(1, 2);
    assertThat(storedQuestions())
        .extracting(QuestionJpaEntity::getId)
        .containsExactly(first.id(), third.id());
  }

  @Test
  @DisplayName("Reordenar aplica a nova ordem e mantém as posições consecutivas")
  void reordena() {
    var first = add(freeText("Primeira"));
    var second = add(freeText("Segunda"));
    var third = add(freeText("Terceira"));

    var reordered =
        client
            .put()
            .uri(uri() + "/order")
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ReorderQuestionsRequestDTO(List.of(third.id(), first.id(), second.id())))
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(new ParameterizedTypeReference<List<QuestionResponseDTO>>() {})
            .returnResult()
            .getResponseBody();

    assertThat(reordered)
        .extracting(QuestionResponseDTO::statement)
        .containsExactly("Terceira", "Primeira", "Segunda");
    assertThat(reordered).extracting(QuestionResponseDTO::position).containsExactly(1, 2, 3);
    assertThat(storedQuestions())
        .extracting(QuestionJpaEntity::getPosition)
        .containsExactly(1, 2, 3);
    assertThat(storedQuestions())
        .extracting(QuestionJpaEntity::getStatement)
        .containsExactly("Terceira", "Primeira", "Segunda");
  }

  @Test
  @DisplayName("Permutação inexata devolve 400 e a ordem anterior fica intacta")
  void recusa_permutacao_inexata() {
    var first = add(freeText("Primeira"));
    add(freeText("Segunda"));

    client
        .put()
        .uri(uri() + "/order")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ReorderQuestionsRequestDTO(List.of(first.id())))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("question.order_invalid");

    assertThat(storedQuestions())
        .extracting(QuestionJpaEntity::getStatement)
        .containsExactly("Primeira", "Segunda");
  }

  @Test
  @DisplayName("Pergunta inexistente e fora do escopo devolvem 404")
  void recusa_pergunta_desconhecida() {
    add(freeText("Primeira"));
    var desconhecida = UUID.randomUUID().toString();

    client
        .delete()
        .uri(uri() + "/" + desconhecida)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("question.not_found");

    client
        .put()
        .uri(uri() + "/" + desconhecida)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new UpdateQuestionRequestDTO("Outro", "free_text", true, null, null))
        .exchange()
        .expectStatus()
        .isNotFound();

    assertThat(storedQuestions()).hasSize(1);
  }

  @Test
  @DisplayName("JSON malformado devolve 400 sem gravar nada")
  void recusa_json_malformado() {
    client
        .post()
        .uri(uri())
        .contentType(MediaType.APPLICATION_JSON)
        .body("{\"statement\":")
        .exchange()
        .expectStatus()
        .isBadRequest();

    assertThat(storedQuestions()).isEmpty();
  }
}
