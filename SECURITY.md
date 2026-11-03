# Política de segurança

## Como reportar uma vulnerabilidade

Não abra issue pública para falha de segurança. Use o
[reporte privado de vulnerabilidade](https://github.com/renanloureiroo/pitaco/security/advisories/new)
do GitHub ou mande um e-mail para **renanloureiro.dev@gmail.com** com:

- a parte afetada (backend, painel ou SDK) e a versão ou o commit;
- a descrição do problema e o impacto que você enxerga;
- os passos para reproduzir ou uma prova de conceito;
- se quiser crédito, o nome ou usuário com que prefere ser citado.

Vou confirmar o recebimento, investigar e combinar com você a correção e a divulgação. Peço que
você não divulgue o problema publicamente antes da correção estar disponível.

## Versões cobertas

O Pitaco ainda não tem versões publicadas. Só a branch `main` recebe correções de segurança.

## Limitações conhecidas

A superfície administrativa da API (`/api/applications/**`), usada pelo painel, ainda não tem
autenticação de usuário. Quem for hospedar o Pitaco deve deixar essa superfície e o painel
acessíveis só por rede privada ou atrás de um proxy com autenticação, e expor publicamente apenas
a superfície de coleta (`/api/collect/**`), que é autenticada pela chave da aplicação.

A chave da aplicação vive no bundle do app e pode ser extraída; ela identifica a aplicação, não
autoriza nada além da coleta. A defesa contra abuso é o limite de requisições por chave e por
origem. O procedimento para chave comprometida está no [`ops/RUNBOOK.md`](ops/RUNBOOK.md).
