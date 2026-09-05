package com.renanloureiroo.pitaco.infra.http.error;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.renanloureiroo.pitaco.core.error.DomainException;
import com.renanloureiroo.pitaco.core.error.NotFoundException;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class ApiExceptionHandlerTest {

  private static final String TRACE_ID = "0af7651916cd43dd8448eb211c80319c";

  record CreateAnswerRequest(@NotBlank(message = "não pode ser vazio") String text) {}

  @RestController
  static class TestController {

    @GetMapping("/answers/not-found")
    String notFound() {
      throw new NotFoundException("answer.not_found", "Resposta 42 não encontrada");
    }

    @GetMapping("/answers/business-rule")
    String businessRule() {
      throw new DomainException("answer.already_submitted", "Resposta já foi enviada");
    }

    @GetMapping("/answers/boom")
    String boom() {
      throw new IllegalStateException("conexão jdbc://user:senha@host falhou");
    }

    @PostMapping("/answers")
    String create(@Valid @RequestBody CreateAnswerRequest request) {
      return request.text();
    }
  }

  private MockMvc mockMvcWith(Optional<Tracer> tracer) {
    return MockMvcBuilders.standaloneSetup(new TestController())
        .setControllerAdvice(new ApiExceptionHandler(tracer))
        .build();
  }

  private MockMvc mockMvcWithoutTracing() {
    return mockMvcWith(Optional.empty());
  }

  private MockMvc mockMvcTracing() {
    var context = mock(TraceContext.class);
    when(context.traceId()).thenReturn(TRACE_ID);

    var span = mock(Span.class);
    when(span.context()).thenReturn(context);

    var tracer = mock(Tracer.class);
    when(tracer.currentSpan()).thenReturn(span);

    return mockMvcWith(Optional.of(tracer));
  }

  @Test
  void traduz_not_found_para_404_com_code() throws Exception {
    mockMvcWithoutTracing()
        .perform(get("/answers/not-found"))
        .andExpect(status().isNotFound())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.code").value("answer.not_found"))
        .andExpect(jsonPath("$.detail").value("Resposta 42 não encontrada"))
        .andExpect(jsonPath("$.status").value(404))
        .andExpect(jsonPath("$.instance").value("/answers/not-found"));
  }

  @Test
  void traduz_regra_de_negocio_para_422() throws Exception {
    mockMvcWithoutTracing()
        .perform(get("/answers/business-rule"))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("answer.already_submitted"));
  }

  @Test
  void erro_inesperado_vira_500_sem_vazar_mensagem_interna() throws Exception {
    mockMvcWithoutTracing()
        .perform(get("/answers/boom"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("internal.unexpected"))
        .andExpect(jsonPath("$.detail").value("Erro inesperado ao processar a requisição"))
        .andExpect(content().string(not(containsString("senha"))));
  }

  @Test
  void erro_de_validacao_detalha_cada_campo() throws Exception {
    mockMvcWithoutTracing()
        .perform(
            post("/answers").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("request.invalid"))
        .andExpect(jsonPath("$.errors.text").value("não pode ser vazio"));
  }

  @Test
  void inclui_o_trace_id_quando_ha_span_ativo() throws Exception {
    var mockMvc = mockMvcTracing();

    mockMvc.perform(get("/answers/not-found")).andExpect(jsonPath("$.traceId").value(TRACE_ID));
    mockMvc.perform(get("/answers/boom")).andExpect(jsonPath("$.traceId").value(TRACE_ID));
    mockMvc
        .perform(
            post("/answers").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"  \"}"))
        .andExpect(jsonPath("$.traceId").value(TRACE_ID));
  }

  @Test
  void omite_o_trace_id_quando_o_tracing_esta_desligado() throws Exception {
    mockMvcWithoutTracing()
        .perform(get("/answers/not-found"))
        .andExpect(jsonPath("$.traceId").doesNotExist());
  }

  @Test
  void omite_o_trace_id_quando_nao_ha_span_ativo() throws Exception {
    var tracer = mock(Tracer.class);
    when(tracer.currentSpan()).thenReturn(null);

    mockMvcWith(Optional.of(tracer))
        .perform(get("/answers/not-found"))
        .andExpect(jsonPath("$.traceId").doesNotExist());
  }
}
