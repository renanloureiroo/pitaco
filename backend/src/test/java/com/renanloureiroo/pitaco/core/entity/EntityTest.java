package com.renanloureiroo.pitaco.core.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.ErrorType;
import com.renanloureiroo.pitaco.core.identity.Id;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class EntityTest {

    static final class AnswerId extends Id {
        private AnswerId(String value) {
            super(value);
        }

        static AnswerId generate() {
            return new AnswerId(newValue());
        }

        static AnswerId of(String value) {
            return new AnswerId(value);
        }
    }

    static final class Answer extends Entity<AnswerId> {
        private final String text;

        Answer(AnswerId id, String text) {
            super(id);
            this.text = text;
        }

        String text() {
            return text;
        }
    }

    static final class Question extends Entity<AnswerId> {
        Question(AnswerId id) {
            super(id);
        }
    }

    @Test
    void expoe_o_proprio_identificador() {
        var id = AnswerId.generate();

        assertThat(new Answer(id, "42").id()).isEqualTo(id);
    }

    @Test
    void a_mesma_entidade_permanece_igual_quando_o_conteudo_muda() {
        var id = AnswerId.generate();

        assertThat(new Answer(id, "original"))
                .isEqualTo(new Answer(id, "editada"))
                .hasSameHashCodeAs(new Answer(id, "editada"));
    }

    @Test
    void entidades_com_identificadores_distintos_nao_sao_iguais() {
        assertThat(new Answer(AnswerId.generate(), "mesmo texto"))
                .isNotEqualTo(new Answer(AnswerId.generate(), "mesmo texto"));
    }

    @Test
    void entidades_de_tipos_diferentes_nao_sao_iguais() {
        var shared = AnswerId.generate();

        assertThat(new Answer(shared, "42")).isNotEqualTo(new Question(shared));
    }

    @Test
    void nao_e_igual_a_null_nem_a_objeto_de_outro_tipo() {
        var answer = new Answer(AnswerId.generate(), "42");

        assertThat(answer).isNotEqualTo(null).isNotEqualTo("42");
    }

    @Test
    void colecao_trata_leituras_diferentes_da_mesma_entidade_como_um_elemento() {
        var id = AnswerId.of("a1");

        var answers = new HashSet<>(List.of(new Answer(id, "original"), new Answer(id, "editada")));

        assertThat(answers).hasSize(1);
    }

    @Test
    void identifica_a_entidade_no_toString() {
        assertThat(new Answer(AnswerId.of("a1"), "42")).hasToString("Answer(a1)");
    }

    @Test
    void rejeita_entidade_sem_identificador() {
        assertThatThrownBy(() -> new Answer(null, "42"))
                .isInstanceOf(DomainException.class)
                .satisfies(error -> {
                    var domainError = (DomainException) error;
                    assertThat(domainError.type()).isEqualTo(ErrorType.VALIDATION);
                    assertThat(domainError.code()).isEqualTo("entity.id_required");
                });
    }
}
