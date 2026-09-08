package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SurveyName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.Optional;
import lombok.Getter;

@Getter
public final class Survey extends Entity<SurveyId> {

  public static final int FIRST_VERSION_NUMBER = 1;

  private static final String APPLICATION_REQUIRED_CODE = "survey.application_required";
  private static final String DRAFT_ALREADY_OPEN_CODE = "survey_version.draft_already_open";
  private static final String NOT_PUBLISHED_CODE = "survey.not_published";
  private static final String TRANSITION_NOT_ALLOWED_CODE = "survey.transition_not_allowed";

  private final ApplicationId applicationId;
  private final Instant createdAt;

  private SurveyName name;
  private SurveyLifecycle lifecycle;
  private Integer publishedVersionNumber;
  private Integer draftVersionNumber;

  private Survey(
      SurveyId id,
      ApplicationId applicationId,
      SurveyName name,
      SurveyLifecycle lifecycle,
      Integer publishedVersionNumber,
      Integer draftVersionNumber,
      Instant createdAt) {
    super(id);

    if (applicationId == null) {
      throw new DomainException(
          ErrorType.VALIDATION,
          APPLICATION_REQUIRED_CODE,
          "Pesquisa precisa pertencer a uma aplicação");
    }

    this.applicationId = applicationId;
    this.name = name;
    this.lifecycle = lifecycle;
    this.publishedVersionNumber = publishedVersionNumber;
    this.draftVersionNumber = draftVersionNumber;
    this.createdAt = createdAt;
  }

  public static Survey create(ApplicationId applicationId, SurveyName name) {
    return new Survey(
        SurveyId.generate(),
        applicationId,
        name,
        SurveyLifecycle.DRAFT,
        null,
        FIRST_VERSION_NUMBER,
        Instant.now());
  }

  public static Survey restore(
      SurveyId id,
      ApplicationId applicationId,
      SurveyName name,
      SurveyLifecycle lifecycle,
      Integer publishedVersionNumber,
      Integer draftVersionNumber,
      Instant createdAt) {
    return new Survey(
        id, applicationId, name, lifecycle, publishedVersionNumber, draftVersionNumber, createdAt);
  }

  public Optional<Integer> publishedVersionNumber() {
    return Optional.ofNullable(publishedVersionNumber);
  }

  public Optional<Integer> draftVersionNumber() {
    return Optional.ofNullable(draftVersionNumber);
  }

  public boolean hasDraft() {
    return draftVersionNumber != null;
  }

  public boolean isPublished() {
    return publishedVersionNumber != null;
  }

  public void rename(SurveyName newName) {
    this.name = newName;
  }

  public void openDraft(int versionNumber) {
    if (hasDraft()) {
      throw new DomainException(
          ErrorType.CONFLICT,
          DRAFT_ALREADY_OPEN_CODE,
          "Já existe um rascunho de versão aberto nesta pesquisa");
    }
    this.draftVersionNumber = versionNumber;
  }

  public void discardDraft() {
    this.draftVersionNumber = null;
  }

  public void markPublished(int versionNumber) {
    this.publishedVersionNumber = versionNumber;
    this.draftVersionNumber = null;
    this.lifecycle = SurveyLifecycle.PUBLISHED;
  }

  public void pause() {
    requirePublishedOnce();
    requireNotIn(SurveyLifecycle.ENDED, SurveyLifecycle.PAUSED);

    this.lifecycle = SurveyLifecycle.PAUSED;
  }

  public void resume() {
    requirePublishedOnce();
    requireNotIn(SurveyLifecycle.ENDED, SurveyLifecycle.PUBLISHED);

    this.lifecycle = SurveyLifecycle.PUBLISHED;
  }

  public void end() {
    requirePublishedOnce();
    requireNotIn(SurveyLifecycle.ENDED);

    this.lifecycle = SurveyLifecycle.ENDED;
  }

  // Rascunho e "já publicada alguma vez" são recusas diferentes: quem nunca publicou não tem o
  // que pausar, quem encerrou não tem volta.
  private void requirePublishedOnce() {
    if (lifecycle == SurveyLifecycle.DRAFT) {
      throw new DomainException(
          ErrorType.BUSINESS_RULE, NOT_PUBLISHED_CODE, "A pesquisa ainda não foi publicada");
    }
  }

  private void requireNotIn(SurveyLifecycle... forbidden) {
    for (var state : forbidden) {
      if (lifecycle == state) {
        throw new DomainException(
            ErrorType.BUSINESS_RULE,
            TRANSITION_NOT_ALLOWED_CODE,
            "A pesquisa não admite esta transição a partir do estado atual");
      }
    }
  }

  // Só PUBLISHED consulta a janela: pausada e encerrada são decisão de comando e a ignoram.
  public SurveyState stateAt(Instant now, Optional<TriggerWindow> publishedWindow) {
    return switch (lifecycle) {
      case DRAFT -> SurveyState.DRAFT;
      case PAUSED -> SurveyState.PAUSED;
      case ENDED -> SurveyState.ENDED;
      case PUBLISHED ->
          publishedWindow.map(window -> stateWithin(window, now)).orElse(SurveyState.ACTIVE);
    };
  }

  private static SurveyState stateWithin(TriggerWindow window, Instant now) {
    if (!window.hasOpenedAt(now)) {
      return SurveyState.SCHEDULED;
    }
    return window.hasClosedAt(now) ? SurveyState.ENDED : SurveyState.ACTIVE;
  }
}
