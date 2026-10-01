# Publicar o Pitaco no homelab

Como levar uma versão nova da `main` para o servidor (`ssh homelab`) e conferir que ela está no
ar. Complementa o [`RUNBOOK.md`](RUNBOOK.md), que cobre a operação do dia a dia.

## Como a publicação funciona

```
GitHub (main) ──git pull──▶ homelab: ~/apps/pitaco ──docker compose up -d --build──▶ containers
                                                                                     │
                     cloudflared (~/apps/infra) ◀── rede "edge" ──── pitaco-api:8080 / pitaco-painel:3001
```

- Não há CI de deploy: **as imagens são construídas no próprio servidor** a partir do código
  clonado. Publicar é atualizar o clone e reconstruir.
- O `compose.yaml` da raiz sobe `postgres`, `api` e `painel`. Nenhum publica porta no host; o
  único caminho de entrada é o túnel do stack `~/apps/infra`, que encontra os serviços pelos
  aliases `pitaco-api` e `pitaco-painel` na rede `edge`.
- As **migrations do banco rodam sozinhas** quando a `api` sobe (Flyway).
- O `.env` com as senhas mora só no servidor, ao lado do `compose.yaml`. Nunca vai para o Git.

> Os caminhos abaixo assumem o clone em `~/apps/pitaco`, ao lado do `~/apps/infra`. Se o seu
> estiver em outro lugar, troque o `cd`.

## Publicar uma versão nova (o caminho de sempre)

```bash
ssh homelab
cd ~/apps/pitaco

# 1. Ver o que vai entrar
git fetch origin
git log --oneline HEAD..origin/main        # commits novos
git diff --stat HEAD origin/main           # arquivos tocados (olhe backend/**/db/migration)

# 2. Backup antes de qualquer migration (ver seção "Backup manual")
mkdir -p ~/backups/pitaco
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > ~/backups/pitaco/pitaco-$(date -u +%Y%m%dT%H%M%SZ).dump

# 3. Atualizar o código
git pull --ff-only origin main

# 4. Reconstruir e subir só o que mudou
docker compose up -d --build

# 5. Conferir
docker compose ps
docker compose logs --tail 50 api
```

O `--build` reconstrói `api` e `painel`; o Docker reaproveita as camadas que não mudaram, e o
cache do Maven fica num volume de build, então a segunda vez é bem mais rápida que a primeira. O
`postgres` não é recriado e os dados ficam no volume `pitaco_pg_data`.

Se o build cair por falta de memória (o Maven da `api` e o Next do `painel` constroem em
paralelo), construa um de cada vez e só então suba:

```bash
docker compose build api
docker compose build painel
docker compose up -d
```

Para reconstruir **só um serviço** (ex.: mudou apenas o painel):

```bash
docker compose up -d --build painel
```

### Quanto tempo leva

No notebook do homelab, ordem de grandeza: a `api` (Maven) leva alguns minutos na primeira
build e menos de um depois; o `painel` compila o SDK e o Next.js, uns poucos minutos. Durante o
build os containers antigos continuam servindo; a troca acontece só no fim, com alguns segundos
de indisponibilidade de cada serviço.

## Conferir que está no ar

Os serviços não publicam porta no host, então a checagem roda de dentro da rede do compose,
com um container descartável:

```bash
# API: deve responder {"status":"UP"}
docker run --rm --network pitaco_internal curlimages/curl -fsS http://api:8080/api/actuator/health

# Painel: deve responder 200
docker run --rm --network pitaco_internal curlimages/curl -fsS -o /dev/null -w '%{http_code}\n' \
  http://painel:3001/aplicacoes
```

Depois, pelo domínio público do túnel, abra o painel e confira a **Visão geral** de uma
aplicação.

Se o **build** falhou:

- `exit code 139` (segfault) ou `137` (morto por memória) no `npm ci` ou no `mvn`: construa um
  serviço por vez, como acima. Persistindo, limpe o cache de build: `docker builder prune`.
- Erro de TypeScript no `next build`: o painel não compila; rode `npm run typecheck` em `painel/`
  na sua máquina antes de publicar.

Disco cheio de repente? Veja se não são core dumps de quedas do Postgres dentro do volume
(`core.NNNN`, ~150 MB cada). O compose desliga os core dumps (`ulimits.core: 0`), mas um volume
antigo pode ainda tê-los. São seguros de apagar (não fazem parte do banco):

```bash
sudo find /var/lib/docker/volumes/pitaco_pg_data/_data -maxdepth 1 -name 'core.*' -delete
```

Quedas repetidas de processos diferentes (Postgres, npm) apontam para hardware, em geral RAM:
`sudo journalctl -k | grep -i -E "segfault|mce|edac"`.

Se algo não subiu:

```bash
docker compose ps                       # estado e reinícios
docker compose logs --tail 200 api      # migration com erro aparece aqui, na subida
docker compose logs --tail 200 painel
```

O túnel não encontra os serviços? Confira que eles estão na rede `edge` com os aliases certos:

```bash
docker network inspect edge --format '{{range .Containers}}{{.Name}} {{end}}'
```

## Voltar para a versão anterior

Como as imagens são construídas do código, voltar é reconstruir o commit anterior:

```bash
cd ~/apps/pitaco
git log --oneline -5                     # escolha o commit bom
git checkout <commit-bom>
docker compose up -d --build
```

Para voltar a acompanhar a `main` depois: `git checkout main && git pull --ff-only`.

**Migrations não voltam sozinhas.** Se a versão nova aplicou uma migration, a versão anterior
pode não subir contra o schema novo (o Hibernate valida o schema na partida). Nesse caso,
restaure o backup feito no passo 2:

```bash
docker compose stop api painel
docker compose exec -T postgres sh -c \
  'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner' \
  < ~/backups/pitaco/pitaco-<data>.dump
docker compose start api painel
```

## Backup manual

O serviço `backup` automático (pg_dump agendado para object storage) **não está no
`compose.yaml` hoje** — saiu no commit `25fb247`. Os comandos `docker compose run --rm backup …`
do `RUNBOOK.md` só voltam a funcionar quando ele voltar. Até lá, faça o dump à mão antes de cada
publicação:

```bash
mkdir -p ~/backups/pitaco
docker compose exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc' \
  > ~/backups/pitaco/pitaco-$(date -u +%Y%m%dT%H%M%SZ).dump

# confere que o arquivo é legível
docker compose exec -T postgres pg_restore --list < ~/backups/pitaco/<arquivo>.dump | head
```

Backup que mora só no homelab não protege contra perder o homelab: copie os dumps para fora
(outra máquina, nuvem) de tempos em tempos.

## Primeira instalação (máquina nova)

```bash
ssh homelab
docker network create edge                # uma vez por máquina; o stack infra também usa
git clone https://github.com/renanloureiroo/pitaco.git ~/apps/pitaco
cd ~/apps/pitaco
cp .env.example .env                      # preencha POSTGRES_PASSWORD (openssl rand -base64 24)
docker compose up -d --build
```

No `~/apps/infra`, o cloudflared precisa ter as rotas para `http://pitaco-api:8080` e
`http://pitaco-painel:3001`.

## Popular com dados de demonstração (opcional)

A demo completa vem pronta em [`seed/`](../seed/README.md): um dump só de dados das aplicações
`demo-completo*`, carregado direto no Postgres numa transação. Leva segundos e não passa pela
API, então não pesa no servidor.

```bash
cd ~/apps/pitaco
seed/carregar-demo.sh              # primeira vez
seed/carregar-demo.sh --substituir # recarregar do zero
seed/carregar-demo.sh --datas      # renovar as datas (os gráficos de 7/30 dias voltam a encher)
```

A `api` precisa ter subido ao menos uma vez antes, para as migrations criarem o schema.

## Checklist rápido

- [ ] `git log HEAD..origin/main` revisado — há migration nova?
- [ ] Dump feito em `~/backups/pitaco/`
- [ ] `git pull --ff-only origin main`
- [ ] `docker compose up -d --build`
- [ ] `docker compose ps` sem reinício em laço
- [ ] Health da API `UP` e painel `200`
- [ ] Visão geral abre pelo domínio público
- [ ] (opcional) `seed/carregar-demo.sh` para a demo
