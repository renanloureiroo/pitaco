# Quickstart — validar a coleta ponta a ponta

Guia de validação, não de implementação. O detalhe dos contratos está em
[contracts/](./contracts/) e o do esquema em [data-model.md](./data-model.md).

## Pré-requisitos

- Java 21 e Docker de pé (Testcontainers e o compose local dependem dele).
- `./mvnw verify` verde **antes** de começar — a suíte da autoria precisa continuar passando
  depois da movimentação de tipos para `core` (D-16).

## Rodar

```bash
./mvnw spring-boot:run          # sobe app, Postgres e a stack LGTM
```

API em `http://localhost:8080/api` · Swagger em `/api/swagger-ui.html`.

## Suíte

```bash
./mvnw test                                     # tudo
./mvnw test -Dtest=SamplingDecisionTest         # uma classe
./mvnw test -Dtest='FindEligibleSurveyUseCaseTest,SubmitSurveyDisplayUseCaseTest'
./mvnw verify                                   # o portão antes do PR
```

Sem Docker: `-Dtest='!*E2ETest,!ContextPathTest,!OpenApiConfigTest,!PitacoApplicationTests'`.

---

## Cenário 1 — o ciclo completo (SC-001)

Prova que a pesquisa entra pela autoria, sai pela entrega e volta como resposta guardada, sem
passo manual e sem tocar no banco.

1. `POST /api/applications` → aplicação; `POST /api/applications/{id}/api-keys` → guarde o
   `plainSecret`, ele só aparece uma vez.
2. Pela autoria: criar pesquisa, acrescentar perguntas (uma `SINGLE_CHOICE` obrigatória, uma
   `NPS` opcional), definir o disparo com `event = checkout_concluido` e `samplingRate = 1`, e
   publicar.
3. `POST /api/collect/eligibility` com `X-Pitaco-Key`, o evento e um respondente qualquer →
   **200** com as duas perguntas na ordem definida, em uma resposta só.
4. `POST /api/collect/displays` com um `displayId` UUID → **201** e header `Location`.
5. `POST /api/collect/displays/{displayId}/submission` com `outcome: COMPLETED` e as duas
   respostas → **204**.
6. Ler de volta do banco: uma linha em `respondents`, uma em `survey_displays` com
   `outcome = 'COMPLETED'` e `version_id` da versão publicada, duas em `survey_answers`.

**Esperado**: nenhum passo exigiu SQL manual, e o `version_id` da exibição aponta a versão exibida.

## Cenário 2 — o caso mais comum é o nada (SC-003)

Conte as linhas de `respondents`, `survey_displays` e `survey_answers`; consulte a elegibilidade
com um evento que nenhuma pesquisa escuta; conte de novo.

**Esperado**: `200 {"survey": null}` e as três contagens idênticas. Repita com a pesquisa
pausada, fora da janela (antes do início e depois do fim) e em rascunho — quatro vezes o mesmo
resultado, e nunca um erro.

## Cenário 3 — segmentação falhando fechado (SC-009b)

Com uma pesquisa cuja regra é `plano EQUALS premium`, consulte três vezes: com
`plano = premium` (entrega), com `plano = free` (nada) e **sem informar `plano`** (nada).
Repita o trio para `NOT_EQUALS`, `PRESENT` e `ABSENT`. Com duas regras, satisfaça uma e viole a
outra → nada.

**Esperado**: atributo ausente nunca casa com regra que o exige.

## Cenário 4 — amostragem estável (SC-005)

Com `samplingRate = 0.5`, dispare a mesma consulta cinco vezes para o mesmo respondente.

**Esperado**: as cinco respostas iguais entre si. Com `samplingRate = 0`, nunca entrega; com
`1`, sempre.

## Cenário 5 — idempotência do reenvio (SC-006)

Abra a exibição duas vezes com o mesmo `displayId` (201, depois 200) e envie o mesmo pacote de
submissão duas vezes (204, depois 204).

**Esperado**: `select count(*) from survey_displays` = 1 e uma resposta por pergunta. Um terceiro
envio com desfecho diferente → **409 `display.already_closed`**.

## Cenário 6 — recusa que lista tudo, e não grava nada (SC-007, SC-008)

Envie, na mesma submissão `COMPLETED`: a obrigatória ausente, uma opção inexistente numa escolha
única e `11` num NPS.

**Esperado**: **422 `submission.rejected`** com **três** entradas em `errors[]`, cada uma com a
sua `questionKey`; `select count(*) from survey_answers where session_id = …` = 0.

## Cenário 7 — dispensa preserva o parcial (SC-010, SC-011)

Abra a exibição, envie a submissão com `outcome: DISMISSED` e apenas a primeira pergunta
respondida.

**Esperado**: exibição `DISMISSED` com `closed_at`, a primeira resposta guardada; nova tentativa de
concluir → **409**. Uma exibição sem nenhuma resposta ainda diz sob qual versão a pesquisa foi
exibida.

## Cenário 8 — não incomodar de novo (SC-009)

1. Respondente conclui → nova consulta não entrega.
2. Respondente dispensa (outro respondente) → não entrega.
3. Exibição aberta e deixada vencer o `display-timeout` → **entrega de novo**, até
   `max-attempts`; no seguinte, para.
4. Publique uma versão **semântica** → entrega de novo. Publique uma **cosmética** → não entrega.
5. Outro respondente da mesma aplicação continua recebendo; o mesmo `reference` em outra
   aplicação continua elegível.

Para o item 3, aponte `pitaco.collect.display-timeout` para um valor curto no perfil de teste.

## Cenário 9 — isolamento entre aplicações (SC-012)

Repita as três operações com a chave de **outra** aplicação: elegibilidade → `{"survey": null}`;
abertura de exibição apontando a versão alheia → **404**; submissão para a exibição alheia → **404**.

**Esperado**: nenhuma resposta revela que a pesquisa ou a exibição existe.

## Cenário 10 — a porta recusando o que deve (US1.6, US1.14)

Sem header → **401 `api_key.missing`**. Com chave desconhecida e com chave revogada → **401
`api_key.invalid`**, resposta idêntica nos dois. Chave pública em `/api/applications/...` →
**403 `api_key.forbidden_surface`**.

## Cenário 11 — a resposta atrasada continua valendo (SC-014)

Abra a exibição, **pause** a pesquisa (ou publique uma versão nova), e só então envie a submissão.

**Esperado**: **204**, e a exibição continua apontando a versão exibida. Enquanto isso, a
elegibilidade já parou de entregar a pesquisa.

## Cenário 12 — nada pessoal no log (SC-013)

Rode a suíte capturando a saída e procure pela referência do respondente, pelo `deviceId`, por um
valor de atributo e por um texto livre enviado.

**Esperado**: nenhuma ocorrência. O log traz campo e motivo, jamais o valor.

---

## Checklist de pronto

- [ ] `./mvnw verify` verde, incluindo a suíte da autoria depois da movimentação para `core`
- [ ] Nenhum `import org.springframework` / `jakarta.*` em `core` ou em `modules/*/domain`
- [ ] Os três endpoints com interface `*Swagger` completa — um `@ApiResponse` por status, 400,
      401, 403, 404, 409 e 422 inclusive
- [ ] E2E de cada endpoint cobrindo caminho feliz (status, corpo, `Location` **e** o estado lido
      de volta do banco), 400 de validação, JSON malformado, cada erro do OpenAPI, e que o estado
      não mudou nos caminhos de falha
- [ ] Migration nova, nenhuma migration existente editada, `ddl-auto` ainda `validate`
- [ ] `application.yml` documentando `pitaco.collect.display-timeout` e `max-attempts`
- [ ] README atualizado com a superfície pública e o header `X-Pitaco-Key`
