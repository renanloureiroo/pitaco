package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.OpenAnswerResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/surveys/{surveyId}/results/open-answers")
class OpenAnswersE2ETest extends ResultsE2ESupport {

  private static final int RETENTION_DAYS = 30;

  @Autowired RestTestClient client;
  @Autowired DatabaseCleaner database;

  private String uri;

  @BeforeEach
  void setUp() {
    database.clean();
    seedSurvey(ApplicationFactory.anApplication().withOpenTextRetention(RETENTION_DAYS));
    uri = base + "/open-answers";
  }

  private PageResponseDTO<OpenAnswerResponseDTO> list(String query) {
    return client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(new ParameterizedTypeReference<PageResponseDTO<OpenAnswerResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Lista da mais recente para a mais antiga, com o contexto da mesma exibição, e busca pelo termo")
  void lista_busca_e_contexto() {
    var now = Instant.now();
    var first = display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of());
    number(first, nps, 9);
    options(first, choice, "yes");
    text(first, text, "Achei confuso o checkout", now.minus(Duration.ofDays(2)));

    var second = display(DisplayOutcome.DISMISSED, DAY_TWO, Map.of());
    text(second, text, "Muito bom, 100% recomendo", now.minus(Duration.ofDays(1)));

    text(display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of()), text, "confuso e vencido", now.minus(Duration.ofDays(RETENTION_DAYS + 1)));

    var all = list("");
    assertThat(all.total()).isEqualTo(2);
    assertThat(all.items()).extracting(OpenAnswerResponseDTO::text)
        .containsExactly("Muito bom, 100% recomendo", "Achei confuso o checkout");

    var withContext = all.items().get(1);
    assertThat(withContext.displayId()).isEqualTo(first.value());
    assertThat(withContext.statement()).isEqualTo("O que achou?");
    assertThat(withContext.context())
        .extracting(OpenAnswerResponseDTO.AnswerContextDTO::statement, OpenAnswerResponseDTO.AnswerContextDTO::value)
        .containsExactlyInAnyOrder(
            org.assertj.core.groups.Tuple.tuple("De 0 a 10?", "9"),
            org.assertj.core.groups.Tuple.tuple("Recomendaria?", "Rótulo yes"));

    assertThat(list("?q=CONFUSO").items()).extracting(OpenAnswerResponseDTO::text)
        .containsExactly("Achei confuso o checkout");
    assertThat(list("?q=100%").items()).extracting(OpenAnswerResponseDTO::text)
        .containsExactly("Muito bom, 100% recomendo");
    assertThat(list("?q=nada-disso").items()).isEmpty();
  }

  @Test
  @DisplayName("Pagina e respeita o recorte de período e de atributo")
  void pagina_e_recorta() {
    var now = Instant.now();
    for (var index = 0; index < 3; index++) {
      text(
          display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of("plano", "pro")),
          text,
          "resposta " + index,
          now.minusSeconds(100 - index));
    }
    text(display(DisplayOutcome.COMPLETED, DAY_TWO, Map.of()), text, "de outro dia", now.minusSeconds(10));

    var firstPage = list("?size=2");
    assertThat(firstPage.total()).isEqualTo(4);
    assertThat(firstPage.totalPages()).isEqualTo(2);
    assertThat(firstPage.items()).hasSize(2);
    assertThat(list("?size=2&page=1").items()).hasSize(2);

    assertThat(list("?attribute=plano&attributeValue=pro").total()).isEqualTo(3);
    assertThat(list("?attribute=plano").items()).extracting(OpenAnswerResponseDTO::text).containsExactly("de outro dia");
    assertThat(list("?from=" + DAY_TWO).total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Paginação fora dos limites, termo longo e recorte inválido respondem 400")
  void recusa_parametros_invalidos() {
    for (var query : new String[] {"?page=-1", "?size=0", "?size=101", "?q=" + "x".repeat(201), "?version=0"}) {
      client
          .get()
          .uri(uri + query)
          .exchange()
          .expectStatus()
          .isBadRequest()
          .expectHeader()
          .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
    }
  }

  @Test
  @DisplayName("Chave de aplicação responde 403 e pesquisa inexistente responde 404")
  void recusa_chave_e_escopo() {
    client.get().uri(uri).header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client
        .get()
        .uri("/applications/" + applicationId.value() + "/surveys/" + UUID.randomUUID() + "/results/open-answers")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_found");
  }
}
