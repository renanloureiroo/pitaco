package com.renanloureiroo.pitaco.infra.http.dtos;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Uma página de resultados, com o total do conjunto que atende ao filtro")
public record PageResponseDTO<T>(
    @Schema(
            description = "Itens desta página, vazio quando nada atende ao filtro",
            requiredMode = Schema.RequiredMode.REQUIRED)
        List<T> items,
    @Schema(
            description = "Página devolvida, base 0",
            example = "0",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int page,
    @Schema(
            description = "Tamanho de página aplicado",
            example = "20",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int size,
    @Schema(
            description = "Total de itens que atendem ao filtro, não apenas os desta página",
            format = "int64",
            example = "42",
            requiredMode = Schema.RequiredMode.REQUIRED)
        long total,
    @Schema(
            description = "Quantidade de páginas; 0 quando não há nenhum item",
            example = "3",
            requiredMode = Schema.RequiredMode.REQUIRED)
        int totalPages) {}
