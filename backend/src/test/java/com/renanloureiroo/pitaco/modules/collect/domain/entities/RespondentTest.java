package com.renanloureiroo.pitaco.modules.collect.domain.entities;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentity;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.RespondentIdentityKind;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Respondent")
class RespondentTest {

  private static final Instant NOW = Instant.parse("2026-09-08T18:00:00Z");
  private static final RespondentIdentity IDENTITY =
      new RespondentIdentity(RespondentIdentityKind.APP_REFERENCE, "u-8f1c");

  @Test
  @DisplayName("Nasce visto agora: primeira e última vez são o mesmo instante")
  void nasce_com_os_dois_instantes_iguais() {
    var respondent = Respondent.create(ApplicationId.generate(), IDENTITY, NOW);

    assertThat(respondent.getFirstSeenAt()).isEqualTo(NOW);
    assertThat(respondent.getLastSeenAt()).isEqualTo(NOW);
  }

  @Test
  @DisplayName("Ser visto de novo só avança a última vez")
  void ser_visto_avanca_apenas_a_ultima_vez() {
    var respondent = Respondent.create(ApplicationId.generate(), IDENTITY, NOW);
    var later = NOW.plusSeconds(3600);

    respondent.seenAt(later);

    assertThat(respondent.getFirstSeenAt()).isEqualTo(NOW);
    assertThat(respondent.getLastSeenAt()).isEqualTo(later);
  }

  @Test
  @DisplayName("Restaurar não recalcula nada")
  void restore_devolve_o_que_veio_do_banco() {
    var id = RespondentId.generate();
    var applicationId = ApplicationId.generate();
    var lastSeen = NOW.plusSeconds(600);

    var respondent = Respondent.restore(id, applicationId, IDENTITY, NOW, lastSeen);

    assertThat(respondent.id()).isEqualTo(id);
    assertThat(respondent.getApplicationId()).isEqualTo(applicationId);
    assertThat(respondent.getIdentity()).isEqualTo(IDENTITY);
    assertThat(respondent.getFirstSeenAt()).isEqualTo(NOW);
    assertThat(respondent.getLastSeenAt()).isEqualTo(lastSeen);
  }
}
