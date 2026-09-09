package com.renanloureiroo.pitaco.modules.collect.domain.valueobjects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AttributeSnapshot")
class AttributeSnapshotTest {

  private static Map<String, String> pairs(int count) {
    return IntStream.range(0, count)
        .boxed()
        .collect(
            LinkedHashMap::new, (map, i) -> map.put("attr" + i, "v" + i), LinkedHashMap::putAll);
  }

  @Test
  void aceita_o_limite_de_cinquenta_pares() {
    assertThat(AttributeSnapshot.of(pairs(50)).values()).hasSize(50);
  }

  @Test
  void recusa_acima_de_cinquenta_pares() {
    assertThatThrownBy(() -> AttributeSnapshot.of(pairs(51))).satisfies(AttributeSnapshotTest::invalido);
  }

  @Test
  void recusa_nome_acima_de_oitenta_caracteres() {
    assertThatThrownBy(() -> AttributeSnapshot.of(Map.of("a".repeat(81), "v")))
        .satisfies(AttributeSnapshotTest::invalido);
  }

  @Test
  void recusa_valor_acima_de_duzentos_caracteres() {
    assertThatThrownBy(() -> AttributeSnapshot.of(Map.of("plano", "v".repeat(201))))
        .satisfies(AttributeSnapshotTest::invalido);
  }

  @Test
  void recusa_nome_em_branco() {
    assertThatThrownBy(() -> AttributeSnapshot.of(Map.of("   ", "v")))
        .satisfies(AttributeSnapshotTest::invalido);
  }

  @Test
  @DisplayName("Nomes são normalizados com strip")
  void normaliza_o_nome() {
    assertThat(AttributeSnapshot.of(Map.of("  plano  ", "premium")).valueOf("plano"))
        .contains("premium");
  }

  @Test
  @DisplayName("A ordem de inserção é preservada")
  void preserva_a_ordem_de_insercao() {
    var informed = new LinkedHashMap<String, String>();
    informed.put("zeta", "1");
    informed.put("alfa", "2");
    informed.put("meio", "3");

    assertThat(AttributeSnapshot.of(informed).values().keySet())
        .containsExactly("zeta", "alfa", "meio");
  }

  @Test
  @DisplayName("Atributo que nenhuma regra usa é aceito e guardado")
  void aceita_atributo_sem_regra() {
    assertThat(AttributeSnapshot.of(Map.of("nada_usa_isto", "x")).valueOf("nada_usa_isto"))
        .contains("x");
  }

  @Test
  void valor_vazio_e_aceito_e_legivel() {
    assertThat(AttributeSnapshot.of(Map.of("plano", "")).valueOf("plano")).contains("");
  }

  @Test
  void vazio_nao_tem_nenhum_atributo() {
    assertThat(AttributeSnapshot.empty().valueOf("plano")).isEmpty();
  }

  private static void invalido(Throwable error) {
    assertThat(error).isInstanceOf(DomainException.class);
    assertThat(((DomainException) error).code()).isEqualTo("attributes.invalid");
  }
}
