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
import lombok.experimental.Accessors;

@Getter
@Accessors(fluent = true)
public final class Application extends Entity<ApplicationId> {

    private static final String QUIET_PERIOD_INVALID_CODE = "application.quiet_period_invalid";
    private static final String RETENTION_INVALID_CODE = "application.retention_invalid";
    private static final String OPEN_TEXT_RETENTION_INVALID_CODE = "application.open_text_retention_invalid";

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
            Instant createdAt,
            Instant updatedAt) {
        super(id);
        this.slug = slug;
        this.name = name;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /**
     * Nova aplicação: nasce ativa e sem nenhuma política definida — sem intervalo
     * de descanso e sem descarte por retenção, que são os padrões conservadores.
     *
     * <p>
     * O instante da criação é lido uma vez só e serve aos dois carimbos, de modo
     * que aplicação recém-criada tem {@code createdAt} e {@code updatedAt}
     * exatamente iguais — nunca separados por alguns microssegundos.
     */
    public static Application create(Slug slug, Name name) {
        var now = Instant.now();
        return new Application(
                ApplicationId.generate(),
                slug,
                name,
                Status.ACTIVE,
                now,
                now);
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /** Vazio quando a aplicação não impõe descanso entre pesquisas. */
    public Optional<Integer> quietPeriodDays() {
        return Optional.ofNullable(quietPeriodDays);
    }

    /** Vazio quando nada é descartado por tempo. */
    public Optional<Integer> retentionDays() {
        return Optional.ofNullable(retentionDays);
    }

    /** Vazio quando texto livre não tem prazo próprio e segue a retenção geral. */
    public Optional<Integer> openTextRetentionDays() {
        return Optional.ofNullable(openTextRetentionDays);
    }

    /**
     * Prazo que de fato vale para texto livre: o próprio, quando definido, ou o
     * da retenção geral. Vazio quando não há descarte nenhum.
     */
    public Optional<Integer> effectiveOpenTextRetentionDays() {
        return openTextRetentionDays().or(this::retentionDays);
    }

    /** O nome é rótulo de painel e muda livremente; o {@code slug}, não. */
    public void rename(Name name, Instant now) {
        this.name = Objects.requireNonNull(name, "name é obrigatório");
        touch(now);
    }

    public void activate(Instant now) {
        this.status = Status.ACTIVE;
        touch(now);
    }

    public void deactivate(Instant now) {
        this.status = Status.INACTIVE;
        touch(now);
    }

    /** Passa a exigir um intervalo de descanso entre pesquisas do respondente. */
    public void defineQuietPeriod(int days, Instant now) {
        this.quietPeriodDays = positive(days, QUIET_PERIOD_INVALID_CODE, "intervalo de descanso");
        touch(now);
    }

    /** Volta a permitir pesquisas sem intervalo. */
    public void removeQuietPeriod(Instant now) {
        this.quietPeriodDays = null;
        touch(now);
    }

    public void defineRetention(int days, Instant now) {
        this.retentionDays = positive(days, RETENTION_INVALID_CODE, "prazo de retenção");
        touch(now);
    }

    /** Volta a guardar resposta indefinidamente. */
    public void removeRetention(Instant now) {
        this.retentionDays = null;
        touch(now);
    }

    /**
     * Prazo próprio para texto livre, sempre mais curto que o geral: é o dado com
     * maior chance de conter algo que o respondente digitou sobre si.
     */
    public void defineOpenTextRetention(int days, Instant now) {
        var validated = positive(
                days, OPEN_TEXT_RETENTION_INVALID_CODE, "prazo de retenção de texto livre");
        if (retentionDays != null && validated > retentionDays) {
            throw new DomainException(
                    ErrorType.BUSINESS_RULE,
                    OPEN_TEXT_RETENTION_INVALID_CODE,
                    "Prazo de retenção de texto livre não pode ser maior que o prazo geral");
        }
        this.openTextRetentionDays = validated;
        touch(now);
    }

    /** Texto livre volta a seguir o prazo geral de retenção. */
    public void resetOpenTextRetention(Instant now) {
        this.openTextRetentionDays = null;
        touch(now);
    }

    /**
     * Registra quando a aplicação mudou.
     *
     * <p>
     * Chamado ao fim de cada mutação, depois da validação: edição recusada não é
     * edição, e não pode mexer no {@code updatedAt}.
     */
    private void touch(Instant now) {
        this.updatedAt = Objects.requireNonNull(now, "now é obrigatório");
    }

    private static int positive(int days, String code, String subject) {
        if (days <= 0) {
            throw new DomainException(
                    ErrorType.VALIDATION, code, "O " + subject + " deve ser de ao menos um dia");
        }
        return days;
    }
}
