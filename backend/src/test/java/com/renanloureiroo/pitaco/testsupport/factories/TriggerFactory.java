package com.renanloureiroo.pitaco.testsupport.factories;

import com.renanloureiroo.pitaco.modules.survey.domain.entities.RuleOperation;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.EventName;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SamplingRate;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.SegmentationRule;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.Trigger;
import com.renanloureiroo.pitaco.modules.survey.domain.valueobjects.TriggerWindow;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

public final class TriggerFactory {

  public static final String DEFAULT_EVENT = "checkout.completed";

  private String event = DEFAULT_EVENT;
  private Instant start = Instant.now().minus(1, ChronoUnit.HOURS);
  private Instant end;
  private double rate = 0.25;

  private TriggerFactory() {}

  public static TriggerFactory aTrigger() {
    return new TriggerFactory();
  }

  /** Janela já aberta e sem fim: a pesquisa fica ativa assim que publicada. */
  public static TriggerFactory anOpenTrigger() {
    return aTrigger();
  }

  /** Janela que só abre no futuro: a pesquisa fica agendada. */
  public static TriggerFactory aScheduledTrigger() {
    return aTrigger().startingIn(1, ChronoUnit.HOURS);
  }

  /** Janela já encerrada: a pesquisa aparece encerrada sem ninguém ter comandado nada. */
  public static TriggerFactory aClosedTrigger() {
    var now = Instant.now();
    return aTrigger().startingAt(now.minus(2, ChronoUnit.HOURS)).endingAt(now.minusSeconds(60));
  }

  /** Janela sem fim: tempo indeterminado. */
  public static TriggerFactory anEndlessTrigger() {
    return aTrigger().endingAt(null);
  }

  public TriggerFactory forEvent(String event) {
    this.event = event;
    return this;
  }

  public TriggerFactory startingAt(Instant start) {
    this.start = start;
    return this;
  }

  public TriggerFactory startingIn(long amount, ChronoUnit unit) {
    this.start = Instant.now().plus(amount, unit);
    return this;
  }

  public TriggerFactory endingAt(Instant end) {
    this.end = end;
    return this;
  }

  public TriggerFactory endingIn(long amount, ChronoUnit unit) {
    this.end = Instant.now().plus(amount, unit);
    return this;
  }

  public TriggerFactory withRate(double rate) {
    this.rate = rate;
    return this;
  }

  public Trigger build() {
    return new Trigger(EventName.of(event), TriggerWindow.of(start, end), SamplingRate.of(rate));
  }

  public static SegmentationRule aPresenceRule(String attribute) {
    return SegmentationRule.create(attribute, RuleOperation.PRESENT, Optional.empty());
  }

  public static SegmentationRule anEqualityRule(String attribute, String value) {
    return SegmentationRule.create(attribute, RuleOperation.EQUALS, Optional.of(value));
  }
}
