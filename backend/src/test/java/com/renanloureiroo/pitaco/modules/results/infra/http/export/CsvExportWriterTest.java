package com.renanloureiroo.pitaco.modules.results.infra.http.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportAnswer;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportQuestion;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportRow;
import com.renanloureiroo.pitaco.modules.results.domain.DisplayResolution;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CsvExportWriter")
class CsvExportWriterTest {

  private static final Instant OPENED = Instant.parse("2026-09-01T10:00:00Z");

  @Test
  @DisplayName("Escapa vírgula, aspas e quebra de linha, separa opções por ponto e vírgula e abre com BOM")
  void escapa_conforme_rfc_4180() {
    var text = QuestionKey.generate();
    var choice = QuestionKey.generate();
    var output =
        new ExportOutput(
            List.of("plano"),
            List.of(new ExportQuestion(text, "O que achou?"), new ExportQuestion(choice, "Canal")),
            consumer ->
                consumer.accept(
                    new ExportRow(
                        "d1",
                        "u-1",
                        1,
                        DisplayResolution.COMPLETED,
                        OPENED,
                        Optional.of(OPENED.plusSeconds(5)),
                        Optional.empty(),
                        Map.of("plano", "pro"),
                        Map.of(
                            text,
                            new ExportAnswer(Optional.of("Disse \"ok\", mas\nfaltou"), Optional.empty(), List.of()),
                            choice,
                            new ExportAnswer(Optional.empty(), Optional.empty(), List.of("app", "email"))))));

    var bytes = new ByteArrayOutputStream();
    CsvExportWriter.write(output, bytes);
    var csv = bytes.toString(StandardCharsets.UTF_8);

    assertThat(csv).startsWith(CsvExportWriter.BOM);
    var lines = csv.substring(1).split("\r\n(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)");
    assertThat(lines).hasSize(2);
    assertThat(lines[0])
        .isEqualTo(
            "displayId,respondentReference,versionNumber,outcome,openedAt,closedAt,sdkVersion,"
                + "attribute:plano,O que achou? [" + text.value() + "],Canal [" + choice.value() + "]");
    assertThat(lines[1])
        .isEqualTo(
            "d1,u-1,1,COMPLETED,2026-09-01T10:00:00Z,2026-09-01T10:00:05Z,,pro,"
                + "\"Disse \"\"ok\"\", mas\nfaltou\",app;email");
  }

  @Test
  @DisplayName("Célula sem vírgula, aspas ou quebra sai sem aspas")
  void sem_aspas_desnecessarias() {
    assertThat(CsvExportWriter.escape("simples")).isEqualTo("simples");
    assertThat(CsvExportWriter.escape("a,b")).isEqualTo("\"a,b\"");
  }
}
