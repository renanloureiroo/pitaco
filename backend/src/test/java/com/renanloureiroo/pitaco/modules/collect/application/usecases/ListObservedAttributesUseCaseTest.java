package com.renanloureiroo.pitaco.modules.collect.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.renanloureiroo.pitaco.core.identity.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationNotFound;
import com.renanloureiroo.pitaco.modules.collect.application.outputs.ObservedAttributeOutput;
import com.renanloureiroo.pitaco.modules.collect.domain.entities.ObservedAttribute;
import com.renanloureiroo.pitaco.modules.collect.domain.valueobjects.AttributeSnapshot;
import com.renanloureiroo.pitaco.testsupport.gateways.InMemoryCollectApplicationScopeGateway;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryObservedAttributeRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ListObservedAttributesUseCase")
class ListObservedAttributesUseCaseTest {

  private static final Instant FIRST = Instant.parse("2026-09-10T10:00:00Z");
  private static final Instant SECOND = Instant.parse("2026-09-11T10:00:00Z");

  private final InMemoryCollectApplicationScopeGateway applications =
      new InMemoryCollectApplicationScopeGateway();
  private final InMemoryObservedAttributeRepository attributes =
      new InMemoryObservedAttributeRepository();

  private ListObservedAttributesUseCase useCase;
  private ApplicationId applicationId;

  @BeforeEach
  void setUp() {
    useCase = new ListObservedAttributesUseCase(applications, attributes);
    applicationId = applications.anActiveApplication();
  }

  private ListObservedAttributesUseCase.Output list(ApplicationId owner) {
    return useCase.execute(new ListObservedAttributesUseCase.Input(owner.value(), 0, 20));
  }

  @Test
  @DisplayName("Do visto mais recentemente para o mais antigo, com os valores em ordem alfabética")
  void ordena_atributos_e_valores() {
    attributes.record(applicationId, AttributeSnapshot.of(Map.of("plano", "pro")), FIRST);
    attributes.record(applicationId, AttributeSnapshot.of(Map.of("plano", "free")), FIRST);
    attributes.record(applicationId, AttributeSnapshot.of(Map.of("versao", "2.1")), SECOND);

    var output = list(applicationId);

    assertThat(output.total()).isEqualTo(2);
    assertThat(output.items())
        .extracting(ObservedAttributeOutput::name)
        .containsExactly("versao", "plano");
    assertThat(output.items().getLast().values())
        .extracting(ObservedAttributeOutput.ValueOutput::value)
        .containsExactly("free", "pro");
  }

  @Test
  @DisplayName("Os valores param de acumular no limite, mas o já conhecido continua avançando")
  void limite_de_valores() {
    for (var index = 0; index < ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE; index++) {
      attributes.record(
          applicationId, AttributeSnapshot.of(Map.of("cidade", "c" + index)), FIRST);
    }

    attributes.record(applicationId, AttributeSnapshot.of(Map.of("cidade", "nova")), SECOND);
    attributes.record(applicationId, AttributeSnapshot.of(Map.of("cidade", "c0")), SECOND);

    var cidade = list(applicationId).items().getFirst();
    assertThat(cidade.values()).hasSize(ObservedAttribute.MAX_VALUES_PER_ATTRIBUTE);
    assertThat(cidade.values())
        .extracting(ObservedAttributeOutput.ValueOutput::value)
        .doesNotContain("nova");
    assertThat(cidade.values())
        .filteredOn(value -> value.value().equals("c0"))
        .singleElement()
        .satisfies(value -> assertThat(value.lastSeenAt()).isEqualTo(SECOND));
  }

  @Test
  @DisplayName("Aplicação inativa continua servindo o catálogo; desconhecida é não encontrada")
  void escopo_da_aplicacao() {
    var inactive = applications.anInactiveApplication();
    attributes.record(inactive, AttributeSnapshot.of(Map.of("plano", "pro")), FIRST);

    assertThat(list(inactive).items()).hasSize(1);
    assertThat(list(applicationId).items()).isEmpty();
    assertThatThrownBy(() -> list(ApplicationId.generate()))
        .isInstanceOf(ApplicationNotFound.class);
  }
}
