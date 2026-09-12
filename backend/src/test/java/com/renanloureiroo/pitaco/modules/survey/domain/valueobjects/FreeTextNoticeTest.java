package com.renanloureiroo.pitaco.modules.survey.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FreeTextNotice")
class FreeTextNoticeTest {

  @Test
  @DisplayName("Nasce ligado e com o texto padrão, curto e humano")
  void padrao() {
    var notice = FreeTextNotice.standard();

    assertThat(notice.enabled()).isTrue();
    assertThat(notice.customText()).isEmpty();
    assertThat(notice.text()).isEqualTo(FreeTextNotice.DEFAULT_TEXT);
  }

  @Test
  @DisplayName("Texto próprio substitui o padrão, sem espaço nas pontas")
  void texto_proprio() {
    var notice = FreeTextNotice.standard().withText("  Não escreva seu telefone.  ");

    assertThat(notice.text()).isEqualTo("Não escreva seu telefone.");
  }

  @Test
  @DisplayName("Voltar ao padrão descarta o texto próprio e mantém o estado ligado ou desligado")
  void volta_ao_padrao() {
    var notice = FreeTextNotice.standard().withText("Outro").enabled(false).withDefaultText();

    assertThat(notice.enabled()).isFalse();
    assertThat(notice.text()).isEqualTo(FreeTextNotice.DEFAULT_TEXT);
  }

  @Test
  @DisplayName("Recusa texto vazio e texto acima de 200 caracteres")
  void recusa_texto_invalido() {
    assertThatThrownBy(() -> new FreeTextNotice(true, Optional.of("   ")))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("survey.free_text_notice_invalid");
    assertThatThrownBy(() -> new FreeTextNotice(true, Optional.of("a".repeat(201))))
        .isInstanceOf(DomainException.class)
        .extracting("code")
        .isEqualTo("survey.free_text_notice_invalid");
  }
}
