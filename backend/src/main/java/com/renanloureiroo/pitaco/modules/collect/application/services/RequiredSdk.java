package com.renanloureiroo.pitaco.modules.collect.application.services;

import com.renanloureiroo.pitaco.core.catalog.SdkCapabilities;
import com.renanloureiroo.pitaco.core.catalog.SdkFeature;
import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableQuestion;
import com.renanloureiroo.pitaco.modules.collect.application.gateways.PublishedSurveyCatalog.DeliverableSurvey;
import java.util.EnumSet;

// A mesma leitura que a autoria faz da versão em rascunho, aqui sobre o que de fato foi entregue.
public final class RequiredSdk {

  private RequiredSdk() {}

  public static SdkVersion of(DeliverableSurvey survey) {
    var features = EnumSet.noneOf(SdkFeature.class);

    if (survey.hasFreeTextNotice()) {
      features.add(SdkFeature.FREE_TEXT_NOTICE);
    }

    for (DeliverableQuestion question : survey.questions()) {
      if (question.isConditional()) {
        features.add(SdkFeature.CONDITIONAL_DISPLAY);
      }
      if (question.minLabel().isPresent() || question.maxLabel().isPresent()) {
        features.add(SdkFeature.SCALE_LABELS);
      }
    }

    return SdkCapabilities.minimumFor(
        survey.questions().stream().map(DeliverableQuestion::type).toList(), features);
  }
}
