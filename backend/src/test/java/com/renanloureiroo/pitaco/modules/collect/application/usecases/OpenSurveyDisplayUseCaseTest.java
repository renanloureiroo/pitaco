package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.collect.application.errors.DisplayIdentifierConflict;
import com.renanloureiroo.pitaco.modules.collect.application.errors.SurveyVersionNotDeliverable;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.SurveyCandidate;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPublishedSurveyCatalog;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRespondentRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("OpenSurveyDisplayUseCase")
class OpenSurveyDisplayUseCaseTest {

  private static final String REFERENCE = "u-8f1c";
  private static final String DEVICE = "0d0f8a5e-1f1b-4c2b-9a2f-3f0f2b7d5c11";
  private static final int COMPARABILITY_GROUP = 2;

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemoryPublishedSurveyCatalog catalog = new InMemoryPublishedSurveyCatalog();
  private final InMemoryRespondentRepository respondents = new InMemoryRespondentRepository();
  private final InMemorySurveyDisplayRepository displays = new InMemorySurveyDisplayRepository();

  private OpenSurveyDisplayUseCase useCase;
  private ApplicationId applicationId;
  private SurveyCandidate published;

  @BeforeEach
  void setUp() {
    useCase = new OpenSurveyDisplayUseCase(applications, catalog, respondents, displays);
    applicationId = applications.anActiveApplication();
    published = publishedVersionFor(applicationId);
  }

  @Test
  @DisplayName("Abre a exibição criando o respondente, com o grupo de comparabilidade da versão")
  void abre_a_exibicao() {
    var displayId = UUID.randomUUID().toString();

    var output = useCase.execute(input(displayId));

    assertThat(output.created()).isTrue();
    assertThat(output.display().displayId()).isEqualTo(displayId);
    assertThat(output.display().outcome()).isEqualTo(DisplayOutcome.STARTED);
    assertThat(respondents.findAll()).hasSize(1);

    var stored = displays.findAll().getFirst();
    assertThat(stored.getComparabilityGroup()).isEqualTo(COMPARABILITY_GROUP);
    assertThat(stored.getAttributes().valueOf("plano")).contains("premium");
    assertThat(stored.sdkVersion()).contains("1.4.2");
  }

  @Test
  @DisplayName("A segunda abertura com o mesmo identificador devolve a existente, sem criar outra")
  void reabertura_devolve_a_existente() {
    var displayId = UUID.randomUUID().toString();

    var primeira = useCase.execute(input(displayId));
    var segunda = useCase.execute(input(displayId));

    assertThat(primeira.created()).isTrue();
    assertThat(segunda.created()).isFalse();
    assertThat(segunda.display().displayId()).isEqualTo(primeira.display().displayId());
    assertThat(displays.findAll()).hasSize(1);
    assertThat(respondents.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("O mesmo identificador para outra pesquisa é conflito")
  void identificador_reusado_para_outra_pesquisa() {
    var displayId = UUID.randomUUID().toString();
    useCase.execute(input(displayId));

    var outra = publishedVersionFor(applicationId);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new OpenSurveyDisplayUseCase.Input(
                        applicationId.value(),
                        displayId,
                        outra.surveyId().value(),
                        outra.versionId().value(),
                        Optional.of(REFERENCE),
                        Optional.empty(),
                        Map.of(),
                        Optional.empty())))
        .isInstanceOf(DisplayIdentifierConflict.class);
  }

  @Test
  @DisplayName("O mesmo identificador para outra versão da mesma pesquisa é conflito")
  void identificador_reusado_para_outra_versao() {
    var displayId = UUID.randomUUID().toString();
    useCase.execute(input(displayId));

    var outraVersao =
        new SurveyCandidate(
            published.surveyId(),
            SurveyVersionId.generate(),
            2,
            COMPARABILITY_GROUP + 1,
            SamplingRate.of(1.0),
            List.of(),
            Instant.parse("2026-02-01T00:00:00Z"), 0, false);
    catalog.withCandidate(
        applicationId, EventName.of("checkout.completed"), Instant.EPOCH, Optional.empty(), outraVersao);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new OpenSurveyDisplayUseCase.Input(
                        applicationId.value(),
                        displayId,
                        published.surveyId().value(),
                        outraVersao.versionId().value(),
                        Optional.of(REFERENCE),
                        Optional.empty(),
                        Map.of(),
                        Optional.empty())))
        .isInstanceOf(DisplayIdentifierConflict.class);
  }

  @Test
  @DisplayName("Versão desconhecida, em rascunho ou de outra aplicação: o mesmo 404")
  void versao_nao_entregavel() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    inputForVersion(
                        UUID.randomUUID().toString(),
                        published.surveyId().value(),
                        SurveyVersionId.generate().value())))
        .isInstanceOf(SurveyVersionNotDeliverable.class);

    var deOutraAplicacao = publishedVersionFor(applications.anActiveApplication());

    assertThatThrownBy(
            () ->
                useCase.execute(
                    inputForVersion(
                        UUID.randomUUID().toString(),
                        deOutraAplicacao.surveyId().value(),
                        deOutraAplicacao.versionId().value())))
        .isInstanceOf(SurveyVersionNotDeliverable.class);
  }

  @Test
  @DisplayName("Versão que não pertence à pesquisa informada é recusada do mesmo jeito")
  void versao_de_outra_pesquisa() {
    assertThatThrownBy(
            () ->
                useCase.execute(
                    inputForVersion(
                        UUID.randomUUID().toString(),
                        SurveyId.generate().value(),
                        published.versionId().value())))
        .isInstanceOf(SurveyVersionNotDeliverable.class);
  }

  @Test
  @DisplayName("Aplicação inativa recusa a abertura, e nada é gravado")
  void aplicacao_inativa_recusa() {
    var inactive = applications.anInactiveApplication();
    var version = publishedVersionFor(inactive);

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new OpenSurveyDisplayUseCase.Input(
                        inactive.value(),
                        UUID.randomUUID().toString(),
                        version.surveyId().value(),
                        version.versionId().value(),
                        Optional.of(REFERENCE),
                        Optional.empty(),
                        Map.of(),
                        Optional.empty())))
        .isInstanceOf(ApplicationIsInactive.class);

    assertThat(displays.isEmpty()).isTrue();
    assertThat(respondents.isEmpty()).isTrue();
  }

  @Test
  @DisplayName("O respondente já conhecido tem a última visita atualizada, sem virar outro")
  void respondente_conhecido_e_revisitado() {
    useCase.execute(input(UUID.randomUUID().toString()));
    var primeiroContato = respondents.findAll().getFirst();

    useCase.execute(input(UUID.randomUUID().toString()));

    var depois = respondents.findAll();
    assertThat(depois).hasSize(1);
    assertThat(depois.getFirst().id()).isEqualTo(primeiroContato.id());
    assertThat(depois.getFirst().getFirstSeenAt()).isEqualTo(primeiroContato.getFirstSeenAt());
    assertThat(depois.getFirst().getLastSeenAt())
        .isAfterOrEqualTo(primeiroContato.getLastSeenAt());
  }

  @Test
  @DisplayName("Duas aberturas só com dispositivo são o mesmo respondente")
  void duas_aberturas_por_dispositivo_sao_o_mesmo_respondente() {
    useCase.execute(inputByDevice(UUID.randomUUID().toString()));
    useCase.execute(inputByDevice(UUID.randomUUID().toString()));

    assertThat(respondents.findAll()).hasSize(1);
  }

  @Test
  @DisplayName("Quem passa a informar a referência do app vira outro respondente, sem fusão")
  void referencia_do_app_cria_respondente_novo() {
    useCase.execute(inputByDevice(UUID.randomUUID().toString()));
    useCase.execute(input(UUID.randomUUID().toString()));

    assertThat(respondents.findAll())
        .describedAs("os históricos não são fundidos (D-09)")
        .hasSize(2);
  }

  @Test
  @DisplayName("A mesma referência em duas aplicações são dois respondentes")
  void mesma_referencia_em_duas_aplicacoes() {
    useCase.execute(input(UUID.randomUUID().toString()));

    var outraAplicacao = applications.anActiveApplication();
    var version = publishedVersionFor(outraAplicacao);
    useCase.execute(
        new OpenSurveyDisplayUseCase.Input(
            outraAplicacao.value(),
            UUID.randomUUID().toString(),
            version.surveyId().value(),
            version.versionId().value(),
            Optional.of(REFERENCE),
            Optional.empty(),
            Map.of(),
            Optional.empty()));

    assertThat(respondents.findAll()).hasSize(2);
  }

  private OpenSurveyDisplayUseCase.Input input(String displayId) {
    return new OpenSurveyDisplayUseCase.Input(
        applicationId.value(),
        displayId,
        published.surveyId().value(),
        published.versionId().value(),
        Optional.of(REFERENCE),
        Optional.empty(),
        Map.of("plano", "premium"),
        Optional.of("1.4.2"));
  }

  private OpenSurveyDisplayUseCase.Input inputByDevice(String displayId) {
    return new OpenSurveyDisplayUseCase.Input(
        applicationId.value(),
        displayId,
        published.surveyId().value(),
        published.versionId().value(),
        Optional.empty(),
        Optional.of(DEVICE),
        Map.of(),
        Optional.empty());
  }

  private OpenSurveyDisplayUseCase.Input inputForVersion(
      String displayId, String surveyId, String versionId) {
    return new OpenSurveyDisplayUseCase.Input(
        applicationId.value(),
        displayId,
        surveyId,
        versionId,
        Optional.of(REFERENCE),
        Optional.empty(),
        Map.of(),
        Optional.empty());
  }

  private SurveyCandidate publishedVersionFor(ApplicationId owner) {
    var candidate =
        new SurveyCandidate(
            SurveyId.generate(),
            SurveyVersionId.generate(),
            1,
            COMPARABILITY_GROUP,
            SamplingRate.of(1.0),
            List.of(),
            Instant.parse("2026-01-01T00:00:00Z"), 0, false);

    catalog.withCandidate(
        owner, EventName.of("checkout.completed"), Instant.EPOCH, Optional.empty(), candidate);

    return candidate;
  }
}
