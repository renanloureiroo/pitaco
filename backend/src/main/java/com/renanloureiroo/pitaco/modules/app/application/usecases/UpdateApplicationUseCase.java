package com.renanloureiroo.pitaco.modules.app.application.usecases;

import com.renanloureiroo.pitaco.core.usecase.Patch;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.app.application.outputs.ApplicationOutput;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class UpdateApplicationUseCase
    implements UseCase<UpdateApplicationUseCase.Input, ApplicationOutput> {

  private final ApplicationRepository applications;

  public UpdateApplicationUseCase(ApplicationRepository applications) {
    this.applications = applications;
  }

  // Três estados por campo: ausente não mexe, nulo remove, valor define. O slug fica de fora
  // por ser imutável.
  public record Input(
      String applicationId,
      Optional<String> name,
      Patch<Integer> quietPeriodDays,
      Patch<Integer> retentionDays,
      Patch<Integer> openTextRetentionDays) {}

  @Override
  public ApplicationOutput execute(Input input) {
    var application =
        applications
            .findById(GetApplicationUseCase.applicationIdOf(input.applicationId()))
            .orElseThrow(() -> new ApplicationNotFound(input.applicationId()));

    input.name().map(Name::of).ifPresent(application::rename);
    input.quietPeriodDays().apply(application::defineQuietPeriod, application::removeQuietPeriod);
    // A retenção geral entra antes da de texto livre: a segunda é validada contra a primeira.
    input.retentionDays().apply(application::defineRetention, application::removeRetention);
    input
        .openTextRetentionDays()
        .apply(application::defineOpenTextRetention, application::resetOpenTextRetention);

    var saved = applications.update(application);

    log.info("Aplicação atualizada [{}]", saved.id().value());

    return ApplicationOutput.of(saved);
  }
}
