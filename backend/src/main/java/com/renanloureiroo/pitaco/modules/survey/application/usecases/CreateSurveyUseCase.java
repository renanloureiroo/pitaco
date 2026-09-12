package com.renanloureiroo.pitaco.modules.survey.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.survey.application.gateways.ApplicationScopeGateway;
import com.renanloureiroo.pitaco.modules.survey.application.outputs.SurveyOutput;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyRepository;
import com.renanloureiroo.pitaco.modules.survey.application.repositories.SurveyVersionRepository;
import com.renanloureiroo.pitaco.modules.survey.application.services.SurveyScope;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.Survey;
import com.renanloureiroo.pitaco.modules.survey.domain.entities.SurveyVersion;
import com.renanloureiroo.pitaco.modules.survey.domain.templates.TemplateQuestions;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyTemplate;
import java.time.Instant;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateSurveyUseCase implements UseCase<CreateSurveyUseCase.Input, SurveyOutput> {

  private final ApplicationScopeGateway applications;
  private final SurveyRepository surveysRepository;
  private final SurveyVersionRepository surveyVersionsRepository;

  public CreateSurveyUseCase(
      ApplicationScopeGateway applications,
      SurveyRepository surveysRepository,
      SurveyVersionRepository surveyVersionsRepository) {
    this.applications = applications;
    this.surveysRepository = surveysRepository;
    this.surveyVersionsRepository = surveyVersionsRepository;
  }

  // O modelo é ponto de partida: o rascunho nasce com a pergunta do formato e segue editável
  // como qualquer outro. O disparo fica vazio, porque o momento certo é do autor.
  @Override
  @Transactional
  public SurveyOutput execute(Input input) {
    var applicationId = SurveyScope.activeApplicationIdOf(applications, input.applicationId());

    var survey = Survey.create(applicationId, SurveyName.of(input.name()), input.template());
    var version = SurveyVersion.create(survey.id(), Survey.FIRST_VERSION_NUMBER);
    input
        .template()
        .ifPresent(template -> TemplateQuestions.draftsFor(template).forEach(version::addQuestion));

    surveysRepository.create(survey);
    surveyVersionsRepository.create(version);

    log.info(
        "Pesquisa criada [{}] application={} version={} template={}",
        survey.id().value(),
        applicationId.value(),
        version.getNumber(),
        input.template().map(Enum::name).orElse("-"));

    return SurveyOutput.of(survey, survey.stateAt(Instant.now(), Optional.empty()));
  }

  public record Input(String applicationId, String name, Optional<SurveyTemplate> template) {

    public Input {
      template = template == null ? Optional.empty() : template;
    }

    public Input(String applicationId, String name) {
      this(applicationId, name, Optional.empty());
    }
  }
}
