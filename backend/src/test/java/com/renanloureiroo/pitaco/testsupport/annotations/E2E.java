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
@SpringBootTest(
    classes = PitacoApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public @interface E2E {}
