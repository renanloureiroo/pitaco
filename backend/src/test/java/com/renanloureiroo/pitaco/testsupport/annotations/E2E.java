package com.renanloureiroo.pitaco.testsupport.annotations;

import com.renanloureiroo.pitaco.infra.PitacoApplication;
import com.renanloureiroo.pitaco.infra.TestcontainersConfiguration;
import com.renanloureiroo.pitaco.testsupport.database.DatabaseCleaner;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// PitacoApplication é apontada explicitamente porque mora em `infra`: a busca ascendente por
// @SpringBootConfiguration não a encontra a partir dos testes sob `modules`.
//
// Nada de @Transactional aqui: com RANDOM_PORT o servidor atende em outra thread, então a
// transação do teste não enxergaria o commit do use case. A limpeza é explícita, via
// DatabaseCleaner.
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({TestcontainersConfiguration.class, DatabaseCleaner.class})
@AutoConfigureRestTestClient
// O limite por origem sobe para não interferir: toda a suíte sai do mesmo endereço e, com o
// padrão de produção, esgotaria a janela no meio de uma classe. O limite por chave fica num
// valor que nenhuma classe alcança com a própria chave, mas que um teste consegue estourar
// de propósito — sem abrir um segundo contexto, que custaria outra pilha de containers. O balde
// dos relatórios de erro do SDK fica no padrão de produção, que um teste estoura de propósito.
@SpringBootTest(
    classes = PitacoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "pitaco.collect.rate-limit.per-key.capacity=" + E2E.RATE_LIMIT_PER_KEY_CAPACITY,
      "pitaco.collect.rate-limit.per-origin.capacity=1000000",
      // A descarga do uso de versões é chamada pelo próprio teste; a agendada não pode gravar no
      // meio de uma classe, depois da limpeza do banco.
      "pitaco.health.sdk-usage.flush-interval=1h",
      // A documentação nasce desligada em produção; aqui ela é parte do que se testa.
      "springdoc.api-docs.enabled=true",
      "springdoc.swagger-ui.enabled=true"
    })
public @interface E2E {

  int RATE_LIMIT_PER_KEY_CAPACITY = 300;
}
