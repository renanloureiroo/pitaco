package com.renanloureiroo.pitaco.modules.app.application.usecases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.renanloureiroo.pitaco.modules.app.application.errors.ApplicationAlreadyExistsWithSameSlug;
import com.renanloureiroo.pitaco.modules.app.application.repositories.ApplicationRepository;
import com.renanloureiroo.pitaco.modules.app.domain.entities.ApplicationId;
import com.renanloureiroo.pitaco.modules.app.domain.entities.Status;
import com.renanloureiroo.pitaco.modules.app.domain.valueobjects.Slug;
import com.renanloureiroo.pitaco.testsupport.factories.ApplicationFactory;
import com.renanloureiroo.pitaco.testsupport.repositories.InMemoryApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Create Application sut")
public class CreateApplicationUseCaseTest {

  private ApplicationRepository applications;
  private CreateApplicationUseCase sut;

  @BeforeEach
  void setUp() {
    applications = new InMemoryApplicationRepository();
    sut = new CreateApplicationUseCase(applications);
  }

  @Test
  @DisplayName("Deve criar e persistir uma nova aplicação")
  void deve_criar_uma_aplicacao() {
    var output = sut.execute(ApplicationFactory.anApplicationWithPolicies().asCreateInput());

    var created = applications.findById(ApplicationId.of(output.id())).orElseThrow();

    assertThat(output.slug()).isEqualTo("acme-app");
    assertThat(created.getName().value()).isEqualTo("Acme App");
    assertThat(created.getStatus()).isEqualTo(Status.ACTIVE);
    assertThat(created.quietPeriodDays()).contains(15);
    assertThat(created.retentionDays()).contains(180);
    assertThat(created.openTextRetentionDays()).contains(30);
  }

  @Test
  @DisplayName("Deve criar sem políticas quando nenhuma for informada")
  void deve_criar_sem_politicas_quando_nenhuma_for_informada() {
    var output = sut.execute(ApplicationFactory.anApplication().asCreateInput());

    var created = applications.findById(ApplicationId.of(output.id())).orElseThrow();

    assertThat(created.quietPeriodDays()).isEmpty();
    assertThat(created.retentionDays()).isEmpty();
    assertThat(created.openTextRetentionDays()).isEmpty();
  }

  @Test
  @DisplayName("Deve criar uma aplicação com o slug customizado enviado")
  void deve_criar_uma_aplicacao_com_slug_customizado() {
    var output =
        sut.execute(
            ApplicationFactory.anApplication()
                .withName("Teste")
                .withSlug("custom-slug")
                .asCreateInput());

    var existing = applications.findById(ApplicationId.of(output.id())).orElseThrow();

    assertTrue(existing.getSlug().equals(Slug.of("custom-slug")));
  }

  @Test
  @DisplayName("Deve derivar o slug do nome")
  void deve_derivar_slug_do_nome() {
    var NAME = "Slug Derivado do Nome";
    var output =
        sut.execute(
            ApplicationFactory.anApplication().withName("Slug Derivado do Nome").asCreateInput());

    var existing = applications.findById(ApplicationId.of(output.id())).orElseThrow();

    assertTrue(existing.getSlug().equals(Slug.from(NAME)));
  }

  @Test
  @DisplayName("Não deve criar uma nova aplicação quando já existir uma com o mesmo slug")
  void nao_deve_criar_uma_aplicacao_quando_ja_existir_uma_com_mesmo_slug() {
    var existing = ApplicationFactory.anApplication().buildSavedIn(applications);

    assertThatThrownBy(() -> sut.execute(ApplicationFactory.anApplication().asCreateInput()))
        .isInstanceOf(ApplicationAlreadyExistsWithSameSlug.class)
        .extracting(error -> ((ApplicationAlreadyExistsWithSameSlug) error).slug())
        .isEqualTo(existing.getSlug());

    assertThat(applications.findAll()).containsExactly(existing);
  }
}
