package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationIsInactive;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.survey.application.errors.SurveyNotFound;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Question;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyLifecycle;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyState;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersionStatus;
import com.renanloureiroo.pitaco.testsupport.factories.QuestionFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyFactory;
import com.renanloureiroo.pitaco.testsupport.factories.SurveyVersionFactory;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyRepository;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemorySurveyVersionRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("DuplicateSurveyUseCase")
class DuplicateSurveyUseCaseTest {

  private InMemoryApplicationScopeGateway applications;
  private InMemorySurveyRepository surveys;
  private InMemorySurveyVersionRepository versions;
  private DuplicateSurveyUseCase useCase;
  private ApplicationId applicationId;
  private Survey published;
  private SurveyVersion content;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationScopeGateway();
    surveys = new InMemorySurveyRepository();
    versions = new InMemorySurveyVersionRepository();
    useCase = new DuplicateSurveyUseCase(applications, surveys, versions);
    applicationId = applications.anActiveApplication();

    published =
        SurveyFactory.aSurvey()
            .forApplication(applicationId)
            .withName("NPS pós-checkout")
            .published(1)
            .withOpenDraft(2)
            .withPriority(7)
            .withResponseQuota(100)
            .buildSavedIn(surveys);
    content =
        SurveyVersionFactory.aVersion()
            .forSurvey(published.id())
            .withQuestions(
                QuestionFactory.anNpsQuestion().withStatement("De 0 a 10?"),
                QuestionFactory.aFreeTextQuestion().withStatement("Por quê?"))
            .buildPublishedSavedIn(versions);
    SurveyVersionFactory.aVersion()
        .forSurvey(published.id())
        .numbered(2)
        .withQuestions(QuestionFactory.aFreeTextQuestion().withStatement("Só no rascunho"))
        .buildSavedIn(versions);
  }

  private DuplicateSurveyUseCase.Input input(Optional<String> target, Optional<String> name) {
    return new DuplicateSurveyUseCase.Input(
        applicationId.value(), published.id().value(), target, name);
  }

  private SurveyVersion versionOf(String surveyId) {
    return versions.findAll().stream()
        .filter(version -> version.getSurveyId().value().equals(surveyId))
        .findFirst()
        .orElseThrow();
  }

  @Test
  @DisplayName("Copia a versão publicada, não o rascunho aberto, numa pesquisa nova em rascunho")
  void copia_a_publicada() {
    var output = useCase.execute(input(Optional.empty(), Optional.empty()));

    assertThat(output.id()).isNotEqualTo(published.id().value());
    assertThat(output.applicationId()).isEqualTo(applicationId.value());
    assertThat(output.name()).isEqualTo("Cópia de NPS pós-checkout");
    assertThat(output.state()).isEqualTo(SurveyState.DRAFT);
    assertThat(output.publishedVersionNumber()).isEmpty();
    assertThat(output.draftVersionNumber()).contains(1);
    assertThat(output.priority()).isEqualTo(7);
    assertThat(output.responseQuota()).contains(100);

    var copy = versionOf(output.id());
    assertThat(copy.getNumber()).isEqualTo(1);
    assertThat(copy.getStatus()).isEqualTo(SurveyVersionStatus.DRAFT);
    assertThat(copy.getQuestions())
        .extracting(question -> question.getStatement().value())
        .containsExactly("De 0 a 10?", "Por quê?");
    assertThat(copy.getQuestions())
        .extracting(Question::getKey)
        .doesNotContainAnyElementsOf(content.getQuestions().stream().map(Question::getKey).toList());
  }

  @Test
  @DisplayName("A original fica como estava")
  void original_intacta() {
    useCase.execute(input(Optional.empty(), Optional.empty()));

    var original = surveys.findAll().stream().filter(survey -> survey.id().equals(published.id())).findFirst().orElseThrow();
    assertThat(original.getLifecycle()).isEqualTo(SurveyLifecycle.PUBLISHED);
    assertThat(original.publishedVersionNumber()).contains(1);
    assertThat(versions.findAll()).hasSize(3);
  }

  @Test
  void pesquisa_que_nunca_publicou_copia_o_rascunho() {
    var draft = SurveyFactory.aSurvey().forApplication(applicationId).buildSavedIn(surveys);
    SurveyVersionFactory.aVersion()
        .forSurvey(draft.id())
        .withQuestions(QuestionFactory.aFreeTextQuestion().withStatement("Rascunho"))
        .buildSavedIn(versions);

    var output =
        useCase.execute(
            new DuplicateSurveyUseCase.Input(
                applicationId.value(), draft.id().value(), Optional.empty(), Optional.empty()));

    assertThat(versionOf(output.id()).getQuestions())
        .extracting(question -> question.getStatement().value())
        .containsExactly("Rascunho");
  }

  @Test
  @DisplayName("Com destino e nome, a cópia vai para a outra aplicação com o nome dado")
  void outra_aplicacao_e_nome() {
    var target = applications.anActiveApplication();

    var output = useCase.execute(input(Optional.of(target.value()), Optional.of("NPS — Transacional")));

    assertThat(output.applicationId()).isEqualTo(target.value());
    assertThat(output.name()).isEqualTo("NPS — Transacional");
  }

  @Test
  void destino_desconhecido_e_recusado_sem_gravar() {
    var desconhecida = applications.anUnknownApplication().value();

    assertThatThrownBy(() -> useCase.execute(input(Optional.of(desconhecida), Optional.empty())))
        .isInstanceOf(ApplicationNotFound.class);

    assertThat(surveys.findAll()).hasSize(1);
    assertThat(versions.findAll()).hasSize(2);
  }

  @Test
  void destino_inativo_e_recusado_sem_gravar() {
    var inativa = applications.anInactiveApplication().value();

    assertThatThrownBy(() -> useCase.execute(input(Optional.of(inativa), Optional.empty())))
        .isInstanceOf(ApplicationIsInactive.class);

    assertThat(surveys.findAll()).hasSize(1);
  }

  @Test
  void origem_de_outra_aplicacao_e_nao_encontrada() {
    var outra = applications.anActiveApplication();

    assertThatThrownBy(
            () ->
                useCase.execute(
                    new DuplicateSurveyUseCase.Input(
                        outra.value(), published.id().value(), Optional.empty(), Optional.empty())))
        .isInstanceOf(SurveyNotFound.class);

    assertThat(surveys.findAll()).hasSize(1);
    assertThat(List.copyOf(versions.findAll())).hasSize(2);
  }
}
