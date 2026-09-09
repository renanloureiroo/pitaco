package com.renanloureiroo.pitaco.modules.collect.infra.gateways;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.mappers.ApplicationJpaMapper;
import com.renanloureiroo.pitaco.modules.app.infra.database.jpa.repositories.ApplicationJpaRepository;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.testsupport.annotations.E2E;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@E2E
@DisplayName("PublishedSurveyCatalogSurvey")
class PublishedSurveyCatalogSurveyTest {

  private static final String EVENT = "checkout.completed";
  private static final Instant WINDOW_START = Instant.parse("2026-09-08T12:00:00Z");
  private static final Instant WINDOW_END = Instant.parse("2026-09-09T12:00:00Z");

  @Autowired PublishedSurveyCatalog catalog;
  @Autowired SurveyRepository surveys;
  @Autowired SurveyVersionRepository versions;
  @Autowired ApplicationJpaRepository applications;
  @Autowired DatabaseCleaner database;

  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    database.clean();

    var application = ApplicationFactory.anApplication().build();
    applications.save(ApplicationJpaMapper.toJpa(application));
    applicationId = application.id();
  }

  @Test
  @DisplayName("A versão publicada corrente é candidata, com os critérios na mesma travessia")
  void versao_publicada_e_candidata() {
    publishedSurveyRuledBy(TriggerFactory.anEqualityRule("plano", "premium"));

    var candidates =
        catalog.candidatesFor(applicationId, EventName.of(EVENT), WINDOW_START.plusSeconds(60));

    assertThat(candidates).hasSize(1);
    assertThat(candidates.getFirst().criteria()).hasSize(1);
    assertThat(candidates.getFirst().criteria().getFirst().attribute()).isEqualTo("plano");
    assertThat(candidates.getFirst().rate().value()).isEqualTo(0.25);
  }

  @Test
  @DisplayName("Rascunho nunca aparece como candidato")
  void rascunho_nunca_aparece() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(survey.id())
        .triggeredBy(openTrigger())
        .buildSavedIn(versions);

    assertThat(catalog.candidatesFor(applicationId, EventName.of(EVENT), WINDOW_START.plusSeconds(60)))
        .isEmpty();
  }

  @Test
  @DisplayName("A janela é avaliada nos limites exatos: início inclusive, fim exclusive")
  void janela_avaliada_nos_limites() {
    publishedSurvey(applicationId);

    var event = EventName.of(EVENT);

    assertThat(catalog.candidatesFor(applicationId, event, WINDOW_START.minusMillis(1))).isEmpty();
    assertThat(catalog.candidatesFor(applicationId, event, WINDOW_START)).hasSize(1);
    assertThat(catalog.candidatesFor(applicationId, event, WINDOW_END.minusMillis(1))).hasSize(1);
    assertThat(catalog.candidatesFor(applicationId, event, WINDOW_END)).isEmpty();
  }

  @Test
  @DisplayName("Evento diferente não traz candidato")
  void evento_diferente_nao_traz_candidato() {
    publishedSurvey(applicationId);

    assertThat(
            catalog.candidatesFor(
                applicationId, EventName.of("outro.evento"), WINDOW_START.plusSeconds(60)))
        .isEmpty();
  }

  @Test
  @DisplayName("Versão de outra aplicação não é alcançada")
  void versao_de_outra_aplicacao_nao_e_alcancada() {
    var version = publishedSurvey(applicationId);
    var outra = ApplicationId.generate();

    assertThat(catalog.candidatesFor(outra, EventName.of(EVENT), WINDOW_START.plusSeconds(60)))
        .isEmpty();
    assertThat(catalog.publishedVersionOf(version.id(), outra)).isEmpty();
    assertThat(catalog.publishedVersionOf(version.id(), applicationId)).isPresent();
  }

  @Test
  @DisplayName("O conteúdo vem inteiro, com as perguntas em ordem de posição")
  void conteudo_vem_inteiro_e_em_ordem() {
    var version =
        publishedSurveyWith(
            QuestionFactory.aSingleChoiceQuestion().withStatement("Recomendaria?"),
            QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?"));

    var content = catalog.contentOf(version.id()).orElseThrow();

    assertThat(content.questions()).hasSize(2);
    assertThat(content.questions().getFirst().position()).isEqualTo(1);
    assertThat(content.questions().getFirst().type()).isEqualTo(QuestionType.SINGLE_CHOICE);
    assertThat(content.questions().getFirst().options()).hasSize(2);
    assertThat(content.questions().getLast().type()).isEqualTo(QuestionType.NPS);
    assertThat(content.questions().getLast().range()).isPresent();
  }

  @Test
  @DisplayName("Conteúdo de rascunho nunca é entregue")
  void conteudo_de_rascunho_nao_e_entregue() {
    var survey = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    var draft =
        SurveyVersionFactory.aVersion()
            .forSurvey(survey.id())
            .triggeredBy(openTrigger())
            .buildSavedIn(versions);

    assertThat(catalog.contentOf(draft.id())).isEmpty();
    assertThat(catalog.publishedVersionOf(draft.id(), applicationId)).isEmpty();
  }

  private SurveyVersion publishedSurvey(ApplicationId owner) {
    return SurveyVersionFactory.aVersion()
        .forSurvey(savedSurvey(owner).id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(openTrigger())
        .buildPublishedSavedIn(versions);
  }

  private SurveyVersion publishedSurveyRuledBy(SegmentationRule rule) {
    return SurveyVersionFactory.aVersion()
        .forSurvey(savedSurvey(applicationId).id())
        .withQuestions(QuestionFactory.aFreeTextQuestion())
        .triggeredBy(openTrigger())
        .ruledBy(rule)
        .buildPublishedSavedIn(versions);
  }

  private SurveyVersion publishedSurveyWith(QuestionFactory... questions) {
    return SurveyVersionFactory.aVersion()
        .forSurvey(savedSurvey(applicationId).id())
        .withQuestions(questions)
        .triggeredBy(openTrigger())
        .buildPublishedSavedIn(versions);
  }

  private Survey savedSurvey(ApplicationId owner) {
    return SurveyFactory.aPublishedSurvey().forApplication(owner).buildSavedIn(surveys);
  }

  private static TriggerFactory openTrigger() {
    return TriggerFactory.aTrigger()
        .forEvent(EVENT)
        .startingAt(WINDOW_START)
        .endingAt(WINDOW_END);
  }
}
