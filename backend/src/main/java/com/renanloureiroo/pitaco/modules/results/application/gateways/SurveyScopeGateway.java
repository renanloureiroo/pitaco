package com.renanloureiroo.pitaco.modules.results.application.gateways;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import java.util.Optional;

// A única travessia da leitura de resultados até a autoria e a aplicação: existência da
// pesquisa no escopo, o prazo que apaga texto livre da leitura e o formato de origem. Uma
// consulta, três fatos.
public interface SurveyScopeGateway {

  String NPS_TEMPLATE = "NPS";

  Optional<SurveyScope> scopeOf(ApplicationId applicationId, SurveyId surveyId);

  // mayContainPersonalData: alguma versão publicada tem texto livre, que é por onde dado pessoal
  // entra sem ninguém pedir.
  record SurveyScope(
      boolean everPublished,
      Optional<Integer> openTextRetentionDays,
      Optional<String> templateKind,
      boolean mayContainPersonalData) {

    public SurveyScope {
      templateKind = templateKind == null ? Optional.empty() : templateKind;
    }

    public SurveyScope(
        boolean everPublished,
        Optional<Integer> openTextRetentionDays,
        Optional<String> templateKind) {
      this(everPublished, openTextRetentionDays, templateKind, false);
    }

    public SurveyScope(boolean everPublished, Optional<Integer> openTextRetentionDays) {
      this(everPublished, openTextRetentionDays, Optional.empty());
    }

    public boolean isNpsTemplate() {
      return templateKind.filter(NPS_TEMPLATE::equals).isPresent();
    }
  }
}
