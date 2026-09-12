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
import com.renanloureiroo.pitaco.testsupport.gateways.InMemorySdkUsageRecorder;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryAnswerRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryObservedAttributeRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryObservedEventRepository;
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
import com.renanloureiroo.pitaco.testsupport.transaction.DirectTransactor;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedEvent;
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
  private final InMemoryObservedEventRepository events = new InMemoryObservedEventRepository();
  private final InMemoryObservedAttributeRepository attributes =
      new InMemoryObservedAttributeRepository();
  private final DirectTransactor transactor = new DirectTransactor();
  private InMemorySdkUsageRecorder sdkUsage = new InMemorySdkUsageRecorder();

  private FindEligibleSurveyUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    useCase = useCaseOver(events);
    applicationId = applications.anActiveApplication();
  }

  private FindEligibleSurveyUseCase useCaseOver(InMemoryObservedEventRepository catalogOfEvents) {
    return useCaseOver(catalogOfEvents, attributes);
  }

  private FindEligibleSurveyUseCase useCaseOver(
      InMemoryObservedEventRepository catalogOfEvents,
      InMemoryObservedAttributeRepository catalogOfAttributes) {
    return new FindEligibleSurveyUseCase(
        applications,
        catalog,
        respondents,
        displays,
        catalogOfEvents,
        catalogOfAttributes,
        sdkUsage,
        transactor,
        DISPLAY_TIMEOUT,
        MAX_ATTEMPTS);
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

  @Nested
  @DisplayName("Catálogo de eventos observados")
  class CatalogoDeEventos {

    @Test
    @DisplayName("Toda consulta registra o evento, mesmo sem pesquisa para ele")
    void registra_o_evento_sem_pesquisa() {
      useCase.execute(
          new FindEligibleSurveyUseCase.Input(
              applicationId.value(),
              Optional.of(REFERENCE),
              Optional.empty(),
              "tela.aberta",
              Map.of()));

      assertThat(events.findAll())
          .singleElement()
          .satisfies(
              event -> {
                assertThat(event.getApplicationId()).isEqualTo(applicationId);
                assertThat(event.getName()).isEqualTo(EventName.of("tela.aberta"));
              });
      assertThat(transactor.executions()).isEqualTo(1);
    }

    @Test
    @DisplayName("O mesmo evento repetido continua sendo uma linha só")
    void repeticao_nao_duplica() {
      publishedSurvey(SamplingRate.of(1.0), List.of());

      useCase.execute(input(Map.of()));
      useCase.execute(input(Map.of()));
      useCase.execute(input(Map.of()));

      assertThat(events.findAll()).hasSize(1);
      assertThat(events.findAll().getFirst().getLastSeenAt())
          .isAfterOrEqualTo(events.findAll().getFirst().getFirstSeenAt());
    }

    @Test
    @DisplayName("Aplicação inativa ou desconhecida não alimenta o catálogo")
    void aplicacao_inativa_nao_registra() {
      var inactive = applications.anInactiveApplication();

      useCase.execute(
          new FindEligibleSurveyUseCase.Input(
              inactive.value(), Optional.of(REFERENCE), Optional.empty(), EVENT, Map.of()));
      useCase.execute(
          new FindEligibleSurveyUseCase.Input(
              ApplicationId.generate().value(),
              Optional.of(REFERENCE),
              Optional.empty(),
              EVENT,
              Map.of()));

      assertThat(events.isEmpty()).isTrue();
      assertThat(transactor.executions()).isZero();
    }

    @Test
    @DisplayName("Catálogo fora do ar não impede a entrega")
    void falha_no_catalogo_nao_impede_a_entrega() {
      var failing = new InMemoryObservedEventRepository().failing();
      var candidate = publishedSurvey(SamplingRate.of(1.0), List.of());

      var output = useCaseOver(failing).execute(input(Map.of()));

      assertThat(output.survey()).isPresent();
      assertThat(output.survey().orElseThrow().surveyId())
          .isEqualTo(candidate.surveyId().value());
    }

    @Test
    @DisplayName("O registro acontece antes de saber se há pesquisa")
    void registra_antes_de_consultar_candidatos() {
      useCase.execute(
          new FindEligibleSurveyUseCase.Input(
              applicationId.value(),
              Optional.of(REFERENCE),
              Optional.empty(),
              "evento.sem.pesquisa",
              Map.of()));

      assertThat(events.findAll()).extracting(ObservedEvent::getName)
          .containsExactly(EventName.of("evento.sem.pesquisa"));
    }
  }

  @Test
  @DisplayName("Nenhuma escrita de coleta em nenhum caminho (D-10, SC-003)")
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
        SurveyId.generate(), SurveyVersionId.generate(), 1, 1, rate, criteria, publishedAt, 0, false);
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
              PUBLISHED_AT.plusSeconds(60), 0, false);
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
              PUBLISHED_AT, 0, false);
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

  @Nested
  @DisplayName("Controle de exposição — descanso, prioridade e atributos")
  class ControleDeExposicao {

    private static final int QUIET_DAYS = 7;

    private SurveyCandidate publishWith(int priority, boolean ignoresQuietPeriod, Instant at) {
      var candidate =
          new SurveyCandidate(
              SurveyId.generate(),
              SurveyVersionId.generate(),
              1,
              1,
              SamplingRate.of(1.0),
              List.of(),
              at,
              priority,
              ignoresQuietPeriod);
      catalog.withCandidate(
          applicationId, EventName.of(EVENT), WINDOW_START, Optional.empty(), candidate);
      catalog.withContent(content(candidate));
      return candidate;
    }

    // Uma exibição de outra pesquisa qualquer: o descanso vale entre pesquisas diferentes.
    private void sawAnotherSurveyAt(Instant openedAt) {
      var respondent =
          respondents
              .findByIdentity(
                  applicationId,
                  new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, REFERENCE))
              .orElseGet(
                  () ->
                      RespondentFactory.aRespondent()
                          .forApplication(applicationId)
                          .identifiedByReference(REFERENCE)
                          .buildSavedIn(respondents));

      SurveyDisplayFactory.aDisplay()
          .forApplication(applicationId)
          .forRespondent(respondent.id())
          .forSurvey(SurveyId.generate())
          .openedAt(openedAt)
          .dismissedAt(openedAt.plusSeconds(30))
          .buildSavedIn(displays);
    }

    @Test
    @DisplayName("Quem viu outra pesquisa dentro do intervalo não recebe esta")
    void descanso_bloqueia_entre_pesquisas() {
      applications.withQuietPeriodDays(applicationId, QUIET_DAYS);
      publishWith(0, false, PUBLISHED_AT);
      sawAnotherSurveyAt(Instant.now().minus(Duration.ofDays(1)));

      assertThat(useCase.execute(input(Map.of())).survey()).isEmpty();
    }

    @Test
    @DisplayName("Passado o intervalo, recebe normalmente")
    void passado_o_intervalo_recebe() {
      applications.withQuietPeriodDays(applicationId, QUIET_DAYS);
      publishWith(0, false, PUBLISHED_AT);
      sawAnotherSurveyAt(Instant.now().minus(Duration.ofDays(QUIET_DAYS + 1)));

      assertThat(useCase.execute(input(Map.of())).survey()).isPresent();
    }

    @Test
    @DisplayName("Pesquisa isenta ignora o descanso; a outra continua barrada")
    void isenta_ignora_o_descanso() {
      applications.withQuietPeriodDays(applicationId, QUIET_DAYS);
      publishWith(50, false, PUBLISHED_AT);
      var isenta = publishWith(0, true, PUBLISHED_AT.plusSeconds(60));
      sawAnotherSurveyAt(Instant.now().minus(Duration.ofHours(1)));

      assertThat(useCase.execute(input(Map.of())).survey().orElseThrow().surveyId())
          .isEqualTo(isenta.surveyId().value());
    }

    @Test
    @DisplayName("Sem intervalo configurado na aplicação, a exibição recente não barra nada")
    void sem_intervalo_nada_muda() {
      publishWith(0, false, PUBLISHED_AT);
      sawAnotherSurveyAt(Instant.now().minus(Duration.ofMinutes(5)));

      assertThat(useCase.execute(input(Map.of())).survey()).isPresent();
    }

    @Test
    @DisplayName("Prioridade maior vence, mesmo publicada depois")
    void prioridade_vence_a_antiguidade() {
      publishWith(0, false, PUBLISHED_AT);
      var prioritaria = publishWith(10, false, PUBLISHED_AT.plusSeconds(3600));

      var primeira = useCase.execute(input(Map.of())).survey().orElseThrow();
      var segunda = useCase.execute(input(Map.of())).survey().orElseThrow();

      assertThat(primeira.surveyId()).isEqualTo(prioritaria.surveyId().value());
      assertThat(segunda.surveyId()).isEqualTo(primeira.surveyId());
    }

    @Test
    @DisplayName("Os atributos enviados alimentam o catálogo, cada catálogo na sua transação")
    void atributos_alimentam_o_catalogo() {
      useCase.execute(input(Map.of("plano", "pro", "versao", "2.1")));

      assertThat(attributes.findAll())
          .extracting(ObservedAttribute::getName)
          .containsExactlyInAnyOrder("plano", "versao");
      assertThat(transactor.executions()).isEqualTo(2);
    }

    @Test
    @DisplayName("Catálogo de atributos fora do ar não impede a entrega")
    void falha_no_catalogo_de_atributos_nao_impede() {
      var candidate = publishWith(0, false, PUBLISHED_AT);

      var output =
          useCaseOver(events, new InMemoryObservedAttributeRepository().failing())
              .execute(input(Map.of("plano", "pro")));

      assertThat(output.survey().orElseThrow().surveyId())
          .isEqualTo(candidate.surveyId().value());
      assertThat(events.findAll()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("Versão do SDK")
  class VersaoDoSdk {

    private FindEligibleSurveyUseCase.Input withVersion(ApplicationId owner, String version) {
      return new FindEligibleSurveyUseCase.Input(
          owner.value(), Optional.of(REFERENCE), Optional.empty(), EVENT, Map.of(),
          Optional.of(version));
    }

    @Test
    @DisplayName("Registra a versão informada, com ou sem pesquisa na resposta")
    void registra_a_versao() {
      useCase.execute(withVersion(applicationId, "1.4.2"));

      assertThat(sdkUsage.recorded())
          .singleElement()
          .satisfies(
              recorded -> {
                assertThat(recorded.applicationId()).isEqualTo(applicationId);
                assertThat(recorded.version().value()).isEqualTo("1.4.2");
              });
    }

    @Test
    @DisplayName("Versão fora do formato é ignorada, e a consulta segue")
    void ignora_versao_invalida() {
      publishedSurvey(SamplingRate.of(1.0), List.of());

      var output = useCase.execute(withVersion(applicationId, "v1"));

      assertThat(output.survey()).isPresent();
      assertThat(sdkUsage.recorded()).isEmpty();
    }

    @Test
    @DisplayName("Sem versão informada, nada é registrado")
    void sem_versao() {
      useCase.execute(input(Map.of()));

      assertThat(sdkUsage.recorded()).isEmpty();
    }

    @Test
    @DisplayName("Falha ao registrar a versão não impede a entrega")
    void falha_nao_impede_entrega() {
      sdkUsage = new InMemorySdkUsageRecorder().failing();
      useCase = useCaseOver(events);
      publishedSurvey(SamplingRate.of(1.0), List.of());

      var output = useCase.execute(withVersion(applicationId, "1.0.0"));

      assertThat(output.survey()).isPresent();
    }

    @Test
    @DisplayName("Aplicação inativa não registra versão")
    void inativa_nao_registra() {
      var inactive = applications.anInactiveApplication();

      useCase.execute(withVersion(inactive, "1.0.0"));

      assertThat(sdkUsage.recorded()).isEmpty();
    }
  }
}
