package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO.QuestionResultDTO;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import java.time.Instant;
import java.time.LocalDate;
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
@DisplayName("GET /applications/{applicationId}/surveys/{surveyId}/results")
class SurveyResultsE2ETest extends ResultsE2ESupport {

  @Autowired RestTestClient client;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() {
    database.clean();
    seedSurvey(ApplicationFactory.anApplication());
  }

  private SurveyResultsResponseDTO results(String query) {
    return client
        .get()
        .uri(base + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(SurveyResultsResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private QuestionResultDTO question(SurveyResultsResponseDTO body, String key) {
    return body.questions().stream().filter(question -> question.key().equals(key)).findFirst().orElseThrow();
  }

  // Concluída com nota 10 e "sim"; concluída com nota 3 e avaliação 4; dispensada com resposta
  // parcial; abandonada e em andamento sem resposta.
  private void seedResponses() {
    var promoter = display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of("plano", "pro"));
    number(promoter, nps, 10);
    options(promoter, choice, "yes");
    skipped(promoter, rating);

    var detractor = display(DisplayOutcome.COMPLETED, DAY_TWO, Map.of("plano", "free"));
    number(detractor, nps, 3);
    number(detractor, rating, 4);
    text(detractor, text, "Achei confuso", DAY_TWO.plusSeconds(20));

    var dismissed = display(DisplayOutcome.DISMISSED, DAY_TWO, Map.of());
    number(dismissed, nps, 7);

    display(DisplayOutcome.STARTED, DAY_ONE, Map.of());
    display(DisplayOutcome.STARTED, Instant.now(), Map.of());
  }

  @Test
  @DisplayName("Os números conferem com o que foi semeado, e a taxa é a definida na tela")
  void numeros_conferem() {
    seedResponses();

    var body = results("");

    var rate = body.responseRate();
    assertThat(rate.displayed()).isEqualTo(5);
    assertThat(rate.completed()).isEqualTo(2);
    assertThat(rate.dismissed()).isEqualTo(1);
    assertThat(rate.abandoned()).isEqualTo(1);
    assertThat(rate.inProgress()).isEqualTo(1);
    assertThat(rate.rate()).isCloseTo(0.4, within(0.0001));
    assertThat(rate.definition()).contains("concluídas ÷ exibidas");
    assertThat(rate.timeline())
        .anySatisfy(
            point -> {
              assertThat(point.day()).isEqualTo(LocalDate.of(2026, 9, 2));
              assertThat(point.displayed()).isEqualTo(2);
              assertThat(point.completed()).isEqualTo(1);
            });

    assertThat(body.everPublished()).isTrue();
    assertThat(body.sampleSize()).isEqualTo(3);
    assertThat(body.smallSample()).isTrue();
    assertThat(body.questions()).extracting(QuestionResultDTO::position).containsExactly(1, 2, 3, 4);

    var npsResult = question(body, nps.value());
    assertThat(npsResult.type()).isEqualTo("nps");
    assertThat(npsResult.answered()).isEqualTo(3);
    assertThat(npsResult.aggregate().kind()).isEqualTo("nps");
    assertThat(npsResult.aggregate().promoters()).isEqualTo(1);
    assertThat(npsResult.aggregate().passives()).isEqualTo(1);
    assertThat(npsResult.aggregate().detractors()).isEqualTo(1);
    assertThat(npsResult.aggregate().score()).isCloseTo(0.0, within(0.0001));
    assertThat(npsResult.aggregate().distribution()).hasSize(11);

    var choiceResult = question(body, choice.value());
    assertThat(choiceResult.answered()).isEqualTo(1);
    assertThat(choiceResult.aggregate().kind()).isEqualTo("choice");
    assertThat(choiceResult.aggregate().options())
        .extracting(option -> option.value(), option -> option.count())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("yes", 1L), org.assertj.core.groups.Tuple.tuple("no", 0L));
    assertThat(choiceResult.aggregate().options().get(0).share()).isCloseTo(1.0, within(0.0001));

    var ratingResult = question(body, rating.value());
    assertThat(ratingResult.answered()).isEqualTo(1);
    assertThat(ratingResult.skipped()).isEqualTo(1);
    assertThat(ratingResult.aggregate().kind()).isEqualTo("numeric");
    assertThat(ratingResult.aggregate().average()).isCloseTo(4.0, within(0.0001));
    assertThat(ratingResult.aggregate().distribution()).hasSize(5);

    var textResult = question(body, text.value());
    assertThat(textResult.answered()).isEqualTo(1);
    assertThat(textResult.aggregate().kind()).isEqualTo("text");

    assertThat(body.attributes()).singleElement().satisfies(
        catalog -> {
          assertThat(catalog.name()).isEqualTo("plano");
          assertThat(catalog.values()).extracting(value -> value.value()).containsExactly("free", "pro");
        });
  }

  @Test
  @DisplayName("Pergunta sem nenhuma resposta vem sem agregado, distinta de zero")
  void pergunta_sem_resposta() {
    var only = display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of());
    number(only, nps, 9);

    var body = results("");

    assertThat(question(body, nps.value()).aggregate()).isNotNull();
    var untouched = question(body, choice.value());
    assertThat(untouched.answered()).isZero();
    assertThat(untouched.skipped()).isZero();
    assertThat(untouched.aggregate()).isNull();
  }

  @Test
  @DisplayName("Recorte por período, por atributo e por ausência de atributo vale para tudo ao mesmo tempo")
  void recortes() {
    seedResponses();

    var dayTwo = results("?from=" + DAY_TWO + "&to=" + DAY_TWO.plusSeconds(3600));
    assertThat(dayTwo.responseRate().displayed()).isEqualTo(2);
    assertThat(question(dayTwo, nps.value()).answered()).isEqualTo(2);
    assertThat(dayTwo.filter().from()).isEqualTo(DAY_TWO);

    var pro = results("?attribute=plano&attributeValue=pro");
    assertThat(pro.responseRate().displayed()).isEqualTo(1);
    assertThat(question(pro, nps.value()).aggregate().promoters()).isEqualTo(1);
    assertThat(pro.filter().attributeAbsent()).isFalse();

    var absent = results("?attribute=plano");
    assertThat(absent.responseRate().displayed()).isEqualTo(3);
    assertThat(absent.responseRate().dismissed()).isEqualTo(1);
    assertThat(absent.filter().attributeAbsent()).isTrue();
    assertThat(absent.smallSample()).isTrue();
  }

  @Test
  @DisplayName("Sem versão consolida; com versão, só as perguntas e exibições daquela versão")
  void por_versao() {
    seedResponses();
    var second = anotherPublishedVersion(2);
    var onSecond = displayOn(second.id(), DisplayOutcome.COMPLETED, DAY_TWO, Map.of());
    number(onSecond, second.getQuestions().get(0).getKey(), 10);

    var consolidated = results("");
    assertThat(consolidated.responseRate().displayed()).isEqualTo(6);
    assertThat(consolidated.questions()).hasSize(5);

    var v2 = results("?version=2");
    assertThat(v2.responseRate().displayed()).isEqualTo(1);
    assertThat(v2.questions()).singleElement().satisfies(question -> assertThat(question.statement()).contains("v2"));
    assertThat(v2.filter().version()).isEqualTo(2);
  }

  @Test
  @DisplayName("Pesquisa publicada sem exibição devolve zeros e amostra pequena; nunca publicada, estado vazio")
  void sem_exibicao_e_nunca_publicada() {
    var body = results("");
    assertThat(body.everPublished()).isTrue();
    assertThat(body.responseRate().displayed()).isZero();
    assertThat(body.responseRate().rate()).isNull();
    assertThat(body.questions()).hasSize(4);
    assertThat(body.questions()).allSatisfy(question -> assertThat(question.aggregate()).isNull());

    var draft = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    var neverPublished =
        client
            .get()
            .uri("/applications/" + applicationId.value() + "/surveys/" + draft.id().value() + "/results")
            .exchange()
            .expectStatus()
            .isOk()
            .expectBody(SurveyResultsResponseDTO.class)
            .returnResult()
            .getResponseBody();
    assertThat(neverPublished.everPublished()).isFalse();
    assertThat(neverPublished.questions()).isEmpty();
  }

  @Test
  @DisplayName("Recorte inválido responde 400 apontando o campo")
  void recusa_recorte_invalido() {
    for (var query :
        new String[] {
          "?from=" + DAY_TWO + "&to=" + DAY_ONE,
          "?from=ontem",
          "?version=0",
          "?attributeValue=pro",
          "?attribute= "
        }) {
      client
          .get()
          .uri(base + query)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
          .expectBody()
          .jsonPath("$.code")
          .isEqualTo("request.invalid");
    }
  }

  @Test
  @DisplayName("Chave de aplicação responde 403; pesquisa fora do escopo responde 404")
  void recusa_chave_e_escopo() {
    client
        .get()
        .uri(base)
        .header(HEADER, "pit_qualquer")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");

    var other = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper.toJpa(other));
    var alheia =
        SurveyFactory.aSurvey().forApplication(other.id()).published(1).inLifecycle(SurveyLifecycle.PUBLISHED).buildSavedIn(surveys);

    for (var target :
        new String[] {
          "/applications/" + applicationId.value() + "/surveys/" + UUID.randomUUID() + "/results",
          "/applications/" + applicationId.value() + "/surveys/" + alheia.id().value() + "/results",
          "/applications/" + applicationId.value() + "/surveys/nao-e-id/results",
          "/applications/" + UUID.randomUUID() + "/surveys/" + surveyId.value() + "/results"
        }) {
      client
          .get()
          .uri(target)
          .exchange()
          .expectStatus()
          .isNotFound()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
  }

  @Test
  @DisplayName("Nenhuma leitura altera o estado gravado")
  void nao_altera_o_estado() {
    seedResponses();

    results("");
    results("?attribute=plano");
    client.get().uri(base + "?version=0").exchange().expectStatus().isBadRequest();

    assertThat(jdbc.sql("select count(*) from survey_displays").query(Long.class).single()).isEqualTo(5);
    assertThat(jdbc.sql("select count(*) from survey_answers").query(Long.class).single()).isEqualTo(7);
  }
}
