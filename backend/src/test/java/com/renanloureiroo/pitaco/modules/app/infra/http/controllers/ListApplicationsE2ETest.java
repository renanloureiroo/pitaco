package com.renanloureiroo.pitaco.modules.app.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Application;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.entities.ApplicationJpaEntity;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.http.dtos.ApplicationSummaryResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications")
class ListApplicationsE2ETest {

  private static final String URI = "/applications";

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired DatabaseCleaner database;

  @BeforeEach
  void setUp() {
    database.clean();
  }

  private ApplicationJpaEntity save(Application application) {
    return applications.save(ApplicationJpaMapper.toJpa(application));
  }

  private PageResponseDTO<ApplicationSummaryResponseDTO> list(String uri) {
    return client
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(
            new ParameterizedTypeReference<PageResponseDTO<ApplicationSummaryResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Lista ativas e inativas, e cada linha bate com o que está gravado no banco")
  void lista_ativas_e_inativas() {
    var active = save(ApplicationFactory.anApplication().withSlug("ativa").build());
    var inactive = save(ApplicationFactory.anApplication().withSlug("inativa").inactive().build());

    var page = list(URI);

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.page()).isZero();
    assertThat(page.size()).isEqualTo(20);
    assertThat(page.totalPages()).isEqualTo(1);
    assertThat(page.items())
        .extracting(ApplicationSummaryResponseDTO::id)
        .containsExactlyInAnyOrder(active.getId(), inactive.getId());

    var stored = applications.findById(active.getId()).orElseThrow();
    var item =
        page.items().stream()
            .filter(each -> each.id().equals(active.getId()))
            .findFirst()
            .orElseThrow();
    assertThat(item.slug()).isEqualTo(stored.getSlug());
    assertThat(item.name()).isEqualTo(stored.getName());
    assertThat(item.createdAt()).isEqualTo(stored.getCreatedAt());
    assertThat(item.status()).isEqualTo("active");
    assertThat(
            page.items().stream()
                .filter(each -> each.id().equals(inactive.getId()))
                .findFirst()
                .orElseThrow()
                .status())
        .isEqualTo("inactive");
  }

  @Test
  @DisplayName("Da mais recente para a mais antiga (FR-007)")
  void ordena_da_mais_recente_para_a_mais_antiga() {
    var oldest = save(batch(0));
    var middle = save(batch(1));
    var newest = save(batch(2));

    var page = list(URI);

    assertThat(page.items())
        .extracting(ApplicationSummaryResponseDTO::id)
        .containsExactly(newest.getId(), middle.getId(), oldest.getId());
  }

  private Application batch(int position) {
    return ApplicationFactory.anApplication()
        .withName("App " + position)
        .createdAt(ApplicationFactory.BATCH_FIRST_CREATED_AT.plusSeconds(position))
        .build();
  }

  @Test
  @DisplayName("Filtra por cada estado, com o total refletindo o filtro")
  void filtra_por_estado() {
    var active = save(ApplicationFactory.anApplication().withSlug("ativa").build());
    var inactive = save(ApplicationFactory.anApplication().withSlug("inativa").inactive().build());

    var onlyActive = list(URI + "?status=active");
    var onlyInactive = list(URI + "?status=inactive");

    assertThat(onlyActive.items())
        .extracting(ApplicationSummaryResponseDTO::id)
        .containsExactly(active.getId());
    assertThat(onlyActive.total()).isEqualTo(1);
    assertThat(onlyInactive.items())
        .extracting(ApplicationSummaryResponseDTO::id)
        .containsExactly(inactive.getId());
    assertThat(onlyInactive.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Percorrer as páginas não repete nem omite nenhuma aplicação")
  void percorre_as_paginas() {
    var saved = List.of(batch(0), batch(1), batch(2), batch(3), batch(4)).stream()
        .map(this::save)
        .map(ApplicationJpaEntity::getId)
        .toList();

    var first = list(URI + "?page=0&size=2");
    var second = list(URI + "?page=1&size=2");
    var third = list(URI + "?page=2&size=2");

    assertThat(first.totalPages()).isEqualTo(3);
    assertThat(
            Stream.of(first, second, third)
                .flatMap(page -> page.items().stream())
                .map(ApplicationSummaryResponseDTO::id))
        .doesNotHaveDuplicates()
        .containsExactlyInAnyOrderElementsOf(saved);
  }

  @Test
  @DisplayName("Página além do fim devolve vazio com o total correto, nunca erro (FR-008)")
  void pagina_alem_do_fim() {
    save(batch(0));
    save(batch(1));

    var beyond = list(URI + "?page=9&size=2");

    assertThat(beyond.items()).isEmpty();
    assertThat(beyond.total()).isEqualTo(2);
  }

  @Test
  @DisplayName("Sem nenhuma aplicação devolve lista vazia com total 0")
  void conjunto_vazio() {
    var page = list(URI);

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
    assertThat(page.totalPages()).isZero();
  }

  @Test
  @DisplayName("Parâmetros de página fora dos limites e estado desconhecido respondem 400")
  void recusa_parametros_invalidos() {
    expectBadRequest(URI + "?page=-1", "page");
    expectBadRequest(URI + "?size=0", "size");
    expectBadRequest(URI + "?size=101", "size");
    expectBadRequest(URI + "?status=arquivada", "status");
  }

  @Test
  @DisplayName("Chave de aplicação na superfície administrativa responde 403")
  void recusa_chave_de_aplicacao() {
    client
        .get()
        .uri(URI)
        .header("X-Pitaco-Key", "pit_qualquer")
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("api_key.forbidden_surface");
  }

  @Test
  @DisplayName("Listar não muda nada no banco, nem nos caminhos de falha (FR-016)")
  void nao_muda_o_estado() {
    var application = save(ApplicationFactory.anApplicationWithPolicies().build());
    var before = applications.findById(application.getId()).orElseThrow();
    var countBefore = applications.count();

    list(URI);
    list(URI + "?status=inactive");
    expectBadRequest(URI + "?size=0", "size");

    var after = applications.findById(application.getId()).orElseThrow();
    assertThat(applications.count()).isEqualTo(countBefore);
    assertThat(after.getName()).isEqualTo(before.getName());
    assertThat(after.getSlug()).isEqualTo(before.getSlug());
    assertThat(after.getStatus()).isEqualTo(Status.ACTIVE);
    assertThat(after.getCreatedAt()).isEqualTo(before.getCreatedAt());
    assertThat(after.getUpdatedAt()).isEqualTo(before.getUpdatedAt());
    assertThat(after.getQuietPeriodDays()).isEqualTo(before.getQuietPeriodDays());
    assertThat(after.getRetentionDays()).isEqualTo(before.getRetentionDays());
    assertThat(after.getOpenTextRetentionDays()).isEqualTo(before.getOpenTextRetentionDays());
  }

  private void expectBadRequest(String uri, String field) {
    client
        .get()
        .uri(uri)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("request.invalid")
        .jsonPath("$.errors." + field)
        .exists();
  }
}
