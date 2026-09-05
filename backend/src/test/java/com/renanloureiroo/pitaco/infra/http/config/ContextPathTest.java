package com.renanloureiroo.pitaco.infra.http.config;

import com.renanloureiroo.pitaco.infra.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * O {@code context-path} move tudo que a aplicação expõe para baixo de
 * {@code /api} — inclusive ferramental. É o comportamento desejado, e este
 * teste existe para que uma mudança nele seja deliberada.
 */
@Import({TestcontainersConfiguration.class, ContextPathTest.PingControllerConfig.class})
@AutoConfigureRestTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContextPathTest {

    @Autowired
    RestTestClient client;

    @TestConfiguration(proxyBeanMethods = false)
    static class PingControllerConfig {

        @RestController
        static class PingController {

            @GetMapping("/ping")
            String ping() {
                return "pong";
            }
        }
    }

    @Test
    void serve_a_rota_do_controller_sob_o_context_path() {
        client.get()
                .uri("/ping")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .isEqualTo("pong");
    }

    @Test
    void serve_o_ferramental_sob_o_mesmo_context_path() {
        client.get().uri("/v3/api-docs").exchange().expectStatus().isOk();
        client.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk();
        client.get().uri("/actuator/health").exchange().expectStatus().isOk();
    }
}
