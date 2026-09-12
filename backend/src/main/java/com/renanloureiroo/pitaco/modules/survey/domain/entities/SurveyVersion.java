package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.catalog.SdkCapabilities;
import com.renanloureiroo.pitaco.core.catalog.QuestionType;
import com.renanloureiroo.pitaco.core.catalog.SdkFeature;
import com.renanloureiroo.pitaco.core.catalog.SdkVersion;
import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.SurveyId;
import com.renanloureiroo.pitaco.core.identity.SurveyVersionId;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.PublicationImpediment;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.DisplayCondition;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

// O recipiente de todo o conteúdo: perguntas, disparo e regras. FR-025 congela os três com um
// único ato, e só existe um jeito de congelar três coisas de uma vez — elas estarem juntas.
@Getter
public final class SurveyVersion extends Entity<SurveyVersionId> {

  public static final int FIRST_COMPARABILITY_GROUP = 1;

  private static final String CONTENT_FROZEN_CODE = "survey.content_frozen";
  private static final String QUESTION_NOT_FOUND_CODE = "question.not_found";
  private static final String ORDER_INVALID_CODE = "question.order_invalid";
  private static final String TRIGGER_NOT_DEFINED_CODE = "trigger.not_defined";
  private static final String RULE_NOT_FOUND_CODE = "segmentation_rule.not_found";
  private static final String NOT_PUBLISHABLE_CODE = "survey.not_publishable";
  private static final String CHANGE_KIND_REQUIRED_CODE = "survey_version.change_kind_required";
  private static final String COSMETIC_REFUSED_CODE = "survey_version.cosmetic_refused";

  private final SurveyId surveyId;
  private final int number;

  private final List<Question> questions;
  private final List<SegmentationRule> rules;

  private SurveyVersionStatus status;
  private Trigger trigger;
  private ChangeKind changeKind;
  private String changeSummary;
  private int comparabilityGroup;
  private Instant publishedAt;

  private SurveyVersion(
      SurveyVersionId id,
      SurveyId surveyId,
      int number,
      SurveyVersionStatus status,
      List<Question> questions,
      Optional<Trigger> trigger,
      List<SegmentationRule> rules,
      Optional<ChangeKind> changeKind,
      Optional<String> changeSummary,
      int comparabilityGroup,
      Optional<Instant> publishedAt) {
    super(id);
    this.surveyId = surveyId;
    this.number = number;
    this.status = status;
    this.questions = new ArrayList<>(questions);
    this.trigger = trigger.orElse(null);
    this.rules = new ArrayList<>(rules);
    this.changeKind = changeKind.orElse(null);
    this.changeSummary = changeSummary.orElse(null);
    this.comparabilityGroup = comparabilityGroup;
    this.publishedAt = publishedAt.orElse(null);
  }

  public static SurveyVersion create(SurveyId surveyId, int number) {
    return new SurveyVersion(
        SurveyVersionId.generate(),
        surveyId,
        number,
        SurveyVersionStatus.DRAFT,
        List.of(),
        Optional.empty(),
        List.of(),
        Optional.empty(),
        Optional.empty(),
        FIRST_COMPARABILITY_GROUP,
        Optional.empty());
  }

  public static SurveyVersion restore(
      SurveyVersionId id,
      SurveyId surveyId,
      int number,
      SurveyVersionStatus status,
      List<Question> questions,
      Optional<Trigger> trigger,
      List<SegmentationRule> rules,
      Optional<ChangeKind> changeKind,
      Optional<String> changeSummary,
      int comparabilityGroup,
      Optional<Instant> publishedAt) {
    return new SurveyVersion(
        id,
        surveyId,
        number,
        status,
        questions,
        trigger,
        rules,
        changeKind,
        changeSummary,
        comparabilityGroup,
        publishedAt);
  }

  public List<Question> getQuestions() {
    return List.copyOf(questions);
  }

  public List<SegmentationRule> getRules() {
    return List.copyOf(rules);
  }

  public Optional<Trigger> trigger() {
    return Optional.ofNullable(trigger);
  }

  public Optional<ChangeKind> changeKind() {
    return Optional.ofNullable(changeKind);
  }

  public Optional<String> changeSummary() {
    return Optional.ofNullable(changeSummary);
  }

  public Optional<Instant> publishedAt() {
    return Optional.ofNullable(publishedAt);
  }

  // O que o SDK precisa saber fazer para desenhar esta versão inteira: o tipo de cada pergunta e
  // os recursos que alguma delas usa.
  public SdkVersion requiredSdkVersion() {
    return requiredSdkVersion(false);
  }

  // O aviso de texto livre é da pesquisa, não da versão: quem sabe se ele está ligado informa.
  // Só exige o recurso quando há campo de texto livre para ele acompanhar.
  public SdkVersion requiredSdkVersion(boolean freeTextNoticeEnabled) {
    var features = java.util.EnumSet.noneOf(SdkFeature.class);

    if (freeTextNoticeEnabled
        && questions.stream().anyMatch(question -> question.getType() == QuestionType.FREE_TEXT)) {
      features.add(SdkFeature.FREE_TEXT_NOTICE);
    }

    for (var question : questions) {
      if (question.condition().isPresent()) {
        features.add(SdkFeature.CONDITIONAL_DISPLAY);
      }
      if (!question.getLabels().isEmpty()) {
        features.add(SdkFeature.SCALE_LABELS);
      }
    }

    return SdkCapabilities.minimumFor(
        questions.stream().map(Question::getType).toList(), features);
  }

  public boolean isEditable() {
    return status == SurveyVersionStatus.DRAFT;
  }

  public Optional<Question> question(QuestionId id) {
    return questions.stream().filter(question -> question.id().equals(id)).findFirst();
  }

  public Question addQuestion(Question.Draft draft) {
    requireEditable();

    var question = Question.create(draft, questions.size() + 1);
    QuestionConditions.check(question, questions);
    questions.add(question);
    return question;
  }

  public void updateQuestion(QuestionId id, Question.Draft draft) {
    requireEditable();

    var index = indexOf(id);
    var rewritten = questions.get(index).rewrittenAs(draft);
    var candidate = new ArrayList<>(questions);
    candidate.set(index, rewritten);

    QuestionConditions.check(rewritten, candidate);
    QuestionConditions.requireDependentsStillValid(rewritten, candidate);

    questions.set(index, rewritten);
  }

  // Remover a origem de uma condição é recusado em vez de limpar a condição da dependente: a
  // pergunta passaria a aparecer para todos sem que ninguém tivesse pedido isso.
  public void removeQuestion(QuestionId id) {
    requireEditable();

    var index = indexOf(id);
    QuestionConditions.requireNoDependents(questions.get(index), questions);

    questions.remove(index);
    compactPositions();
  }

  // A integridade é verificada no resultado final, não a cada passo: a lista precisa ser uma
  // permutação exata das perguntas existentes, e nada é aplicado antes de ela passar.
  public void reorder(List<QuestionId> newOrder) {
    requireEditable();

    var distinct = new LinkedHashSet<>(newOrder);
    var existing = questions.stream().map(Question::id).collect(Collectors.toSet());

    if (distinct.size() != newOrder.size() || !distinct.equals(existing)) {
      throw new DomainException(
          ErrorType.VALIDATION,
          ORDER_INVALID_CODE,
          "A nova ordem deve conter exatamente as perguntas desta versão, sem repetição");
    }

    var reordered = new ArrayList<Question>();
    for (var index = 0; index < newOrder.size(); index++) {
      reordered.add(question(newOrder.get(index)).orElseThrow().movedTo(index + 1));
    }
    QuestionConditions.requireSourcesFirst(reordered);

    questions.clear();
    questions.addAll(reordered);
  }

  public void defineTrigger(Trigger newTrigger) {
    requireEditable();

    this.trigger = newTrigger;
  }

  public void addRule(SegmentationRule rule) {
    requireEditable();

    if (trigger == null) {
      throw new DomainException(
          ErrorType.BUSINESS_RULE,
          TRIGGER_NOT_DEFINED_CODE,
          "Defina o disparo antes de acrescentar regras de segmentação");
    }
    rules.add(rule);
  }

  public void removeRule(SegmentationRuleId id) {
    requireEditable();

    var removed = rules.removeIf(rule -> rule.id().equals(id));
    if (!removed) {
      throw new DomainException(
          ErrorType.NOT_FOUND, RULE_NOT_FOUND_CODE, "Regra de segmentação não encontrada");
    }
  }

  // Sempre a lista completa, nunca a primeira falha: quem monta a pesquisa descobre tudo que
  // falta em uma requisição só.
  public List<PublicationImpediment> publicationImpediments() {
    var impediments = new ArrayList<PublicationImpediment>();

    if (questions.isEmpty()) {
      impediments.add(PublicationImpediment.of(PublicationImpediment.NO_QUESTIONS, "questions"));
    }

    for (var index = 0; index < questions.size(); index++) {
      var question = questions.get(index);

      if (question.getStatement().value().isBlank()) {
        impediments.add(
            PublicationImpediment.ofQuestion(
                PublicationImpediment.STATEMENT_MISSING,
                "questions[" + index + "].statement",
                question.getKey()));
      }
      if (question.getType().requiresOptions() && question.getOptions().isEmpty()) {
        impediments.add(
            PublicationImpediment.ofQuestion(
                PublicationImpediment.OPTIONS_MISSING,
                "questions[" + index + "].options",
                question.getKey()));
      }
    }

    if (trigger == null) {
      impediments.add(PublicationImpediment.of(PublicationImpediment.TRIGGER_MISSING, "trigger"));
    } else if (trigger
        .window()
        .end()
        .filter(end -> !end.isAfter(trigger.window().start()))
        .isPresent()) {
      impediments.add(
          PublicationImpediment.of(PublicationImpediment.WINDOW_INVALID, "trigger.window"));
    }

    return List.copyOf(impediments);
  }

  // O único ponto que grava publishedAt e comparabilityGroup e muda o status.
  public SurveyVersion publish(
      Instant now,
      Optional<SurveyVersion> previous,
      Optional<ChangeKind> declared,
      Optional<String> summary) {
    requireEditable();

    if (!publicationImpediments().isEmpty()) {
      throw new DomainException(
          ErrorType.BUSINESS_RULE,
          NOT_PUBLISHABLE_CODE,
          "O rascunho ainda tem pendências que impedem a publicação");
    }

    this.comparabilityGroup =
        previous.isEmpty()
            ? FIRST_COMPARABILITY_GROUP
            : groupAfter(previous.get(), verifiedKind(previous.get(), declared));

    this.changeKind = previous.isEmpty() ? null : declared.orElseThrow();
    this.changeSummary = previous.isEmpty() ? null : summary.orElse(null);
    this.status = SurveyVersionStatus.PUBLISHED;
    this.publishedAt = now;

    return this;
  }

  private ChangeKind verifiedKind(SurveyVersion previous, Optional<ChangeKind> declared) {
    var kind =
        declared.orElseThrow(
            () ->
                new DomainException(
                    ErrorType.VALIDATION,
                    CHANGE_KIND_REQUIRED_CODE,
                    "A partir da segunda versão é preciso declarar a natureza da mudança"));

    if (kind == ChangeKind.COSMETIC) {
      var differences = ChangeClassification.between(previous.getQuestions(), getQuestions());
      if (!differences.isEmpty()) {
        throw new DomainException(
            ErrorType.BUSINESS_RULE,
            COSMETIC_REFUSED_CODE,
            "A mudança declarada como cosmética alterou a estrutura das perguntas");
      }
    }

    return kind;
  }

  private static int groupAfter(SurveyVersion previous, ChangeKind kind) {
    return kind == ChangeKind.COSMETIC
        ? previous.getComparabilityGroup()
        : previous.getComparabilityGroup() + 1;
  }

  // Perguntas com a mesma QuestionKey e QuestionId novo: a linhagem atravessa, a identidade
  // da linha não.
  public SurveyVersion copyAsDraft(int newNumber) {
    return new SurveyVersion(
        SurveyVersionId.generate(),
        surveyId,
        newNumber,
        SurveyVersionStatus.DRAFT,
        questions.stream().map(Question::copyForNewVersion).toList(),
        trigger(),
        rules.stream().map(SegmentationRule::copyForNewVersion).toList(),
        Optional.empty(),
        Optional.empty(),
        comparabilityGroup,
        Optional.empty());
  }

  // Pesquisa nova a partir desta versão: rascunho número 1, chaves novas — não há resposta
  // anterior com que comparar — e condições reapontadas para as chaves novas. Janela já
  // encerrada não serve a ninguém; vira janela aberta a partir de agora e sem fim, que o
  // autor revê antes de publicar.
  public SurveyVersion duplicateFor(SurveyId newSurveyId, Instant now) {
    var keys = new HashMap<QuestionKey, QuestionKey>();
    questions.forEach(question -> keys.put(question.getKey(), QuestionKey.generate()));

    var copied =
        questions.stream()
            .map(
                question ->
                    question.duplicatedAs(
                        keys.get(question.getKey()),
                        question
                            .condition()
                            .map(condition -> condition.pointingTo(keys.get(condition.sourceKey())))))
            .toList();

    return new SurveyVersion(
        SurveyVersionId.generate(),
        newSurveyId,
        Survey.FIRST_VERSION_NUMBER,
        SurveyVersionStatus.DRAFT,
        copied,
        trigger().map(current -> reopenedIfClosed(current, now)),
        rules.stream().map(SegmentationRule::copyForNewVersion).toList(),
        Optional.empty(),
        Optional.empty(),
        FIRST_COMPARABILITY_GROUP,
        Optional.empty());
  }

  private static Trigger reopenedIfClosed(Trigger trigger, Instant now) {
    if (!trigger.window().hasClosedAt(now)) {
      return trigger;
    }
    return new Trigger(
        trigger.event(), new TriggerWindow(now, Optional.empty()), trigger.rate());
  }

  public boolean sameContentAs(SurveyVersion other) {
    return Objects.equals(trigger, other.trigger)
        && ruleFingerprintOf(this).equals(ruleFingerprintOf(other))
        && questionFingerprintOf(this).equals(questionFingerprintOf(other));
  }

  private static List<String> questionFingerprintOf(SurveyVersion version) {
    return version.questions.stream()
        .map(
            question ->
                String.join(
                    "|",
                    question.getKey().value(),
                    question.getStatement().value(),
                    question.getType().name(),
                    String.valueOf(question.isRequired()),
                    optionFingerprintOf(question),
                    question.range().map(String::valueOf).orElse("-"),
                    question.getLabels().min().orElse("-")
                        + "~"
                        + question.getLabels().max().orElse("-"),
                    question.condition().map(DisplayCondition::fingerprint).orElse("-")))
        .toList();
  }

  private static String optionFingerprintOf(Question question) {
    return question.getOptions().stream()
        .map(option -> option.value() + "=" + option.label())
        .collect(Collectors.joining(","));
  }

  private static Set<String> ruleFingerprintOf(SurveyVersion version) {
    return version.rules.stream()
        .map(rule -> rule.attribute() + "|" + rule.operation() + "|" + rule.value().orElse("-"))
        .collect(Collectors.toSet());
  }

  private int indexOf(QuestionId id) {
    for (var index = 0; index < questions.size(); index++) {
      if (questions.get(index).id().equals(id)) {
        return index;
      }
    }
    throw new DomainException(
        ErrorType.NOT_FOUND, QUESTION_NOT_FOUND_CODE, "Pergunta não encontrada");
  }

  private void compactPositions() {
    for (var index = 0; index < questions.size(); index++) {
      questions.set(index, questions.get(index).movedTo(index + 1));
    }
  }

  private void requireEditable() {
    if (!isEditable()) {
      throw new DomainException(
          ErrorType.BUSINESS_RULE,
          CONTENT_FROZEN_CODE,
          "O conteúdo desta versão está publicado e não aceita mais alterações");
    }
  }
}
