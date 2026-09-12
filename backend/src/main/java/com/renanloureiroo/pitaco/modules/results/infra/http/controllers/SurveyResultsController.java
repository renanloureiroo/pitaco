package com.renanloureiroo.pitaco.modules.results.infra.http.controllers;

import com.renanloureiroo.pitaco.infra.http.dtos.PageResponseDTO;
import com.renanloureiroo.pitaco.modules.results.application.usecases.ExportSurveyResultsUseCase;
import com.renanloureiroo.pitaco.modules.results.application.usecases.GetSurveyResultsUseCase;
import com.renanloureiroo.pitaco.modules.results.application.usecases.ListOpenAnswersUseCase;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.OpenAnswerResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.OpenAnswersQueryDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.ResultsQueryDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.dtos.SurveyResultsResponseDTO;
import com.renanloureiroo.pitaco.modules.results.infra.http.export.CsvExportWriter;
import com.renanloureiroo.pitaco.modules.results.infra.http.presenters.OpenAnswerPresenter;
import com.renanloureiroo.pitaco.modules.results.infra.http.presenters.SurveyResultsPresenter;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/applications/{applicationId}/surveys/{surveyId}/results")
public class SurveyResultsController implements SurveyResultsSwagger {

  private static final MediaType CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

  // Texto livre é por onde dado pessoal entra sem ninguém pedir: quem consome o arquivo fora do
  // painel recebe o mesmo lembrete que a tela mostra antes de exportar.
  static final String CONTENT_WARNING_HEADER = "X-Pitaco-Content-Warning";
  static final String PERSONAL_DATA_WARNING = "may-contain-personal-data";

  private final GetSurveyResultsUseCase getSurveyResultsUseCase;
  private final ListOpenAnswersUseCase listOpenAnswersUseCase;
  private final ExportSurveyResultsUseCase exportSurveyResultsUseCase;

  SurveyResultsController(
      GetSurveyResultsUseCase getSurveyResultsUseCase,
      ListOpenAnswersUseCase listOpenAnswersUseCase,
      ExportSurveyResultsUseCase exportSurveyResultsUseCase) {
    this.getSurveyResultsUseCase = getSurveyResultsUseCase;
    this.listOpenAnswersUseCase = listOpenAnswersUseCase;
    this.exportSurveyResultsUseCase = exportSurveyResultsUseCase;
  }

  @Override
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SurveyResultsResponseDTO> results(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @ModelAttribute ResultsQueryDTO query) {
    var output = getSurveyResultsUseCase.execute(query.toInput(applicationId, surveyId));

    return ResponseEntity.ok(SurveyResultsPresenter.present(output));
  }

  @Override
  @GetMapping(value = "/open-answers", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PageResponseDTO<OpenAnswerResponseDTO>> openAnswers(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @ModelAttribute OpenAnswersQueryDTO query) {
    var output = listOpenAnswersUseCase.execute(query.toInput(applicationId, surveyId));

    return ResponseEntity.ok(OpenAnswerPresenter.present(output));
  }

  // O escopo é resolvido antes de a resposta começar — 404 ainda sai como problem+json — e as
  // linhas são escritas conforme chegam do banco, lote a lote.
  @Override
  @GetMapping(value = "/export", produces = "text/csv")
  public ResponseEntity<StreamingResponseBody> export(
      @PathVariable String applicationId,
      @PathVariable String surveyId,
      @Valid @ModelAttribute ResultsQueryDTO query) {
    var output = exportSurveyResultsUseCase.execute(query.toExportInput(applicationId, surveyId));

    var disposition =
        ContentDisposition.attachment()
            .filename("resultados-" + surveyId + ".csv", StandardCharsets.UTF_8)
            .build();

    var response =
        ResponseEntity.ok()
            .contentType(CSV)
            .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString());
    if (output.mayContainPersonalData()) {
      response.header(CONTENT_WARNING_HEADER, PERSONAL_DATA_WARNING);
    }

    return response.body(out -> CsvExportWriter.write(output, out));
  }
}
