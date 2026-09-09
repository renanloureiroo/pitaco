# Quickstart — validar `006-collection-read-list`

Como provar que a fatia funciona de ponta a ponta. Não traz código de implementação: traz o que
rodar e o que esperar. Detalhe de corpo e de erro está em
[`contracts/collection-read.md`](./contracts/collection-read.md); semântica de porta, em
[`contracts/module-ports.md`](./contracts/module-ports.md).

## Pré-requisitos

- Java 21 e o wrapper Maven do repositório
- Docker de pé (Testcontainers sobe Postgres e a stack LGTM nos testes E2E)

---

## 1. Suíte completa — o portão

```bash
./mvnw verify
```

Verde, sem teste ignorado. É o portão da constituição antes de qualquer PR.

Sem Docker, dá para rodar tudo menos o que sobe contexto:

```bash
./mvnw test -Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'
```

## 2. Só esta fatia

```bash
# casos de uso, sobre os fakes
./mvnw test -Dtest='ListSurveyDisplaysUseCaseTest,GetSurveyDisplayUseCaseTest,ListRespondentsUseCaseTest,ListRespondentDisplaysUseCaseTest'

# E2E das quatro leituras
./mvnw test -Dtest='ListSurveyDisplaysE2ETest,GetSurveyDisplayE2ETest,ListRespondentsE2ETest,ListRespondentDisplaysE2ETest'
```

## 3. A migration aplicou

```bash
./mvnw spring-boot:run
```

Sobe sem erro de validação de schema (`ddl-auto: validate`). Confirmar os índices:

```sql
select indexname from pg_indexes
 where tablename in ('survey_displays', 'respondents')
 order by indexname;
```

Esperado: `idx_survey_displays_survey_listing`, `idx_survey_displays_respondent_listing`,
`idx_respondents_application_listing`, `idx_survey_displays_history` — e **não**
`idx_survey_displays_survey`, derrubado pela migration por ser prefixo redundante.

---

## 4. Roteiro manual de ponta a ponta

API em `http://localhost:8080/api` · Swagger em `/api/swagger-ui.html`.

O roteiro precisa de dados coletados, então começa pela coleta — que já existe. Do começo:

1. **Criar aplicação** e **emitir chave** (`POST /applications`, `POST /applications/{id}/api-keys`).
   Guardar o identificador da aplicação e o segredo da chave.
2. **Criar e publicar uma pesquisa** com pelo menos quatro perguntas, incluindo **uma de texto
   livre** e uma não obrigatória — é o que permite exercitar `EXPIRED` e `SKIPPED`.
3. **Coletar três exibições** pela superfície pública (`X-Pitaco-Key`), com respondentes
   diferentes: uma respondida por completo, uma dispensada, uma deixada aberta.
4. **Trocar de superfície**: daqui em diante, **sem** o header `X-Pitaco-Key`.

Então validar, na ordem:

| # | Chamada | Esperado |
| --- | --- | --- |
| 1 | `GET /applications/{app}/surveys/{survey}/displays` | as três exibições, `openedAt` desc; `closedAt` presente nas duas fechadas e `null` na aberta |
| 2 | a mesma com `?outcome=DISMISSED` | só a dispensada |
| 3 | a mesma com `?outcome=ABANDONED` | `400`, `code` de validação — o valor não é aceito |
| 4 | a mesma com `?openedFrom=<depois de tudo>` | `200`, `items: []`, `total: 0` |
| 5 | a mesma com `?openedFrom=<fim>&openedTo=<início>` | `400` apontando o campo |
| 6 | a mesma com `?size=101` | `400` apontando o campo |
| 7 | a mesma com `?page=99` | `200`, `items: []`, `total` correto |
| 8 | a mesma com um `surveyId` inexistente | `404 survey.not_found` |
| 9 | a mesma com o `surveyId` de outra aplicação | `404 survey.not_found` — não `403` |
| 10 | `GET /applications/{app}/displays/{id da respondida}` | atributos, `sdkVersion`, e as respostas na ordem das perguntas da versão |
| 11 | a mesma, na exibição aberta | `200`, `answers: []`, `closedAt: null` |
| 12 | a mesma, na dispensada | desfecho `DISMISSED`, `answers: []` |
| 13 | a mesma com id inexistente | `404 display.not_found` |
| 14 | `GET /applications/{app}/respondents` | os três respondentes, `lastSeenAt` desc, com `identityKind` distinguível |
| 15 | `GET /applications/{app}/respondents/{id}/displays` | as exibições daquele respondente, cada uma com `surveyId` |
| 16 | a mesma com respondente inexistente | `404 respondent.not_found` |
| 17 | qualquer uma das quatro **com** `X-Pitaco-Key` | `403 api_key.forbidden_surface` |

### O cenário de expiração (FR-028)

Não dá para esperar o prazo passar. Duas formas de provar:

- **No E2E**, que é onde precisa estar coberto: gravar a exibição com `answeredAt` recuado além do
  prazo configurado na aplicação, e conferir que a resposta de texto livre volta com
  `status: "EXPIRED"` e sem `text`, enquanto a de escolha e a numérica da **mesma** exibição
  voltam `ANSWERED` com valor. É o par que prova que a supressão atinge só texto livre.
- **Manualmente**: criar a aplicação com `openTextRetentionDays: 1`, coletar, e recuar
  `answered_at` da resposta de texto livre em dois dias direto no banco.

Conferir também que `SKIPPED` e `EXPIRED` são visivelmente diferentes na mesma resposta — é a
distinção que a FR-028 exige.

---

## 5. O que precisa estar verde para a fatia estar pronta

- [ ] `./mvnw verify` verde
- [ ] As quatro leituras com E2E cobrindo: caminho feliz **com o estado lido de volta do banco**,
      `400` de validação, cada erro anunciado no OpenAPI, e `403` da chave de SDK
- [ ] E2E provando que **nenhum** caminho — feliz ou de recusa — alterou exibição, resposta ou
      respondente (FR-027, SC-005)
- [ ] Teste de caso de uso para cada uma das três situações de resposta: `ANSWERED`, `SKIPPED`,
      `EXPIRED`
- [ ] Teste de caso de uso provando a ordem das respostas contra a ordem da versão exibida, e o
      ramo da chave que não existe na versão
- [ ] Teste de constraint de cada DTO de consulta, incluindo o período invertido
- [ ] Swagger com todo status declarado nas quatro rotas, `400`, `403` e `404` inclusive
- [ ] Nenhuma importação de framework em `domain`; nenhum `@Service` em caso de uso
- [ ] Nenhum log com `identityValue`, conteúdo de resposta ou valor de atributo
- [ ] `README` atualizado se alguma decisão arquitetural mudou — nesta fatia, não deve ter mudado
