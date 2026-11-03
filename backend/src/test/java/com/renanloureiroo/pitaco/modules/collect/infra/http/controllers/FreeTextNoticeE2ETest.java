package com.renanloureiroo.pitaco.modules.collect.infra.http.controllers;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApiKey;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.ApiKeyLabel;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApiKeyJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApiKeyJpaRepository;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityRequestDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.EligibilityResponseDTO;
import com.renanloureiroo.pitaco.modules.collect.infra.http.dtos.RespondentDTO;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.FreeTextNotice;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyDetailResponseDTO;
import com.renanloureiroo.pitaco.modules.survey.infra.http.dtos.SurveyResponseDTO;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.client.RestTestClient;

@E2E
@DisplayName("Aviso de dado pessoal no texto livre: autoria, entrega e duplicação")
class FreeTextNoticeE2ETest {

  private static final String HEADER = "X-Pitaco-Key";
  private static final String EVENT = "checkout.completed";

  @Autowired RestTestClient client;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired ApiKeyJpaRepository apiKeys;
  @Autowired JdbcClient jdbc;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;
  private String key;
  private String surveyId;

  private record Stored(boolean enabled, String text) {}

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();

    var issued = ApiKey.issue(applicationId, ApiKeyLabel.of("app iOS"));
    apiKeys.save(ApiKeyJpaMapper.toJpa(issued.apiKey()));
    key = issued.plainSecret();

    var survey =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .published(1)
            .inLifecycle(SurveyLifecycle.PUBLISHED)
            .buildSavedIn(surveys);
    surveyId = survey.id().value();
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion().withStatement("O que achou?"))
        .triggeredBy(TriggerFactory.aTrigger().forEvent(EVENT).withRate(1.0))
        .buildPublishedSavedIn(versions);
  }

  private String surveyUri() {
    return "/applications/" + applicationId.value() + "/surveys/" + surveyId;
  }

  private RestTestClient.ResponseSpec patch(String body) {
    return client
        .patch()
        .uri(surveyUri())
        .contentType(MediaType.APPLICATION_JSON)
        .body(body)
        .exchange();
  }

  private SurveyResponseDTO patchOk(String body) {
    return patch(body)
        .expectStatus()
        .isOk()
        .expectBody(SurveyResponseDTO.class)
        .returnResult()
        .getResponseBody();
  }

  private EligibilityResponseDTO.FreeTextNoticeDTO delivered() {
    return client
        .post()
        .uri("/collect/eligibility")
        .header(HEADER, key)
        .contentType(MediaType.APPLICATION_JSON)
        .body(new EligibilityRequestDTO(EVENT, new RespondentDTO("u-1", null), null))
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(EligibilityResponseDTO.class)
        .returnResult()
        .getResponseBody()
        .survey()
        .freeTextNotice();
  }

  private Stored stored(String id) {
    return jdbc.sql(
            "select free_text_notice_enabled, free_text_notice_text from surveys where id = :id")
        .param("id", id)
        .query((rs, row) -> new Stored(rs.getBoolean(1), rs.getString(2)))
        .single();
  }

  @Test
  @DisplayName("Sem configuração, o SDK recebe o aviso ligado com o texto padrão")
  void padrao() {
    var notice = delivered();

    assertThat(notice.enabled()).isTrue();
    assertThat(notice.text()).isEqualTo(FreeTextNotice.DEFAULT_TEXT);
  }

  @Test
  @DisplayName("Texto próprio é gravado, devolvido e entregue ao SDK")
  void texto_proprio() {
    var body = patchOk("{\"freeTextNoticeText\":\"Não escreva seu telefone.\"}");

    assertThat(body.freeTextNotice().enabled()).isTrue();
    assertThat(body.freeTextNotice().customText()).isEqualTo("Não escreva seu telefone.");
    assertThat(body.freeTextNotice().text()).isEqualTo("Não escreva seu telefone.");
    assertThat(body.freeTextNotice().defaultText()).isEqualTo(FreeTextNotice.DEFAULT_TEXT);
    assertThat(stored(surveyId)).isEqualTo(new Stored(true, "Não escreva seu telefone."));
    assertThat(delivered().text()).isEqualTo("Não escreva seu telefone.");
  }

  @Test
  @DisplayName("Desligado, o SDK recebe o aviso desligado e sem texto")
  void desligado() {
    patchOk("{\"freeTextNoticeEnabled\":false}");

    var notice = delivered();
    assertThat(notice.enabled()).isFalse();
    assertThat(notice.text()).isNull();
    assertThat(stored(surveyId).enabled()).isFalse();
  }

  @Test
  @DisplayName("Texto nulo volta ao padrão")
  void volta_ao_padrao() {
    patchOk("{\"freeTextNoticeText\":\"Outro\"}");

    var body = patchOk("{\"freeTextNoticeText\":null}");

    assertThat(body.freeTextNotice().customText()).isNull();
    assertThat(body.freeTextNotice().text()).isEqualTo(FreeTextNotice.DEFAULT_TEXT);
    assertThat(stored(surveyId)).isEqualTo(new Stored(true, null));
  }

  @Test
  @DisplayName("Texto em branco, longo demais ou aviso nulo é 400, e nada muda")
  void validacao() {
    patch("{\"freeTextNoticeText\":\"   \"}").expectStatus().isBadRequest();
    patch("{\"freeTextNoticeText\":\"" + "a".repeat(201) + "\"}").expectStatus().isBadRequest();
    patch("{\"freeTextNoticeEnabled\":null}").expectStatus().isBadRequest();

    assertThat(stored(surveyId)).isEqualTo(new Stored(true, null));
  }

  @Test
  @DisplayName("O detalhe da pesquisa mostra o aviso, e a duplicação o leva junto")
  void detalhe_e_duplicacao() {
    patchOk("{\"freeTextNoticeEnabled\":false,\"freeTextNoticeText\":\"Sem dado pessoal\"}");

    var detail =
        client.get().uri(surveyUri()).exchange().expectStatus().isOk()
            .expectBody(SurveyDetailResponseDTO.class).returnResult().getResponseBody();
    assertThat(detail.freeTextNotice().enabled()).isFalse();
    assertThat(detail.freeTextNotice().customText()).isEqualTo("Sem dado pessoal");

    var copy =
        client
            .post()
            .uri(surveyUri() + "/duplicate")
            .contentType(MediaType.APPLICATION_JSON)
            .body("{}")
            .exchange()
            .expectStatus()
            .isCreated()
            .expectBody(SurveyResponseDTO.class)
            .returnResult()
            .getResponseBody();

    assertThat(copy.freeTextNotice().enabled()).isFalse();
    assertThat(stored(copy.id())).isEqualTo(new Stored(false, "Sem dado pessoal"));
  }
}
