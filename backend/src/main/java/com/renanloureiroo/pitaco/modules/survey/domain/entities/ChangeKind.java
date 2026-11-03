package com.renanloureiroo.pitaco.modules.survey.domain.entities;

// O que autoriza somar as respostas de duas versões: cosmética diz que o sentido do que se
// pergunta não mudou, e é verificada; semântica é sempre aceita.
public enum ChangeKind {
  COSMETIC,
  SEMANTIC
}
