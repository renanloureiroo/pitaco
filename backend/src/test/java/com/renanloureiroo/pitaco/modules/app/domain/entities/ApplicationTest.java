package com.renanloureiroo.pitaco.modules.app.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Name;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Application")
class ApplicationTest {

  static final Name NAME = Name.of("Acme App");
  static final Slug SLUG = Slug.from(NAME.value());

  static Application newApplication() {
    return ApplicationFactory.anApplication().withName(NAME.value()).build();
  }

  /**
   * O carimbo de edição não é mais escolhido pelo teste: o que dá para afirmar é que ele foi
   * refeito depois do instante em que a mutação começou — o que falha se {@code touch()} não for
   * chamado, porque aí ele continua no {@code createdAt}.
   */
  static void assertTouchedSince(Application application, Instant before) {
    assertThat(application.getUpdatedAt()).isAfterOrEqualTo(before);
  }

  @Test
  @DisplayName("nasce ativa, com identidade própria e sem políticas definidas")
  void nasce_ativa_com_identidade_propria_e_sem_politicas() {
    var application = newApplication();

    assertThat(application.id()).isNotNull();
    assertThat(application.getSlug()).isEqualTo(SLUG);
    assertThat(application.getName()).isEqualTo(NAME);
    assertThat(application.getStatus()).isEqualTo(Status.ACTIVE);
    assertThat(application.isActive()).isTrue();
    assertThat(application.getCreatedAt()).isNotNull();
    assertThat(application.quietPeriodDays()).isEmpty();
    assertThat(application.retentionDays()).isEmpty();
    assertThat(application.openTextRetentionDays()).isEmpty();
  }

  @Test
  @DisplayName("nasce com updatedAt igual ao createdAt")
  void nasce_com_updatedAt_igual_ao_createdAt() {
    var application = newApplication();

    assertThat(application.getUpdatedAt()).isEqualTo(application.getCreatedAt());
  }

  @Test
  @DisplayName("renomeia sem tocar no slug")
  void renomeia_sem_tocar_no_slug() {
    var application = newApplication();

    var before = Instant.now();
    application.rename(Name.of("Acme Brasil"));

    assertThat(application.getName()).isEqualTo(Name.of("Acme Brasil"));
    assertThat(application.getSlug()).isEqualTo(SLUG);
    assertTouchedSince(application, before);
  }

  @Test
  @DisplayName("alterna entre ativa e inativa")
  void alterna_entre_ativa_e_inativa() {
    var application = newApplication();

    var beforeDeactivate = Instant.now();
    application.deactivate();
    assertThat(application.getStatus()).isEqualTo(Status.INACTIVE);
    assertTouchedSince(application, beforeDeactivate);

    var beforeActivate = Instant.now();
    application.activate();
    assertThat(application.getStatus()).isEqualTo(Status.ACTIVE);
    assertTouchedSince(application, beforeActivate);
  }

  @Test
  @DisplayName("define e remove o intervalo de descanso")
  void define_e_remove_o_intervalo_de_descanso() {
    var application = newApplication();

    var beforeDefine = Instant.now();
    application.defineQuietPeriod(7);
    assertThat(application.quietPeriodDays()).contains(7);
    assertTouchedSince(application, beforeDefine);

    var beforeRemove = Instant.now();
    application.removeQuietPeriod();
    assertThat(application.quietPeriodDays()).isEmpty();
    assertTouchedSince(application, beforeRemove);
  }

  @Test
  @DisplayName("define e remove a retenção")
  void define_e_remove_a_retencao() {
    var application = newApplication();

    var beforeDefine = Instant.now();
    application.defineRetention(90);
    assertThat(application.retentionDays()).contains(90);
    assertTouchedSince(application, beforeDefine);

    var beforeRemove = Instant.now();
    application.removeRetention();
    assertThat(application.retentionDays()).isEmpty();
    assertTouchedSince(application, beforeRemove);
  }

  @Test
  @DisplayName("o createdAt nunca muda")
  void o_createdAt_nunca_muda() {
    var application = newApplication();
    var createdAt = application.getCreatedAt();

    application.rename(Name.of("Acme Brasil"));
    application.deactivate();

    assertThat(application.getCreatedAt()).isEqualTo(createdAt);
  }

  @ParameterizedTest(name = "{0} dia(s)")
  @ValueSource(ints = {0, -1})
  @DisplayName("rejeita prazo que não chega a um dia")
  void rejeita_prazo_que_nao_chega_a_um_dia(int invalid) {
    var application = newApplication();

    assertThatThrownBy(() -> application.defineQuietPeriod(invalid))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> application.defineRetention(invalid))
        .isInstanceOf(DomainException.class);
    assertThatThrownBy(() -> application.defineOpenTextRetention(invalid))
        .isInstanceOf(DomainException.class);
  }

  @Test
  @DisplayName("edição recusada não mexe no updatedAt")
  void edicao_recusada_nao_mexe_no_updatedAt() {
    var application = newApplication();
    var updatedAt = application.getUpdatedAt();

    assertThatThrownBy(() -> application.defineRetention(0)).isInstanceOf(DomainException.class);

    assertThat(application.getUpdatedAt()).isEqualTo(updatedAt);
  }

  @Test
  @DisplayName("texto livre sem prazo próprio segue a retenção geral")
  void texto_livre_sem_prazo_proprio_segue_a_retencao_geral() {
    var application = newApplication();
    application.defineRetention(90);

    assertThat(application.openTextRetentionDays()).isEmpty();
    assertThat(application.effectiveOpenTextRetentionDays()).contains(90);
  }

  @Test
  @DisplayName("texto livre com prazo próprio prevalece sobre a retenção geral")
  void texto_livre_com_prazo_proprio_prevalece_sobre_a_retencao_geral() {
    var application = newApplication();
    application.defineRetention(90);
    application.defineOpenTextRetention(30);

    assertThat(application.effectiveOpenTextRetentionDays()).contains(30);
  }

  @Test
  @DisplayName("sem retenção nenhuma, não há prazo para texto livre")
  void sem_retencao_nenhuma_nao_ha_prazo_para_texto_livre() {
    assertThat(newApplication().effectiveOpenTextRetentionDays()).isEmpty();
  }

  @Test
  @DisplayName("texto livre não pode durar mais que a retenção geral")
  void texto_livre_nao_pode_durar_mais_que_a_retencao_geral() {
    var application = newApplication();
    application.defineRetention(30);

    assertThatThrownBy(() -> application.defineOpenTextRetention(90))
        .isInstanceOf(DomainException.class)
        .satisfies(
            error -> {
              var domainError = (DomainException) error;
              assertThat(domainError.type()).isEqualTo(ErrorType.BUSINESS_RULE);
              assertThat(domainError.code()).isEqualTo("application.open_text_retention_invalid");
            });
  }

  @Test
  @DisplayName("texto livre volta a seguir o prazo geral")
  void texto_livre_volta_a_seguir_o_prazo_geral() {
    var application = newApplication();
    application.defineRetention(90);
    application.defineOpenTextRetention(30);

    var before = Instant.now();
    application.resetOpenTextRetention();

    assertThat(application.openTextRetentionDays()).isEmpty();
    assertThat(application.effectiveOpenTextRetentionDays()).contains(90);
    assertTouchedSince(application, before);
  }
}
