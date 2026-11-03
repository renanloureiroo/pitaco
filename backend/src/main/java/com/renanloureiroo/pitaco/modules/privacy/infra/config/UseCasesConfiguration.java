package com.renanloureiroo.pitaco.modules.privacy.infra.config;

import com.renanloureiroo.pitaco.core.transaction.Transactor;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.PrivacyApplicationGateway;
import com.renanloureiroo.pitaco.modules.privacy.application.gateways.RetentionSchedule;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.AggregateSnapshotRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.DeletionAuditRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RespondentErasureRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionRunRepository;
import com.renanloureiroo.pitaco.modules.privacy.application.repositories.RetentionStore;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.ApplyRetentionUseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.DeleteRespondentUseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.GetRetentionPreviewUseCase;
import com.renanloureiroo.pitaco.modules.privacy.application.usecases.ListDeletionAuditsUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(value = "privacyUseCasesConfiguration", proxyBeanMethods = false)
public class UseCasesConfiguration {

  @Bean
  DeleteRespondentUseCase deleteRespondentUseCase(
      PrivacyApplicationGateway applications,
      RespondentErasureRepository respondents,
      DeletionAuditRepository audits) {
    return new DeleteRespondentUseCase(applications, respondents, audits);
  }

  @Bean
  ListDeletionAuditsUseCase listDeletionAuditsUseCase(
      PrivacyApplicationGateway applications, DeletionAuditRepository audits) {
    return new ListDeletionAuditsUseCase(applications, audits);
  }

  @Bean
  ApplyRetentionUseCase applyRetentionUseCase(
      PrivacyApplicationGateway applications,
      RetentionStore store,
      AggregateSnapshotRepository snapshots,
      RetentionRunRepository runs,
      Transactor transactor,
      PrivacyProperties properties) {
    return new ApplyRetentionUseCase(
        applications, store, snapshots, runs, transactor, properties.retention().batchSize());
  }

  @Bean
  GetRetentionPreviewUseCase getRetentionPreviewUseCase(
      PrivacyApplicationGateway applications,
      RetentionStore store,
      RetentionRunRepository runs,
      RetentionSchedule schedule) {
    return new GetRetentionPreviewUseCase(applications, store, runs, schedule);
  }
}
