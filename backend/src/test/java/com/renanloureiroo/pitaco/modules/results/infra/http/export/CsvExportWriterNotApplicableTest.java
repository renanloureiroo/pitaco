package com.renanloureiroo.pitaco.modules.results.infra.http.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.renanloureiroo.pitaco.core.catalog.QuestionKey;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportAnswer;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportQuestion;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportRow;
import com.renanloureiroo.pitaco.modules.results.domain.DisplayResolution;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CsvExportWriter — não aplicável")
class CsvExportWriterNotApplicableTest {

  @Test
  @DisplayName("Não aplicável sai como n/a; pulada sai vazia: são coisas diferentes")
  void nao_aplicavel_tem_marca_propria() {
    var skipped = QuestionKey.generate();
    var notApplicable = QuestionKey.generate();
    var output =
        new ExportOutput(
            List.of(),
            List.of(new ExportQuestion(skipped, "Pulada"), new ExportQuestion(notApplicable, "Condicionada")),
            consumer -> {});
    var row =
        new ExportRow(
            "d1",
            "u-1",
            2,
            DisplayResolution.COMPLETED,
            Instant.parse("2026-09-01T10:00:00Z"),
            Optional.empty(),
            Optional.empty(),
            Map.of(),
            Map.of(skipped, ExportAnswer.EMPTY, notApplicable, ExportAnswer.NOT_APPLICABLE));

    var cells = CsvExportWriter.cells(output, row);

    assertThat(cells.subList(CsvExportWriter.FIXED_COLUMNS.size(), cells.size()))
        .containsExactly("", CsvExportWriter.NOT_APPLICABLE_CELL);
    assertThat(CsvExportWriter.NOT_APPLICABLE_CELL).isEqualTo("n/a");
  }
}
