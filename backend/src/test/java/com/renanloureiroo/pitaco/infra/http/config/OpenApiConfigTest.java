package com.renanloureiroo.pitaco.infra.http.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.infra.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

@Import(TestcontainersConfiguration.class)
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiConfigTest {

    @Autowired
    RestTestClient client;

    @Test
    void expoe_o_contrato_openapi_com_os_metadados_da_api() {
        client.get()
                .uri("/v3/api-docs")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("\"title\":\"Pitaco API\"")
                        .contains("\"version\":\"v1\""));
    }

    @Test
    void serve_a_interface_do_swagger_ui() {
        client.get()
                .uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus()
                .isOk();
    }
}
