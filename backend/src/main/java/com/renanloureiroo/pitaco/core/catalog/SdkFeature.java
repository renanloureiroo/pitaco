package com.renanloureiroo.pitaco.core.catalog;

// O que o SDK precisa saber fazer além de desenhar o tipo da pergunta. Recurso novo entra aqui e
// no SdkCapabilities, e o compilador recusa o catálogo que esquecer dele.
public enum SdkFeature {
  CONDITIONAL_DISPLAY,
  SCALE_LABELS,
  FREE_TEXT_NOTICE
}
