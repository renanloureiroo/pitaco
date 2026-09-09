package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.EventName;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.RuleOperation;
import com.renanloureiroo.pitaco.core.catalog.SamplingRate;
import com.renanloureiroo.pitaco.core.catalog.ScaleRange;
import com.renanloureiroo.pitaco.core.catalog.SegmentationCriterion;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.SurveyCandidate;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryPublishedSurveyCatalog;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryAnswerRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryRespondentRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyDisplayRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.DisplayOutcome;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import com.renanloureiroo.pitaco.testsupport.factories.RespondentFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyDisplayFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FindEligibleSurveyUseCase")
class FindEligibleSurveyUseCaseTest {

  private static final String EVENT = "checkout.completed";
  private static final String REFERENCE = "u-8f1c";
  private static final Instant WINDOW_START = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant PUBLISHED_AT = Instant.parse("2026-01-01T00:00:00Z");
  private static final Duration DISPLAY_TIMEOUT = Duration.ofMinutes(30);
  private static final int MAX_ATTEMPTS = 3;

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemoryPublishedSurveyCatalog catalog = new InMemoryPublishedSurveyCatalog();
  private final InMemoryRespondentRepository respondents = new InMemoryRespondentRepository();
  private final InMemorySurveyDisplayRepository displays = new InMemorySurveyDisplayRepository();
  private final InMemoryAnswerRepository answers = new InMemoryAnswerRepository();

  private FindEligibleSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    useCase =
        new FindEligibleSurveyUseCase(
            applications, catalog, respondents, displays, DISPLAY_TIMEOUT, MAX_ATTEMPTS);
    applicationId = applications.anActiveApplication();
  }

  @Test
  @DisplayName("A pesquisa elegível volta inteira, com as perguntas em ordem")
  void pesquisa_elegivel_volta_inteira() {
    var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());

    var output = useCase.execute(input(Map.of()));

    var survey = output.survey().orElseThrow();
    assertThat(survey.surveyId()).isEqualTo(candidate.surveyId().value());
    assertThat(survey.versionId()).isEqualTo(candidate.versionId().value());
    assertThat(survey.questions()).hasSize(2);
    assertThat(survey.questions().getFirst().position()).isEqualTo(1);
    assertThat(survey.questions().getLast().type()).isEqualTo(QuestionType.NPS);
    assertThat(survey.questions().getLast().range()).isPresent();
  }

  @Test
  @DisplayName("Aplicação inativa devolve vazio, sem erro e sem consultar o catálogo")
  void aplicacao_inativa_devolve_vazio() {
    var inactive = applications.anInactiveApplication();
    publishedSurvey(SamplingRate.of(1.0), List.of());

    var output =
        useCase.execute(
            new FindEligibleSurveyUseCase.Input(
                inactive.value(), Optional.of(REFERENCE), Optional.empty(), EVENT, Map.of()));

    assertThat(output.survey()).isEmpty();
    assertThat(catalog.candidateCalls()).isZero();
  }

  @Test
  @DisplayName("Aplicação desconhecida devolve vazio, no mesmo silêncio da inativa")
  void aplicacao_desconhecida_devolve_vazio() {
    var output =
        useCase.execute(
            new FindEligibleSurveyUseCase.Input(
                ApplicationId.generate().value(),
                Optional.of(REFERENCE),
                Optional.empty(),
                EVENT,
                Map.of()));

    assertThat(output.survey()).isEmpty();
  }

  @Test
  @DisplayName("Evento sem pesquisa devolve vazio")
  void evento_sem_pesquisa_devolve_vazio() {
    publishedSurvey(SamplingRate.of(1.0), List.of());

    var output =
        useCase.execute(
            new FindEligibleSurveyUseCase.Input(
                applicationId.value(),
                Optional.of(REFERENCE),
                Optional.empty(),
                "outro.evento",
                Map.of()));

    assertThat(output.survey()).isEmpty();
  }

  @Test
  @DisplayName("Pesquisa fora do ar nunca é candidata")
  void pesquisa_fora_do_ar_devolve_vazio() {
    var candidate = candidate(SamplingRate.of(1.0), List.of(), PUBLISHED_AT);
    catalog.withOffAirCandidate(
        applicationId, EventName.of(EVENT), WINDOW_START, Optional.empty(), candidate);
    catalog.withContent(content(candidate));

    assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
  }

  @Test
  @DisplayName("Regra violada devolve vazio")
  void segmentacao_violada_devolve_vazio() {
    publishedSurvey(
        SamplingRate.of(1.0),
        List.of(
            new SegmentationCriterion(
                "plano", RuleOperation.EQUALS, Optional.of("premium"))));

    assertThat(useCase.execute(input(Map.of("plano", "free"))).survey()).isEmpty();
    assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
    assertThat(useCase.execute(input(Map.of("plano", "premium"))).survey()).isPresent();
  }

  @Test
  @DisplayName("Não sorteado devolve vazio")
  void nao_sorteado_devolve_vazio() {
    publishedSurvey(SamplingRate.of(0.0), List.of());

    assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
  }

  @Test
  @DisplayName("A segmentação é avaliada antes do sorteio (FR-011)")
  void segmentacao_vem_antes_do_sorteio() {
    publishedSurvey(
        SamplingRate.of(0.0),
        List.of(
            new SegmentationCriterion("plano", RuleOperation.EQUALS, Optional.of("premium"))));

    // Nenhuma das duas camadas deixa passar; o que importa é que o conteúdo nunca é buscado.
    assertThat(useCase.execute(input(Map.of("plano", "premium"))).survey()).isEmpty();
    assertThat(catalog.contentCalls()).isZero();
  }

  @Test
  @DisplayName("Duas elegíveis devolvem exatamente uma, e sempre a mesma")
  void desempate_e_estavel() {
    var maisAntiga = publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT);
    publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT.plusSeconds(3600));

    var primeira = useCase.execute(input(Map.of())).survey().orElseThrow();
    var segunda = useCase.execute(input(Map.of())).survey().orElseThrow();

    assertThat(primeira.surveyId()).isEqualTo(maisAntiga.surveyId().value());
    assertThat(segunda.surveyId()).isEqualTo(primeira.surveyId());
  }

  @Test
  @DisplayName("Empate de publicação é desfeito pelo surveyId em ordem lexicográfica")
  void empate_desfeito_pelo_identificador() {
    var uma = publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT);
    var outra = publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT);

    var menor =
        uma.surveyId().value().compareTo(outra.surveyId().value()) < 0 ? uma : outra;

    assertThat(useCase.execute(input(Map.of())).survey().orElseThrow().surveyId())
        .isEqualTo(menor.surveyId().value());
  }

  @Test
  @DisplayName("O conteúdo é buscado só para a escolhida")
  void conteudo_e_buscado_so_para_a_escolhida() {
    publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT);
    publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT.plusSeconds(3600));

    useCase.execute(input(Map.of()));

    assertThat(catalog.contentCalls()).isEqualTo(1);
  }

  @Test
  @DisplayName("Nenhuma escrita em nenhum caminho (D-10, SC-003)")
  void nao_escreve_em_caminho_nenhum() {
    publishedSurvey(SamplingRate.of(1.0), List.of());

    useCase.execute(input(Map.of()));
    useCase.execute(
        new FindEligibleSurveyUseCase.Input(
            applicationId.value(),
            Optional.of(REFERENCE),
            Optional.empty(),
            "evento.sem.pesquisa",
            Map.of()));

    assertThat(respondents.isEmpty()).isTrue();
    assertThat(displays.isEmpty()).isTrue();
    assertThat(answers.isEmpty()).isTrue();
  }

  private FindEligibleSurveyUseCase.Input input(Map<String, String> attributes) {
    return new FindEligibleSurveyUseCase.Input(
        applicationId.value(), Optional.of(REFERENCE), Optional.empty(), EVENT, attributes);
  }

  private SurveyCandidate publishedSurvey(
      SamplingRate rate, List<SegmentationCriterion> criteria) {
    return publishedSurvey(rate, criteria, PUBLISHED_AT);
  }

  private SurveyCandidate publishedSurvey(
      SamplingRate rate, List<SegmentationCriterion> criteria, Instant publishedAt) {
    var candidate = candidate(rate, criteria, publishedAt);

    catalog.withCandidate(
        applicationId, EventName.of(EVENT), WINDOW_START, Optional.empty(), candidate);
    catalog.withContent(content(candidate));

    return candidate;
  }

  private static SurveyCandidate candidate(
      SamplingRate rate, List<SegmentationCriterion> criteria, Instant publishedAt) {
    return new SurveyCandidate(
        SurveyId.generate(), SurveyVersionId.generate(), 1, 1, rate, criteria, publishedAt);
  }

  @Nested
  @DisplayName("Camada 5 — histórico do respondente")
  class Historico {

    @Test
    @DisplayName("Quem já concluiu não recebe de novo")
    void concluida_nao_volta() {
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());
      resolvedDisplay(candidate, DisplayOutcome.COMPLETED, Instant.now());

      assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
    }

    @Test
    @DisplayName("Quem dispensou não recebe de novo")
    void dispensada_nao_volta() {
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());
      resolvedDisplay(candidate, DisplayOutcome.DISMISSED, Instant.now());

      assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
    }

    @Test
    @DisplayName("Exibição iniciada dentro do prazo não bloqueia nem resolve")
    void iniciada_dentro_do_prazo_nao_bloqueia() {
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());
      resolvedDisplay(candidate, DisplayOutcome.STARTED, Instant.now());

      assertThat(useCase.execute(input(Map.of())).survey()).isPresent();
    }

    @Test
    @DisplayName("Abandono conta tentativa, e o limite para a entrega")
    void abandono_conta_tentativa() {
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());
      var vencida = Instant.now().minus(DISPLAY_TIMEOUT).minusSeconds(60);

      for (var attempt = 0; attempt < MAX_ATTEMPTS - 1; attempt++) {
        resolvedDisplay(candidate, DisplayOutcome.STARTED, vencida.minusSeconds(attempt));
      }
      assertThat(useCase.execute(input(Map.of())).survey())
          .describedAs("abaixo do limite, ainda é entregue")
          .isPresent();

      resolvedDisplay(candidate, DisplayOutcome.STARTED, vencida.minusSeconds(MAX_ATTEMPTS));
      assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
    }

    @Test
    @DisplayName("Grupo de comparabilidade novo reabre a entrega")
    void grupo_novo_reabre() {
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());
      resolvedDisplay(candidate, DisplayOutcome.COMPLETED, Instant.now());

      var republicada =
          new SurveyCandidate(
              candidate.surveyId(),
              SurveyVersionId.generate(),
              2,
              candidate.comparabilityGroup() + 1,
              SamplingRate.of(1.0),
              List.of(),
              PUBLISHED_AT.plusSeconds(60));
      catalog.withCandidate(
          applicationId, EventName.of(EVENT), WINDOW_START, Optional.empty(), republicada);
      catalog.withContent(content(republicada));

      assertThat(useCase.execute(input(Map.of())).survey())
          .describedAs("versão semântica nova reabre; cosmética manteria o grupo e não reabriria")
          .isPresent();
    }

    @Test
    @DisplayName("O histórico é consultado uma vez só, para todos os candidatos")
    void historico_e_consultado_uma_vez() {
      var primeira = publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT);
      publishedSurvey(SamplingRate.of(1.0), List.of(), PUBLISHED_AT.plusSeconds(3600));
      resolvedDisplay(primeira, DisplayOutcome.STARTED, Instant.now());

      useCase.execute(input(Map.of()));

      assertThat(displays.historyCalls()).isEqualTo(1);
    }

    @Test
    @DisplayName("Sem candidato nenhum, o histórico nem é consultado")
    void sem_candidato_nao_consulta_historico() {
      useCase.execute(input(Map.of()));

      assertThat(displays.historyCalls()).isZero();
    }

    @Test
    @DisplayName("Respondentes distintos não interferem entre si")
    void respondentes_distintos_nao_interferem() {
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());
      resolvedDisplay(candidate, DisplayOutcome.COMPLETED, Instant.now());

      var outro =
          new FindEligibleSurveyUseCase.Input(
              applicationId.value(),
              Optional.of("u-outro"),
              Optional.empty(),
              EVENT,
              Map.of());

      assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
      assertThat(useCase.execute(outro).survey()).isPresent();
    }

    @Test
    @DisplayName("O mesmo identificador em duas aplicações mantém históricos independentes")
    void aplicacoes_distintas_tem_historicos_independentes() {
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());
      resolvedDisplay(candidate, DisplayOutcome.COMPLETED, Instant.now());

      var outraAplicacao = applications.anActiveApplication();
      var daOutra =
          new SurveyCandidate(
              SurveyId.generate(),
              SurveyVersionId.generate(),
              1,
              1,
              SamplingRate.of(1.0),
              List.of(),
              PUBLISHED_AT);
      catalog.withCandidate(
          outraAplicacao, EventName.of(EVENT), WINDOW_START, Optional.empty(), daOutra);
      catalog.withContent(content(daOutra));

      assertThat(
              useCase
                  .execute(
                      new FindEligibleSurveyUseCase.Input(
                          outraAplicacao.value(),
                          Optional.of(REFERENCE),
                          Optional.empty(),
                          EVENT,
                          Map.of()))
                  .survey())
          .isPresent();
    }

    // O respondente e a exibição são plantados direto nos fakes: a elegibilidade nunca os cria.
    private void resolvedDisplay(
        SurveyCandidate candidate, DisplayOutcome outcome, Instant openedAt) {
      var respondent =
          respondents
              .findByIdentity(applicationId, identity())
              .orElseGet(
                  () ->
                      RespondentFactory.aRespondent()
                          .forApplication(applicationId)
                          .identifiedByReference(REFERENCE)
                          .buildSavedIn(respondents));

      var display =
          SurveyDisplayFactory.aDisplay()
              .forApplication(applicationId)
              .forRespondent(respondent.id())
              .forSurvey(candidate.surveyId())
              .forVersion(candidate.versionId())
              .inComparabilityGroup(candidate.comparabilityGroup())
              .openedAt(openedAt);

      switch (outcome) {
        case COMPLETED -> display.completedAt(openedAt.plusSeconds(60));
        case DISMISSED -> display.dismissedAt(openedAt.plusSeconds(60));
        default -> {}
      }

      display.buildSavedIn(displays);
    }

    private RespondentIdentity identity() {
      return new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, REFERENCE);
    }
  }

  private static DeliverableSurvey content(SurveyCandidate candidate) {
    return new DeliverableSurvey(
        candidate.surveyId(),
        candidate.versionId(),
        candidate.versionNumber(),
        List.of(
            new DeliverableQuestion(
                QuestionKey.generate(),
                1,
                "O que achou do checkout?",
                QuestionType.FREE_TEXT,
                true,
                List.of(),
                Optional.empty()),
            new DeliverableQuestion(
                QuestionKey.generate(),
                2,
                "De 0 a 10, quanto recomendaria?",
                QuestionType.NPS,
                false,
                List.of(),
                Optional.of(new ScaleRange(0, 10)))));
  }
}
