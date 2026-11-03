package com.renanloureiroo.pitaco.modules.privacy.application.usecases;

import com.renanloureiroo.pitaco.core.transaction.Transactional;
import com.renanloureiroo.pitaco.core.usecase.UseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.application.outputs.RespondentErasureOutput;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.DeletionAuditRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RespondentErasureRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.services.PrivacyScope;
import com.renanloureiroo.pitaco.modules.privacy.domain.entities.DeletionAudit;
import com.renanloureiroo.pitaco.modules.privacy.domain.valueobjects.ErasureTarget;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

// Irreversível. A contagem, a exclusão e o registro acontecem juntos: não existe exclusão sem
// registro, nem registro de exclusão que não aconteceu. Os agregados de resultado não são
// recalculados porque são lidos na hora; o congelado pela retenção é anônimo e fica.
@Slf4j
public class DeleteRespondentUseCase
    implements UseCase<DeleteRespondentUseCase.Input, RespondentErasureOutput> {

  private final PrivacyApplicationGateway applications;
  private final RespondentErasureRepository respondents;
  private final DeletionAuditRepository audits;

  public DeleteRespondentUseCase(
      PrivacyApplicationGateway applications,
      RespondentErasureRepository respondents,
      DeletionAuditRepository audits) {
    this.applications = applications;
    this.respondents = respondents;
    this.audits = audits;
  }

  public record Input(
      String applicationId, Optional<String> reference, Optional<String> deviceId) {}

  @Override
  @Transactional
  public RespondentErasureOutput execute(Input input) {
    var applicationId =
        PrivacyScope.existingApplicationOf(applications, input.applicationId()).applicationId();
    var target = ErasureTarget.of(input.reference(), input.deviceId());

    var found = respondents.find(applicationId, target);
    if (found.isEmpty()) {
      log.info("Exclusão sem alvo application={}", applicationId.value());
      return RespondentErasureOutput.nothingToDelete();
    }

    var respondent = found.get();
    respondents.erase(respondent.respondentId());
    audits.create(
        DeletionAudit.record(applicationId, respondent.displays(), respondent.answers()));

    // Nunca a referência nem o identificador interno: o log também não pode guardar quem saiu.
    log.info(
        "Respondente excluído application={} exibicoes={} respostas={}",
        applicationId.value(),
        respondent.displays(),
        respondent.answers());

    return new RespondentErasureOutput(true, respondent.displays(), respondent.answers());
  }
}
