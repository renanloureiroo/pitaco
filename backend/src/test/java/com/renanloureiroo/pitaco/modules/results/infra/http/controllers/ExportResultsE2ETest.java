package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/surveys/{surveyId}/results/export")
class ExportResultsE2ETest extends ResultsE2ESupport {

  @Autowired RestTestClient client;
  @Autowired DatabaseCleaner database;

  private String uri;

  @BeforeEach
  void setUp() {
    database.clean();
    seedSurvey(ApplicationFactory.anApplication());
    uri = base + "/export";
  }

  private String csv(String query) {
    var result =
        client
            .get()
            .uri(uri + query)
            .exchange()
            .expectStatus()
            .isOk()
            .expectHeader()
            .contentTypeCompatibleWith("text/csv")
            .expectBody(String.class)
            .returnResult();

    assertThat(result.getResponseHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
        .contains("attachment")
        .contains("resultados-" + surveyId.value() + ".csv");

    return result.getResponseBody();
  }

  // Leitor RFC 4180 mínimo: é o que prova que o arquivo abre íntegro numa planilha.
  static List<List<String>> parse(String csv) {
    var rows = new ArrayList<List<String>>();
    var row = new ArrayList<String>();
    var cell = new StringBuilder();
    var quoted = false;
    var content = csv.startsWith("﻿") ? csv.substring(1) : csv;

    for (var index = 0; index < content.length(); index++) {
      var character = content.charAt(index);
      if (quoted) {
        if (character == '"') {
          if (index + 1 < content.length() && content.charAt(index + 1) == '"') {
            cell.append('"');
            index++;
          } else {
            quoted = false;
          }
        } else {
          cell.append(character);
        }
      } else if (character == '"') {
        quoted = true;
      } else if (character == ',') {
        row.add(cell.toString());
        cell.setLength(0);
      } else if (character == '\r') {
        continue;
      } else if (character == '\n') {
        row.add(cell.toString());
        rows.add(List.copyOf(row));
        row.clear();
        cell.setLength(0);
      } else {
        cell.append(character);
      }
    }
    return rows;
  }

  @Test
  @DisplayName("Uma linha por exibição, inclusive dispensada e abandonada, íntegra com vírgula, aspas e quebra de linha")
  void exporta_integro() {
    var completed = display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of("plano", "pro"));
    number(completed, nps, 10);
    options(completed, choice, "yes");
    text(completed, text, "Disse \"ok\", mas\nfaltou algo", DAY_ONE.plusSeconds(20));
    display(DisplayOutcome.DISMISSED, DAY_TWO, Map.of());
    display(DisplayOutcome.STARTED, DAY_ONE.plusSeconds(1), Map.of("cidade", "SP"));

    var csv = csv("");

    assertThat(csv).startsWith("﻿");
    var rows = parse(csv);
    assertThat(rows).hasSize(4);

    var header = rows.get(0);
    assertThat(header.subList(0, 7))
        .containsExactly("displayId", "respondentReference", "versionNumber", "outcome", "openedAt", "closedAt", "sdkVersion");
    assertThat(header).contains("attribute:cidade", "attribute:plano");
    assertThat(header).anyMatch(column -> column.startsWith("O que achou? ["));
    assertThat(rows).allSatisfy(row -> assertThat(row).hasSize(header.size()));

    var first = rows.get(1);
    assertThat(first.get(0)).isEqualTo(completed.value());
    assertThat(first.get(1)).startsWith("u-");
    assertThat(first.get(3)).isEqualTo("COMPLETED");
    assertThat(first.get(header.indexOf("attribute:plano"))).isEqualTo("pro");
    assertThat(first.get(header.indexOf("attribute:cidade"))).isEmpty();
    assertThat(first).contains("10", "yes", "Disse \"ok\", mas\nfaltou algo");

    assertThat(rows.get(2).get(3)).isEqualTo("ABANDONED");
    assertThat(rows.get(3).get(3)).isEqualTo("DISMISSED");
    assertThat(rows.get(3).get(5)).isNotEmpty();
  }

  @Test
  @DisplayName("Respeita o recorte de período e nunca carrega mais que a referência opaca do respondente")
  void respeita_o_recorte() {
    display(DisplayOutcome.COMPLETED, DAY_ONE, Map.of());
    display(DisplayOutcome.COMPLETED, DAY_TWO, Map.of());

    var rows = parse(csv("?from=" + DAY_TWO));

    assertThat(rows).hasSize(2);
    assertThat(rows.get(1).get(4)).isEqualTo(DAY_TWO.toString());
    assertThat(rows.get(0)).doesNotContain("identityKind", "email");
  }

  @Test
  @DisplayName("Sem exibição, sai só o cabeçalho")
  void sem_exibicao() {
    assertThat(parse(csv(""))).hasSize(1);
  }

  @Test
  @DisplayName("Recorte inválido responde 400, chave 403, pesquisa inexistente 404 — antes de qualquer byte do arquivo")
  void recusas_antes_do_arquivo() {
    client.get().uri(uri + "?version=0").exchange().expectStatus().isBadRequest()
        .expectBody().jsonPath("$.code").isEqualTo("request.invalid");
    client.get().uri(uri).header(HEADER, "pit_qualquer").exchange().expectStatus().isForbidden();
    client
        .get()
        .uri("/applications/" + applicationId.value() + "/surveys/" + UUID.randomUUID() + "/results/export")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("survey.not_found");
  }

  @Test
  @DisplayName("Volume maior que um lote sai inteiro, em ordem de abertura")
  void mais_de_um_lote() {
    var start = Instant.parse("2026-08-01T00:00:00Z");
    for (var index = 0; index < 505; index++) {
      display(DisplayOutcome.COMPLETED, start.plusSeconds(index), Map.of());
    }

    var rows = parse(csv(""));

    assertThat(rows).hasSize(506);
    assertThat(rows.get(1).get(4)).isEqualTo(start.toString());
    assertThat(rows.get(505).get(4)).isEqualTo(start.plusSeconds(504).toString());
  }
}
