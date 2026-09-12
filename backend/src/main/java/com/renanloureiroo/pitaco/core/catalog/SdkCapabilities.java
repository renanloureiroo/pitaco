package com.renanloureiroo.pitaco.core.catalog;

import java.util.Collection;

// A versão mínima do SDK que sabe renderizar cada tipo e cada recurso. Switch sem default: tipo
// ou recurso novo sem versão declarada não compila. Tudo nasce na 1.0.0 porque é a primeira
// versão publicada do SDK.
public final class SdkCapabilities {

  private SdkCapabilities() {}

  public static SdkVersion minimumFor(QuestionType type) {
    return switch (type) {
      case SINGLE_CHOICE, MULTIPLE_CHOICE, RATING, SCALE, NPS, FREE_TEXT -> SdkVersion.BASELINE;
    };
  }

  public static SdkVersion minimumFor(SdkFeature feature) {
    return switch (feature) {
      case CONDITIONAL_DISPLAY, SCALE_LABELS, FREE_TEXT_NOTICE -> SdkVersion.BASELINE;
    };
  }

  public static SdkVersion minimumFor(
      Collection<QuestionType> types, Collection<SdkFeature> features) {
    var required = SdkVersion.BASELINE;

    for (var type : types) {
      required = SdkVersion.highest(required, minimumFor(type));
    }
    for (var feature : features) {
      required = SdkVersion.highest(required, minimumFor(feature));
    }

    return required;
  }
}
