package com.renanloureiroo.pitaco.infra.http.error;

import com.renanloureiroo.pitaco.core.error.ApplicationException;
import io.micrometer.tracing.Tracer;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Ponto único de tradução de erros para respostas HTTP.
 *
 * <p>Responde no formato RFC 9457 ({@code application/problem+json}) e
 * acrescenta ao corpo duas propriedades: {@code code}, o identificador estável
 * do erro definido no core, e {@code traceId}, que liga a resposta vista pelo
 * usuário ao trace correspondente no observability stack.
 *
 * <p>Estende {@link ResponseEntityExceptionHandler} para também cobrir as
 * exceções lançadas pelo próprio Spring MVC — payload malformado, método não
 * suportado, parâmetro ausente — com o mesmo formato de resposta.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final String CODE_PROPERTY = "code";
    private static final String TRACE_ID_PROPERTY = "traceId";
    private static final String ERRORS_PROPERTY = "errors";
    private static final String VALIDATION_CODE = "request.invalid";
    private static final String UNEXPECTED_CODE = "internal.unexpected";

    private final Optional<Tracer> tracer;

    /**
     * @param tracer ausente quando o tracing está desligado — a resposta
     *               simplesmente sai sem {@code traceId}
     */
    public ApiExceptionHandler(Optional<Tracer> tracer) {
        this.tracer = tracer;
    }

    /** Erros previstos pelo core, com natureza e código próprios. */
    @ExceptionHandler(ApplicationException.class)
    public ResponseEntity<ProblemDetail> handleApplicationException(
            ApplicationException error, WebRequest request) {

        var status = ErrorTypeHttpStatus.of(error.type());
        var problem = problemOf(status, error.getMessage(), error.code(), request);

        log.debug("Erro de aplicação [{}] {}", error.code(), error.getMessage());

        return ResponseEntity.status(status).body(problem);
    }

    /** Qualquer erro não previsto: nunca expõe a mensagem interna ao client. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(
            Exception error, WebRequest request) {

        var status = HttpStatus.INTERNAL_SERVER_ERROR;
        var problem = problemOf(
                status, "Erro inesperado ao processar a requisição", UNEXPECTED_CODE, request);

        log.error("Erro inesperado ao processar a requisição", error);

        return ResponseEntity.status(status).body(problem);
    }

    /** Bean Validation nos DTOs de entrada, detalhando o erro de cada campo. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException error,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        var problem = problemOf(status, "Requisição inválida", VALIDATION_CODE, request);
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setProperty(ERRORS_PROPERTY, fieldErrorsOf(error));

        return ResponseEntity.status(status).body(problem);
    }

    private ProblemDetail problemOf(
            HttpStatusCode status, String detail, String code, WebRequest request) {

        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        if (status instanceof HttpStatus resolved) {
            problem.setTitle(resolved.getReasonPhrase());
        }
        problem.setProperty(CODE_PROPERTY, code);
        applyTraceId(problem);
        applyInstance(problem, request);
        return problem;
    }

    private Map<String, String> fieldErrorsOf(MethodArgumentNotValidException error) {
        return error.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> fieldError.getDefaultMessage() == null
                                ? "inválido"
                                : fieldError.getDefaultMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));
    }

    private void applyTraceId(ProblemDetail problem) {
        tracer.map(Tracer::currentSpan)
                .map(span -> span.context().traceId())
                .ifPresent(traceId -> problem.setProperty(TRACE_ID_PROPERTY, traceId));
    }

    private void applyInstance(ProblemDetail problem, WebRequest request) {
        var description = request.getDescription(false);
        if (description.startsWith("uri=")) {
            problem.setInstance(URI.create(description.substring(4)));
        }
    }
}
