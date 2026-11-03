package com.renanloureiroo.pitaco.modules.survey.application.errors;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.PublicationImpediment;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Carrega a lista inteira, não a primeira falha: quem monta a pesquisa descobre tudo que falta
// em uma requisição só. A borda a devolve na extensão `impediments` do corpo RFC 9457.
public final class SurveyNotPublishable extends ApplicationException {

  private static final String CODE = "survey.not_publishable";

  private final List<PublicationImpediment> impediments;

  public SurveyNotPublishable(List<PublicationImpediment> impediments) {
    super(
        ErrorType.BUSINESS_RULE, CODE, "O rascunho ainda tem pendências que impedem a publicação");
    this.impediments = List.copyOf(impediments);
  }

  public List<PublicationImpediment> impediments() {
    return impediments;
  }

  @Override
  public Map<String, Object> extensions() {
    return Map.of("impediments", impediments.stream().map(SurveyNotPublishable::flatten).toList());
  }

  private static Map<String, Object> flatten(PublicationImpediment impediment) {
    var entry = new LinkedHashMap<String, Object>();
    entry.put("code", impediment.code());
    entry.put("field", impediment.field());
    impediment.questionKey().ifPresent(key -> entry.put("questionKey", key.value()));
    return entry;
  }
}
