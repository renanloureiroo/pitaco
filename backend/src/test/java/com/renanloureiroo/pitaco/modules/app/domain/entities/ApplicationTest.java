package com.renanloureiroo.pitaco.modules.app.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ApplicationTest {

    static final Instant LATER = Instant.parse("2126-09-05T00:00:00Z");
    static final Instant EVEN_LATER = LATER.plus(Duration.ofHours(1));

    static final Slug SLUG = Slug.of("acme-app");
    static final Name NAME = Name.of("Acme App");

    static Application newApplication() {
        return Application.create(SLUG, NAME);
    }

    @Test
    void nasce_ativa_com_identidade_propria_e_sem_politicas() {
        var application = newApplication();

        assertThat(application.id()).isNotNull();
        assertThat(application.slug()).isEqualTo(SLUG);
        assertThat(application.name()).isEqualTo(NAME);
        assertThat(application.status()).isEqualTo(Status.ACTIVE);
        assertThat(application.isActive()).isTrue();
        assertThat(application.createdAt()).isNotNull();
        assertThat(application.quietPeriodDays()).isEmpty();
        assertThat(application.retentionDays()).isEmpty();
        assertThat(application.openTextRetentionDays()).isEmpty();
    }

    @Test
    void nasce_com_updatedAt_igual_ao_createdAt() {
        var application = newApplication();

        assertThat(application.updatedAt()).isEqualTo(application.createdAt());
    }

    @Test
    void renomeia_sem_tocar_no_slug() {
        var application = newApplication();

        application.rename(Name.of("Acme Brasil"), LATER);

        assertThat(application.name()).isEqualTo(Name.of("Acme Brasil"));
        assertThat(application.slug()).isEqualTo(SLUG);
        assertThat(application.updatedAt()).isEqualTo(LATER);
    }

    @Test
    void alterna_entre_ativa_e_inativa() {
        var application = newApplication();

        application.deactivate(LATER);
        assertThat(application.status()).isEqualTo(Status.INACTIVE);
        assertThat(application.updatedAt()).isEqualTo(LATER);

        application.activate(EVEN_LATER);
        assertThat(application.status()).isEqualTo(Status.ACTIVE);
        assertThat(application.updatedAt()).isEqualTo(EVEN_LATER);
    }

    @Test
    void define_e_remove_o_intervalo_de_descanso() {
        var application = newApplication();

        application.defineQuietPeriod(7, LATER);
        assertThat(application.quietPeriodDays()).contains(7);
        assertThat(application.updatedAt()).isEqualTo(LATER);

        application.removeQuietPeriod(EVEN_LATER);
        assertThat(application.quietPeriodDays()).isEmpty();
        assertThat(application.updatedAt()).isEqualTo(EVEN_LATER);
    }

    @Test
    void define_e_remove_a_retencao() {
        var application = newApplication();

        application.defineRetention(90, LATER);
        assertThat(application.retentionDays()).contains(90);
        assertThat(application.updatedAt()).isEqualTo(LATER);

        application.removeRetention(EVEN_LATER);
        assertThat(application.retentionDays()).isEmpty();
        assertThat(application.updatedAt()).isEqualTo(EVEN_LATER);
    }

    @Test
    void o_createdAt_nunca_muda() {
        var application = newApplication();
        var createdAt = application.createdAt();

        application.rename(Name.of("Acme Brasil"), LATER);
        application.deactivate(EVEN_LATER);

        assertThat(application.createdAt()).isEqualTo(createdAt);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejeita_prazo_que_nao_chega_a_um_dia(int invalid) {
        var application = newApplication();

        assertThatThrownBy(() -> application.defineQuietPeriod(invalid, LATER))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> application.defineRetention(invalid, LATER))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> application.defineOpenTextRetention(invalid, LATER))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void edicao_recusada_nao_mexe_no_updatedAt() {
        var application = newApplication();
        var updatedAt = application.updatedAt();

        assertThatThrownBy(() -> application.defineRetention(0, LATER))
                .isInstanceOf(DomainException.class);

        assertThat(application.updatedAt()).isEqualTo(updatedAt);
    }

    @Test
    void texto_livre_sem_prazo_proprio_segue_a_retencao_geral() {
        var application = newApplication();
        application.defineRetention(90, LATER);

        assertThat(application.openTextRetentionDays()).isEmpty();
        assertThat(application.effectiveOpenTextRetentionDays()).contains(90);
    }

    @Test
    void texto_livre_com_prazo_proprio_prevalece_sobre_a_retencao_geral() {
        var application = newApplication();
        application.defineRetention(90, LATER);
        application.defineOpenTextRetention(30, LATER);

        assertThat(application.effectiveOpenTextRetentionDays()).contains(30);
    }

    @Test
    void sem_retencao_nenhuma_nao_ha_prazo_para_texto_livre() {
        assertThat(newApplication().effectiveOpenTextRetentionDays()).isEmpty();
    }

    @Test
    void texto_livre_nao_pode_durar_mais_que_a_retencao_geral() {
        var application = newApplication();
        application.defineRetention(30, LATER);

        assertThatThrownBy(() -> application.defineOpenTextRetention(90, LATER))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    var domainError = (DomainException) error;
                    assertThat(domainError.type()).isEqualTo(ErrorType.BUSINESS_RULE);
                    assertThat(domainError.code())
                            .isEqualTo("application.open_text_retention_invalid");
                });
    }

    @Test
    void texto_livre_volta_a_seguir_o_prazo_geral() {
        var application = newApplication();
        application.defineRetention(90, LATER);
        application.defineOpenTextRetention(30, LATER);

        application.resetOpenTextRetention(EVEN_LATER);

        assertThat(application.openTextRetentionDays()).isEmpty();
        assertThat(application.effectiveOpenTextRetentionDays()).contains(90);
        assertThat(application.updatedAt()).isEqualTo(EVEN_LATER);
    }
}
