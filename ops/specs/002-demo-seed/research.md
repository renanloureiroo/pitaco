# Research: Demo completa com dados fictícios

**Feature**: `002-demo-seed` · **Date**: 2026-10-01 · **Spec**: [spec.md](./spec.md)

Cada decisão abaixo resolveu uma incógnita antes de escrever os arquivos. A principal — dump
pronto em vez de gerador — veio depois de usar a alternativa: a primeira versão da demo era um
gerador que passava pela API, e foi substituída por esta.

---

## D-01 — Dump SQL pronto em vez de gerador pela API (FR-001, FR-008, SC-001)

**Decisão**: a demo é um `pg_dump --data-only` das aplicações `demo-completo*`, versionado
comprimido em `seed/demo-completo.sql.gz`, e carregado direto no banco.

**Rationale**: o gerador anterior montava aplicações, chaves, pesquisas e versões pela API
administrativa e simulava milhares de respondentes pelas rotas reais do SDK — elegibilidade,
abertura, eventos, envio, supressões, erros. Era fiel, e tinha quatro custos:

- **Tempo**: minutos na escala completa num backend local, mais num servidor modesto.
- **Dependência da API no ar**, com o servidor pagando a carga de milhares de requisições.
- **Limite de requisições**: fora da máquina local, o limite de 120 por minuto por origem valia e
  o gerador ficava muito mais lento; mesmo dentro, precisou de novas tentativas para erros
  transitórios `5xx` da coleta.
- **Datas**: a API grava a hora do servidor, então o gerador ainda precisava de um SQL aplicado
  direto no banco para espalhar as exibições pelos 90 dias — ou seja, já não era só API.

O dump preserva o que o gerador tinha de bom — o dado foi produzido por ele, pelas rotas reais, com
todas as validações do backend — e elimina os quatro custos: a carga é um `COPY` que leva
segundos, sem API, sem limite, sem custo para o servidor.

**Alternativas consideradas**: manter o gerador e paralelizar mais — rejeitada porque o limite por
origem é proteção da superfície pública e não deve ser contornado por um seed; gerador escrevendo
direto no banco — rejeitada por duplicar, num script, regras que o domínio já valida.

---

## D-02 — Só dados, só das aplicações da demo (FR-001, FR-012)

**Decisão**: o dump não traz `CREATE` de nada nem linha de aplicação fora do prefixo
`demo-completo`. O schema vem das migrations que a API aplica na subida.

**Rationale**: um dump com schema brigaria com o Flyway — ou recriaria tabelas, ou falharia por
elas existirem — e congelaria uma versão de schema dentro do repositório, ao lado das migrations
que são a fonte da verdade. Só dados mantém um único dono do schema. Limitar ao prefixo deixa a
demo conviver com aplicações reais e com o seed do app de exemplo no mesmo banco.

**Alternativas consideradas**: dump do banco inteiro — rejeitada por carregar `flyway_schema_history`
e quaisquer outros dados do ambiente de geração; banco separado só para a demo — rejeitada porque
o painel lê um banco só.

---

## D-03 — Pelo container do banco, sem porta publicada (FR-008)

**Decisão**: o script executa `psql` por `docker compose exec -T postgres`, lendo usuário e banco
das variáveis do próprio container (`POSTGRES_USER`, `POSTGRES_DB`), com `ON_ERROR_STOP=1`, `-q` e
`-X`. O conteúdo vai por stdin.

**Rationale**: a stack de produção não publica a porta do banco; o container é o único caminho que
funciona igual em produção e na stack local, sem copiar senha para o host. `-X` ignora um `.psqlrc`
que mudaria o comportamento; `ON_ERROR_STOP` transforma o primeiro erro em falha da execução — e,
dentro de `begin`/`commit`, em rollback.

**Alternativas consideradas**: `psql` no host com a URL do banco — rejeitada por exigir porta
publicada e cliente instalado; copiar o arquivo para dentro do container — rejeitada por deixar
sobra no container sem ganho.

---

## D-04 — Uma transação; chaves estrangeiras suspensas só durante a cópia (FR-011, FR-013)

**Decisão**: a carga envia, num único `psql`: `begin`; a limpeza da demo (sempre emitida — com a
demo ausente, não apaga nada); `set local session_replication_role = replica`; o dump
descomprimido; `set local session_replication_role = origin`; `set search_path = public`;
`seed/datas.sql`; `commit`.

**Rationale**: transação única é o que torna a carga tudo-ou-nada (SC-004) e a substituição
atômica — não existe instante com a demo pela metade. A suspensão das chaves estrangeiras é
necessária porque uma pergunta condicional referencia outra pergunta da mesma tabela, e com as
restrições ativas a ordem das linhas dentro do `COPY` importaria. `set local` limita a suspensão à
transação, e ela é desfeita antes do ajuste de datas. A limpeza vem antes da suspensão, então roda
com as restrições ativas e com as remoções em cascata do schema. O `search_path` é refeito porque o
dump o esvazia.

**Alternativas consideradas**: ordenar as linhas do dump por dependência — rejeitada por ser
frágil a cada regeneração; `deferrable` nas constraints — rejeitada por exigir migration para
servir a um seed.

---

## D-05 — Datas relativas, com a exibição mais recente a uma hora de agora (FR-016, FR-017)

**Decisão**: `seed/datas.sql` calcula `delta = (now() − 1 hora) − max(opened_at)` sobre as
exibições da demo e soma `delta` a toda coluna de instante de toda tabela da demo — inclusive
janelas de disparo, instantes de revogação e de último uso de chave, e instantes de recebimento de
eventos e erros.

**Rationale**: os gráficos do painel olham para "últimos 7 dias" e "últimos 30 dias"; uma demo de
datas fixas fica vazia nesses gráficos em poucas semanas. Um deslocamento único preserva toda
distância entre instantes — duração das exibições, ordem dos eventos, sazonalidade semanal,
crescimento ao longo do período —, que é o que a análise do painel mostra. Uma hora, e não zero,
para nenhuma exibição parecer em andamento e as exibições sem desfecho já aparecerem como
abandonadas. Somar um intervalo a um valor nulo dá nulo, então revogação ausente continua ausente.

**Alternativas consideradas**: regenerar o dump periodicamente — rejeitada por exigir o gerador de
volta; datas absolutas no futuro — rejeitada por produzir exibições "do futuro" nas telas.

---

## D-06 — Renovação como modo próprio, idempotente (FR-018, FR-019)

**Decisão**: `carregar-demo.sh --datas` aplica só `seed/datas.sql`, numa transação, sobre a demo
existente; sem demo, recusa.

**Rationale**: renovar as datas não deve custar recarregar 80 mil linhas nem desfazer o que quem
demonstra mudou no painel. Como o deslocamento é sempre calculado a partir da exibição mais
recente, rodar de novo logo em seguida dá deslocamento praticamente nulo — o mesmo estado. Os
mesmos comandos rodam no fim de toda carga, então não há dois caminhos para a mesma regra.

**Alternativas consideradas**: deslocar por um valor fixo informado — rejeitada porque rodar duas
vezes deslocaria duas vezes.

---

## D-07 — Recusar demo duplicada; substituir só com pedido explícito (FR-010, FR-013)

**Decisão**: sem argumento, se existe alguma aplicação com slug `demo-completo%`, a carga recusa e
indica `--substituir`. Com `--substituir`, a limpeza apaga a demo e a carga segue.

**Rationale**: o dump tem identificadores fixos; carregar por cima de uma demo existente
violaria chaves primárias no meio da transação, com uma mensagem pouco útil. A recusa antecipada
diz o que fazer. Substituir apaga o que quem demonstra pode ter alterado pelo painel; por isso é
um pedido, não o padrão.

**Alternativas consideradas**: substituir sempre — rejeitada por apagar mudanças sem aviso;
`on conflict do nothing` — impossível com `COPY` e deixaria uma demo meio antiga, meio nova.

---

## D-08 — Uso diário do SDK deslocado em dias inteiros (FR-020)

**Decisão**: `sdk_version_daily_usage`, cuja chave inclui o dia (`date`), é copiada para uma
tabela temporária com `day + dias(delta)`, apagada e reinserida.

**Rationale**: somar um intervalo a uma data num `update` pode colidir com a chave de outra linha
no meio da atualização; trocar o bloco inteiro evita a colisão. A série por dia só tem sentido em
dias inteiros, e a parte fracionária do deslocamento é descartada — a diferença é de horas numa
série diária.

**Alternativas consideradas**: `update` direto — rejeitada pelo risco de colisão de chave;
arredondar o deslocamento global para dias inteiros — rejeitada porque perderia o "uma hora antes
de agora" das exibições.
