package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.SurveyCandidate;
import com.renanloureiroo.pitaco.modules.collect.domain.health.SuppressionReason;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPublishedSurveyCatalog;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRespondentRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySuppressionEventRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RecordSuppressionUseCase")
class RecordSuppressionUseCaseTest {

  private static final String DEVICE = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11";
  private static final String REFERENCE = "u-8f1c";

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemoryPublishedSurveyCatalog catalog = new InMemoryPublishedSurveyCatalog();
  private final InMemoryRespondentRepository respondents = new InMemoryRespondentRepository();
  private final InMemorySuppressionEventRepository suppressions =
      new InMemorySuppressionEventRepository();

  private RecordSuppressionUseCase useCase;
  private ApplicationId applicationId;
  private SurveyCandidate published;

  @BeforeEach
  void setUp() {
    useCase =
        new RecordSuppressionUseCase(
            applications, catalog, respondents, suppressions, Duration.ofHours(24));
    applicationId = applications.anActiveApplication();
    published = publishedIn(applicationId);
  }

  private SurveyCandidate publishedIn(ApplicationId owner) {
    var candidate =
        new SurveyCandidate(
            SurveyId.generate(),
            SurveyVersionId.generate(),
            1,
            1,
            SamplingRate.of(1.0),
            List.of(),
            Instant.parse("2026-01-01T00:00:00Z"),
            0,
            false);
    catalog
        .withCandidate(
            owner,
            EventName.of("checkout.completed"),
            Instant.parse("2026-01-01T00:00:00Z"),
            Optional.empty(),
            candidate)
        .withContent(
            new DeliverableSurvey(
                candidate.surveyId(),
                candidate.versionId(),
                1,
                List.of(
                    new DeliverableQuestion(
                        QuestionKey.generate(),
                        1,
                        "Recomendaria?",
                        QuestionType.NPS,
                        true,
                        List.of(),
                        Optional.of(ScaleRange.NPS)))));
    return candidate;
  }

  private RecordSuppressionUseCase.Input input(
      ApplicationId owner, SurveyCandidate candidate, Optional<String> reference) {
    return new RecordSuppressionUseCase.Input(
        owner.value(),
        candidate.surveyId().value(),
        candidate.versionId().value(),
        SuppressionReason.UNKNOWN_QUESTION_TYPE,
        reference,
        Optional.of(DEVICE),
        Optional.of("0.9.0"));
  }

  private RecordSuppressionUseCase.Input input() {
    return input(applicationId, published, Optional.empty());
  }

  @Test
  @DisplayName("Grava a supressão com motivo, versão do SDK e versão mínima calculada")
  void grava_a_supressao() {
    useCase.execute(input());

    assertThat(suppressions.findAll())
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getApplicationId()).isEqualTo(applicationId);
              assertThat(event.getSurveyId()).isEqualTo(published.surveyId());
              assertThat(event.getVersionId()).isEqualTo(published.versionId());
              assertThat(event.getReason()).isEqualTo(SuppressionReason.UNKNOWN_QUESTION_TYPE);
              assertThat(event.sdkVersion()).hasValueSatisfying(
                  version -> assertThat(version.value()).isEqualTo("0.9.0"));
              assertThat(event.getMinRequiredVersion().value()).isEqualTo("1.0.0");
              assertThat(event.dedupKey()).isPresent();
            });
  }

  @Test
  @DisplayName("Não cria respondente: quem nunca foi visto fica sem associação")
  void nao_cria_respondente() {
    useCase.execute(input());

    assertThat(respondents.isEmpty()).isTrue();
    assertThat(suppressions.findAll().getFirst().respondentId()).isEmpty();
  }

  @Test
  @DisplayName("Associa o respondente que já existe, pela mesma regra de identidade")
  void associa_respondente_existente() {
    var known =
        RespondentFactory.aRespondent()
            .forApplication(applicationId)
            .identifiedByReference(REFERENCE)
            .buildSavedIn(respondents);

    useCase.execute(input(applicationId, published, Optional.of(REFERENCE)));

    assertThat(suppressions.findAll().getFirst().respondentId()).contains(known.id());
  }

  @Test
  @DisplayName("A mesma supressão do mesmo dispositivo na mesma versão conta uma vez na janela")
  void deduplica_na_janela() {
    useCase.execute(input());
    useCase.execute(input());

    assertThat(suppressions.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("Pesquisa de outra aplicação é descartada em silêncio")
  void outra_aplicacao() {
    var other = applications.anActiveApplication();
    var alheia = publishedIn(other);

    useCase.execute(input(applicationId, alheia, Optional.empty()));

    assertThat(suppressions.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Versão que não pertence à pesquisa informada é descartada")
  void versao_de_outra_pesquisa() {
    var mixed =
        new SurveyCandidate(
            SurveyId.generate(),
            published.versionId(),
            1,
            1,
            SamplingRate.of(1.0),
            List.of(),
            Instant.now(),
            0,
            false);

    useCase.execute(input(applicationId, mixed, Optional.empty()));

    assertThat(suppressions.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Identificador malformado é descartado, sem erro")
  void identificador_malformado() {
    useCase.execute(
        new RecordSuppressionUseCase.Input(
            applicationId.value(),
            "nao-e-uuid",
            "tambem-nao",
            SuppressionReason.UNSUPPORTED_FEATURE,
            Optional.empty(),
            Optional.of(DEVICE),
            Optional.empty()));

    assertThat(suppressions.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Aplicação inativa descarta")
  void aplicacao_inativa() {
    var inactive = applications.anInactiveApplication();
    var candidate = publishedIn(inactive);

    useCase.execute(input(inactive, candidate, Optional.empty()));

    assertThat(suppressions.findAll()).isEmpty();
  }

  @Test
  @DisplayName("Versão do SDK malformada vira ausência, e a supressão é gravada")
  void versao_do_sdk_malformada() {
    useCase.execute(
        new RecordSuppressionUseCase.Input(
            applicationId.value(),
            published.surveyId().value(),
            published.versionId().value(),
            SuppressionReason.UNSUPPORTED_FEATURE,
            Optional.empty(),
            Optional.of(DEVICE),
            Optional.of("latest")));

    assertThat(suppressions.findAll().getFirst().sdkVersion()).isEmpty();
  }
}
