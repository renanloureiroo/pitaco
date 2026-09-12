package com.renanloureiroo.pitaco.modules.results.infra.http.export;

import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportAnswer;
import com.renanloureiroo.pitaco.modules.results.application.outputs.ExportOutput.ExportRow;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// RFC 4180 com BOM: vírgula, aspas e quebra de linha dentro do texto são escapadas, e a
// planilha abre o arquivo com acento certo sem que ninguém escolha codificação.
public final class CsvExportWriter {

  static final String BOM = "﻿";
  static final String LINE_END = "\r\n";
  static final String OPTION_SEPARATOR = ";";
  static final String NOT_APPLICABLE_CELL = "n/a";

  static final List<String> FIXED_COLUMNS =
      List.of(
          "displayId",
          "respondentReference",
          "versionNumber",
          "outcome",
          "openedAt",
          "closedAt",
          "sdkVersion");

  private CsvExportWriter() {}

  public static void write(ExportOutput output, OutputStream out) {
    var writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
    try {
      writer.write(BOM);
      writer.write(line(header(output)));
      output.rows().forEach(row -> writeRow(writer, output, row));
      writer.flush();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  static List<String> header(ExportOutput output) {
    var columns = new ArrayList<>(FIXED_COLUMNS);
    output.attributeNames().forEach(name -> columns.add("attribute:" + name));
    output
        .questions()
        .forEach(question -> columns.add(question.statement() + " [" + question.key().value() + "]"));
    return columns;
  }

  private static void writeRow(Writer writer, ExportOutput output, ExportRow row) {
    try {
      writer.write(line(cells(output, row)));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  static List<String> cells(ExportOutput output, ExportRow row) {
    var cells = new ArrayList<String>();
    cells.add(row.displayId());
    cells.add(row.respondentReference());
    cells.add(String.valueOf(row.versionNumber()));
    cells.add(row.outcome().name());
    cells.add(row.openedAt().toString());
    cells.add(row.closedAt().map(Object::toString).orElse(""));
    cells.add(row.sdkVersion().orElse(""));
    output.attributeNames().forEach(name -> cells.add(row.attributes().getOrDefault(name, "")));
    output
        .questions()
        .forEach(
            question ->
                cells.add(
                    Optional.ofNullable(row.answers().get(question.key()))
                        .map(CsvExportWriter::cell)
                        .orElse("")));
    return cells;
  }

  private static String cell(ExportAnswer answer) {
    if (answer.notApplicable()) {
      return NOT_APPLICABLE_CELL;
    }
    if (answer.text().isPresent()) {
      return answer.text().get();
    }
    if (answer.number().isPresent()) {
      return String.valueOf(answer.number().get());
    }
    return String.join(OPTION_SEPARATOR, answer.options());
  }

  static String line(List<String> cells) {
    var builder = new StringBuilder();
    for (var index = 0; index < cells.size(); index++) {
      if (index > 0) {
        builder.append(',');
      }
      builder.append(escape(cells.get(index)));
    }
    return builder.append(LINE_END).toString();
  }

  static String escape(String value) {
    var needsQuotes =
        value.indexOf(',') >= 0
            || value.indexOf('"') >= 0
            || value.indexOf('\n') >= 0
            || value.indexOf('\r') >= 0;
    if (!needsQuotes) {
      return value;
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }
}
