package com.renanloureiroo.pitaco.modules.survey.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.ChangeClassification;
import com.renanloureiroo.pitaco.modules.survey.domain.publication.PublicationImpediment;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import java.time.Instant;
import java.util.ArrayList;
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

  public boolean isEditable() {
    return status == SurveyVersionStatus.DRAFT;
  }

  public Optional<Question> question(QuestionId id) {
    return questions.stream().filter(question -> question.id().equals(id)).findFirst();
  }

  public Question addQuestion(Question.Draft draft) {
    requireEditable();

    var question = Question.create(draft, questions.size() + 1);
    questions.add(question);
    return question;
  }

  public void updateQuestion(QuestionId id, Question.Draft draft) {
    requireEditable();

    var index = indexOf(id);
    questions.set(index, questions.get(index).rewrittenAs(draft));
  }

  public void removeQuestion(QuestionId id) {
    requireEditable();

    questions.remove(indexOf(id));
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

    var reordered =
        newOrder.stream().map(id -> question(id).orElseThrow()).collect(Collectors.toList());

    questions.clear();
    questions.addAll(reordered);
    compactPositions();
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
                    question.range().map(String::valueOf).orElse("-")))
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
