package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.repositories.ObservedAttributeRepository;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.ObservedAttributeResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("GET /applications/{applicationId}/attributes")
class ListObservedAttributesE2ETest {

  private static final Instant FIRST = Instant.parse("2026-09-10T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-11T11:00:00Z");

  @Autowired RestTestClient client;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ObservedAttributeRepository attributes;
  @Autowired Transactor transactor;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private String uri;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();
    uri = "/applications/" + applicationId.value() + "/attributes";
  }

  private void seen(ApplicationId owner, Map<String, String> values, Instant at) {
    transactor.runInTransaction(() -> attributes.record(owner, AttributeSnapshot.of(values), at));
  }

  private PageResponseDTO<ObservedAttributeResponseDTO> list(String query) {
    return client
        .get()
        .uri(uri + query)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(
            new ParameterizedTypeReference<PageResponseDTO<ObservedAttributeResponseDTO>>() {})
        .returnResult()
        .getResponseBody();
  }

  @Test
  @DisplayName("Devolve os atributos com os valores em ordem alfabética, como no banco")
  void caminho_feliz() {
    seen(applicationId, Map.of("plano", "pro"), FIRST);
    seen(applicationId, Map.of("plano", "free", "versao", "2.1"), SECOND);

    var page = list("");

    assertThat(page.total()).isEqualTo(2);
    assertThat(page.items())
        .extracting(ObservedAttributeResponseDTO::name)
        .containsExactly("plano", "versao");

    var plano = page.items().getFirst();
    assertThat(plano.firstSeenAt()).isEqualTo(FIRST);
    assertThat(plano.lastSeenAt()).isEqualTo(SECOND);
    assertThat(plano.values())
        .extracting(ObservedAttributeResponseDTO.Value::value)
        .containsExactly("free", "pro");
    assertThat(jdbc.sql("select count(*) from application_attribute_values").query(Long.class).single())
        .isEqualTo(3);
  }

  @Test
  @DisplayName("Visto de novo em poucos minutos, o registro não é reescrito")
  void escrita_amortizada() {
    seen(applicationId, Map.of("plano", "pro"), FIRST);
    seen(applicationId, Map.of("plano", "pro"), FIRST.plusSeconds(60));

    assertThat(list("").items().getFirst().lastSeenAt()).isEqualTo(FIRST);
  }

  @Test
  @DisplayName("No limite de valores distintos, valor novo não entra e o conhecido avança")
  void limite_de_valores() {
    seen(applicationId, Map.of("cidade", "c0"), FIRST);
    var attributeId =
        jdbc.sql("select id from application_attributes where name = 'cidade'")
            .query(String.class)
            .single();
    for (var index = 1; index < ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE; index++) {
      jdbc.sql(
              "insert into application_attribute_values (id, attribute_id, value, last_seen_at)"
                  + " values (:id, :attribute, :value, :at)")
          .param("id", UUID.randomUUID().toString())
          .param("attribute", attributeId)
          .param("value", "c" + index)
          .param("at", Timestamp.from(FIRST))
          .update();
    }

    seen(applicationId, Map.of("cidade", "nova"), SECOND);
    seen(applicationId, Map.of("cidade", "c1"), SECOND);

    var cidade = list("").items().getFirst();
    assertThat(cidade.values()).hasSize(ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE);
    assertThat(cidade.values())
        .extracting(ObservedAttributeResponseDTO.Value::value)
        .doesNotContain("nova");
    assertThat(cidade.values())
        .filteredOn(value -> value.value().equals("c1"))
        .singleElement()
        .satisfies(value -> assertThat(value.lastSeenAt()).isEqualTo(SECOND));
  }

  @Test
  @DisplayName("Não devolve atributo de outra aplicação, e aplicação sem nenhum devolve página vazia")
  void isola_as_aplicacoes() {
    var outra = ApplicationFactory.anApplication().withSlug("outra").build();
    applications.save(ApplicationJpaMapper.toJpa(outra));
    seen(outra.id(), Map.of("plano", "pro"), FIRST);

    var page = list("");

    assertThat(page.items()).isEmpty();
    assertThat(page.total()).isZero();
  }

  @Test
  @DisplayName("Aplicação desconhecida é 404, chave do SDK é 403 e paginação inválida é 400")
  void recusas() {
    client
        .get()
        .uri("/applications/" + UUID.randomUUID() + "/attributes")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .jsonPath("$.code")
        .isEqualTo("application.not_found");
    client
        .get()
        .uri(uri)
        .header("X-Pitaco-Key", "qualquer")
        .exchange()
        .expectStatus()
        .isForbidden();
    client.get().uri(uri + "?size=0").exchange().expectStatus().isBadRequest();
  }
}
