# Quickstart — validar `012-survey-results`

Contrato em [`contracts/results.md`](./contracts/results.md).

## 1. Portão

```bash
./mvnw verify
```

## 2. Só esta fatia

```bash
# domínio, DTO e CSV (sem Docker)
./mvnw test -Dtest='QuestionAggregationTest,ResponseRateTest,QuestionComparabilityTest,ResultsQueryDTOTest,CsvExportWriterTest,CsvExportWriterNotApplicableTest'

# casos de uso
./mvnw test -Dtest='GetSurveyResultsUseCaseTest,GetSurveyResultsAdvancedAuthoringTest,GetSurveyResultsRetentionTest,ListOpenAnswersUseCaseTest,ExportSurveyResultsUseCaseTest'

# E2E
./mvnw test -Dtest='SurveyResultsE2ETest,OpenAnswersE2ETest,ExportResultsE2ETest,ExportPersonalDataWarningE2ETest,AdvancedAuthoringResultsE2ETest,ResultsOpenApiTest'
```

## 3. Roteiro manual

Com a demo carregada (`seed/carregar-demo.sh`) ou com uma pesquisa publicada e exibições coletadas:

| # | Chamada | Esperado |
| --- | --- | --- |
| 1 | `GET …/results` | cinco contagens, taxa com `definition`, `timeline`, perguntas com agregado na forma do tipo |
| 2 | `…?attribute=plano&attributeValue=pro` | todos os números recortados; `filter.attributeAbsent: false` |
| 3 | `…?attribute=plano` | só quem não enviou `plano`; `filter.attributeAbsent: true` |
| 4 | `…?attributeValue=pro` | `400` apontando o campo |
| 5 | `…?version=1` | perguntas da v1, sem `comparability` |
| 6 | sem `version`, com uma opção trocada entre v1 e v2 exibidas | só aquela pergunta com `comparable: false` |
| 7 | `GET …/results/open-answers?q=entrega` | só textos com "entrega", cada um com `context` |
| 8 | `GET …/results/export` | CSV com BOM, uma linha por exibição, `X-Pitaco-Content-Warning` se houver texto livre |
| 9 | `GET …/results/export?version=0` | `400` em `problem+json`, sem corpo CSV |
| 10 | qualquer uma com `X-Pitaco-Key` | `403 api_key.forbidden_surface` |

## 4. O que precisa estar verde

- [X] Cada forma de agregado conferida contra o semeado
- [X] Ausência de agregado distinta de zero
- [X] Recorte idêntico em resultados, respostas abertas e export
- [X] Comparabilidade por pergunta só no consolidado
- [X] Retenção somada só no recorte de versão
- [X] Export íntegro com vírgula, aspas e quebra de linha, em lotes
- [X] Nenhuma leitura altera o banco
