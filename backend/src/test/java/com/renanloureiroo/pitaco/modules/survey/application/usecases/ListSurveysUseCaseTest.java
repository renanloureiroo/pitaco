package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.TriggerFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListSurveysUseCase")
class ListSurveysUseCaseTest {

  private InMemoryApplicationScopeGateway applications;
  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private ListSurveysUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationScopeGateway();
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    useCase = new ListSurveysUseCase(applications, surveys, versions);
    applicationId = applications.anActiveApplication();
  }

  private ListSurveysUseCase.Input input(int page, int size) {
    return new ListSurveysUseCase.Input(applicationId.value(), page, size);
  }

  private ListSurveysUseCase.Input input() {
    return input(0, 20);
  }

  @Test
  @DisplayName("Devolve da mais recente para a mais antiga, com desempate determinístico")
  void ordena_da_mais_recente_para_a_mais_antiga() {
    var created =
        SurveyFactory.aSurvey().forApplication(applicationId).buildBatchSavedIn(surveys, 3);

    var output = useCase.execute(input());

    assertThat(output.items())
        .extracting(SurveyOutput::id)
        .containsExactly(
            created.get(2).id().value(), created.get(1).id().value(), created.get(0).id().value());
    assertThat(output.total()).isEqualTo(3);
    assertThat(output.totalPages()).isEqualTo(1);
    assertThat(output.page()).isZero();
    assertThat(output.size()).isEqualTo(20);
  }

  @Test
  @DisplayName("Não devolve pesquisa de outra aplicação")
  void isola_as_aplicacoes() {
    var minha = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyFactory.aSurvey().forApplication(ApplicationId.generate()).buildSavedIn(surveys);

    var output = useCase.execute(input());

    assertThat(output.items()).extracting(SurveyOutput::id).containsExactly(minha.id().value());
    assertThat(output.total()).isEqualTo(1);
  }

  @Test
  @DisplayName("Aplicação sem nenhuma pesquisa devolve lista vazia, não erro")
  void aplicacao_sem_pesquisa_devolve_lista_vazia() {
    var output = useCase.execute(input());

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isZero();
    assertThat(output.totalPages()).isZero();
  }

  @Test
  @DisplayName("Percorre as páginas sem repetir nem omitir, e conta o conjunto inteiro")
  void percorre_as_paginas() {
    SurveyFactory.aSurvey().forApplication(applicationId).buildBatchSavedIn(surveys, 5);

    var first = useCase.execute(input(0, 2));
    var second = useCase.execute(input(1, 2));
    var third = useCase.execute(input(2, 2));

    assertThat(first.items()).hasSize(2);
    assertThat(second.items()).hasSize(2);
    assertThat(third.items()).hasSize(1);
    assertThat(first.total()).isEqualTo(5);
    assertThat(first.totalPages()).isEqualTo(3);
    assertThat(
            Stream.of(first, second, third)
                .flatMap(output -> output.items().stream())
                .map(SurveyOutput::id))
        .doesNotHaveDuplicates()
        .hasSize(5);
  }

  @Test
  @DisplayName("Página além do fim devolve lista vazia com o total correto")
  void pagina_alem_do_fim() {
    SurveyFactory.aSurvey().forApplication(applicationId).buildBatchSavedIn(surveys, 3);

    var output = useCase.execute(input(9, 20));

    assertThat(output.items()).isEmpty();
    assertThat(output.total()).isEqualTo(3);
    assertThat(output.totalPages()).isEqualTo(1);
  }

  @Test
  @DisplayName("Cada item traz o estado derivado da janela da sua versão publicada")
  void deriva_o_estado_de_cada_item() {
    var rascunho =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .createdAt(SurveyFactory.BATCH_FIRST_CREATED_AT)
            .buildSavedIn(surveys);
    var ativa =
        SurveyFactory.aPublishedSurvey()
            .forApplication(applicationId)
            .createdAt(SurveyFactory.BATCH_FIRST_CREATED_AT.plusSeconds(1))
            .buildSavedIn(surveys);
    var agendada =
        SurveyFactory.aPublishedSurvey()
            .forApplication(applicationId)
            .createdAt(SurveyFactory.BATCH_FIRST_CREATED_AT.plusSeconds(2))
            .buildSavedIn(surveys);

    SurveyVersionFactory.aVersion()
        .forSurvey(ativa.id())
        .triggeredBy(TriggerFactory.anOpenTrigger())
        .buildPublishedSavedIn(versions);
    SurveyVersionFactory.aVersion()
        .forSurvey(agendada.id())
        .triggeredBy(TriggerFactory.aScheduledTrigger())
        .buildPublishedSavedIn(versions);

    var output = useCase.execute(input());

    assertThat(output.items())
        .filteredOn(item -> item.id().equals(rascunho.id().value()))
        .extracting(SurveyOutput::state)
        .containsExactly(SurveyState.DRAFT);
    assertThat(output.items())
        .filteredOn(item -> item.id().equals(ativa.id().value()))
        .extracting(SurveyOutput::state)
        .containsExactly(SurveyState.ACTIVE);
    assertThat(output.items())
        .filteredOn(item -> item.id().equals(agendada.id().value()))
        .extracting(SurveyOutput::state)
        .containsExactly(SurveyState.SCHEDULED);
  }

  @Test
  @DisplayName("Aplicação inexistente e identificador malformado recusam do mesmo jeito")
  void recusa_aplicacao_que_nao_existe() {
    var desconhecida = applications.anUnknownApplication().value();

    assertThatThrownBy(() -> useCase.execute(new ListSurveysUseCase.Input(desconhecida, 0, 20)))
        .isInstanceOf(ApplicationNotFound.class);
    assertThatThrownBy(() -> useCase.execute(new ListSurveysUseCase.Input("nao-e-um-id", 0, 20)))
        .isInstanceOf(ApplicationNotFound.class);
  }

  @Test
  @DisplayName("Aplicação inativa lista normalmente: inatividade impede criar, não enxergar")
  void aplicacao_inativa_lista() {
    var inativa = applications.anInactiveApplication();
    var survey = SurveyFactory.aSurvey().forApplication(inativa).buildSavedIn(surveys);

    var output = useCase.execute(new ListSurveysUseCase.Input(inativa.value(), 0, 20));

    assertThat(output.items()).extracting(SurveyOutput::id).containsExactly(survey.id().value());
  }

  @Test
  void tamanho_zero_nao_quebra_o_calculo_de_paginas() {
    SurveyFactory.aSurvey().forApplication(applicationId).buildBatchSavedIn(surveys, 3);

    assertThat(useCase.execute(input(0, 0)).totalPages()).isZero();
  }
}
