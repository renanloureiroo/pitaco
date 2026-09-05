package com.renanloureiroo.pitaco.modules.app.domain.entities;

import com.renanloureiroo.pitaco.core.entity.Entity;
import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;

@Getter
public final class Application extends Entity<ApplicationId> {

  private static final String QUIET_PERIOD_INVALID_CODE = "application.quiet_period_invalid";
  private static final String RETENTION_INVALID_CODE = "application.retention_invalid";
  private static final String OPEN_TEXT_RETENTION_INVALID_CODE =
      "application.open_text_retention_invalid";

  private final Slug slug;
  private final Instant createdAt;

  private Name name;
  private Status status;
  private Integer quietPeriodDays;
  private Integer retentionDays;
  private Integer openTextRetentionDays;

  private Instant updatedAt;

  private Application(
      ApplicationId id,
      Slug slug,
      Name name,
      Status status,
      Integer quietPeriodDays,
      Integer retentionDays,
      Integer openTextRetentionDays,
      Instant createdAt,
      Instant updatedAt) {
    super(id);
    this.slug = slug;
    this.name = name;
    this.status = status;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;

    setQuietPeriodDays(quietPeriodDays);
    setRetentionDays(retentionDays);
    setOpenTextRetentionDays(openTextRetentionDays);
  }

  public static Application create(Slug slug, Name name) {
    return create(slug, name, null, null, null);
  }

  public static Application create(
      Slug slug,
      Name name,
      Integer quietPeriodDays,
      Integer retentionDays,
      Integer openTextRetentionDays) {
    var now = Instant.now();

    return new Application(
        ApplicationId.generate(),
        slug,
        name,
        Status.ACTIVE,
        quietPeriodDays,
        retentionDays,
        openTextRetentionDays,
        now,
        now);
  }

  public static Application restore(
      ApplicationId id,
      Slug slug,
      Name name,
      Status status,
      Integer quietPeriodDays,
      Integer retentionDays,
      Integer openTextRetentionDays,
      Instant createdAt,
      Instant updatedAt) {
    return new Application(
        id,
        slug,
        name,
        status,
        quietPeriodDays,
        retentionDays,
        openTextRetentionDays,
        createdAt,
        updatedAt);
  }

  public boolean isActive() {
    return status == Status.ACTIVE;
  }

  public Optional<Integer> quietPeriodDays() {
    return Optional.ofNullable(quietPeriodDays);
  }

  public Optional<Integer> retentionDays() {
    return Optional.ofNullable(retentionDays);
  }

  public Optional<Integer> openTextRetentionDays() {
    return Optional.ofNullable(openTextRetentionDays);
  }

  public Optional<Integer> effectiveOpenTextRetentionDays() {
    return openTextRetentionDays().or(this::retentionDays);
  }

  public void rename(Name name) {
    this.name = Objects.requireNonNull(name, "name é obrigatório");
    touch();
  }

  public void activate() {
    this.status = Status.ACTIVE;
    touch();
  }

  public void deactivate() {
    this.status = Status.INACTIVE;
    touch();
  }

  public void defineQuietPeriod(int days) {
    setQuietPeriodDays(days);
    touch();
  }

  public void removeQuietPeriod() {
    setQuietPeriodDays(null);
    touch();
  }

  public void defineRetention(int days) {
    setRetentionDays(days);
    touch();
  }

  public void removeRetention() {
    setRetentionDays(null);
    touch();
  }

  public void defineOpenTextRetention(int days) {
    setOpenTextRetentionDays(days);
    touch();
  }

  public void resetOpenTextRetention() {
    setOpenTextRetentionDays(null);
    touch();
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }

  private void setQuietPeriodDays(Integer days) {
    this.quietPeriodDays =
        days == null ? null : positive(days, QUIET_PERIOD_INVALID_CODE, "intervalo de descanso");
  }

  private void setRetentionDays(Integer days) {
    this.retentionDays =
        days == null ? null : positive(days, RETENTION_INVALID_CODE, "prazo de retenção");
  }

  private void setOpenTextRetentionDays(Integer days) {
    if (days == null) {
      this.openTextRetentionDays = null;
      return;
    }

    var validated =
        positive(days, OPEN_TEXT_RETENTION_INVALID_CODE, "prazo de retenção de texto livre");
    if (retentionDays != null && validated > retentionDays) {
      throw new DomainException(
          ErrorType.BUSINESS_RULE,
          OPEN_TEXT_RETENTION_INVALID_CODE,
          "Prazo de retenção de texto livre não pode ser maior que o prazo geral");
    }

    this.openTextRetentionDays = validated;
  }

  private static int positive(int days, String code, String subject) {
    if (days <= 0) {
      throw new DomainException(
          ErrorType.VALIDATION, code, "O " + subject + " deve ser de ao menos um dia");
    }
    return days;
  }
}
