# Crawler Distribuido - Programacao Distribuida 01

Projeto academico de crawler distribuido em Java, organizado em 4 modulos Maven:

- `data-server`: servidor TCP que entrega conteudo de paginas mockadas.
- `coordinator`: coordena workers, distribui tarefas e detecta encerramento global.
- `worker`: processa URLs, extrai links com Jsoup e reporta resultados.
- `common`: utilitarios compartilhados (protocolo, cliente do data-server, logging, etc).

## Visao geral da arquitetura

O fluxo principal de execucao e:

1. O `data-server` carrega o dataset em memoria no startup.
2. O `coordinator` carrega seeds iniciais e abre socket para registrar workers.
3. Cada `worker` se registra com `REGISTER <id> <capacity>`.
4. O coordinator envia `TASK <url>` para workers com capacidade disponivel.
5. O worker busca o conteudo no data-server, extrai links e envia `FOUND`/`IDLE`.
6. O coordinator deduplica links, reabastece a fila e encerra com `STOP` quando terminar.

Diagramas da arquitetura:

- `docs/distributed-architecture.d2`
- `docs/component-diagram.d2`

## Requisitos

- Java 21 (o projeto usa virtual threads)
- Maven 3.x
- Bash (para usar `start.sh`)

## Estrutura do repositorio

```text
.
|- common/
|- data-server/
|- coordinator/
|- worker/
|- docs/
|- start.sh
`- pom.xml
```

## Executando rapidamente (recomendado)

1. De permissao de execucao ao script (uma vez):

```bash
chmod +x start.sh
```

2. Suba tudo com defaults:

```bash
./start.sh
```

Por padrao, isso sobe:

- `data-server` na porta `9090`
- `coordinator` na porta `7070`
- `1 worker` com `capacity=1`

Para um teste mais rapido, limite o numero de seeds:

```bash
./start.sh --all --workers 3 --capacity 2 --seeds-count 20
```

Para parar todos os processos iniciados pelo script, use `Ctrl+C`.

## Opcoes do script `start.sh`

```text
--all             Start all services (default if no flags given)
--data-server     Start only the data-server
--coordinator     Start only the coordinator
--workers [N]     Start N workers (default: 1)
--capacity C      Set capacity per worker (default: 1)
--seeds-count N   Limit coordinator to read only N seeds from the file
--clean           Clean all Maven modules
--no-build        Skip Maven build steps (use existing compiled artifacts)
```

Exemplo de limpeza apenas:

```bash
./start.sh --clean
```

## Suporte a variaveis de ambiente

O projeto tem suporte a variaveis de ambiente via `start.sh`.
Quando presente, o script carrega automaticamente o arquivo `.env` na raiz
(ou outro caminho definido em `ENV_FILE`).

Variaveis suportadas:

- `WORKER_COUNT`
- `WORKER_COORDINATOR_HOST`
- `WORKER_COORDINATOR_PORT`
- `WORKER_DATA_SERVER_HOST`
- `WORKER_DATA_SERVER_PORT`
- `WORKER_CAPACITY`
- `WORKER_LOG_MODE` (`stdout`, `logger`, `disabled`)
- `COORDINATOR_SEEDS_COUNT`
- `BUILD_SKIP`

Exemplo de `.env`:

```bash
WORKER_COUNT=3
WORKER_CAPACITY=2
WORKER_LOG_MODE=logger
COORDINATOR_SEEDS_COUNT=20
BUILD_SKIP=false
```

Observacao: flags de linha de comando (como `--workers` e `--capacity`) tem
precedencia sobre os valores carregados do `.env`.

## Execucao manual por modulo

Compile tudo uma vez na raiz:

```bash
mvn clean install -DskipTests
```

Inicie os processos em terminais separados:

```bash
# Terminal 1
mvn -pl data-server exec:java -Dexec.args="--server 9090"

# Terminal 2
mvn -pl coordinator exec:java -Dexec.args="--coordinator 7070 --seeds-count 20"

# Terminal 3+
mvn -pl worker exec:java -Dexec.args="--worker-id worker-1 --capacity 2 --coordinator-host localhost --coordinator-port 7070 --data-server-host localhost --data-server-port 9090"
```

Repita o comando do worker com IDs diferentes para subir varios workers.

## Protocolo de mensagens (TCP)

Mensagens principais trocadas entre coordinator e workers:

- `REGISTER <workerId> <capacity>`
- `REGISTERED <workerId> <capacity>`
- `TASK <url>`
- `FOUND: <url1>, <url2> FROM <sourceUrl> CATEGORY=<categoria>`
- `IDLE <url>`
- `PING`
- `STOP`
- `QUIT`

## Dataset e seeds

O dataset base fica em:

- `common/src/main/java/com/example/common/sitecontent/resources/top-1m.csv`

O `SiteContentLoader` gera conteudo HTML sintetico para cada URL com links internos.
O coordinator usa seeds derivadas desse arquivo e, por default, tenta iniciar com ate `1000` seeds.

## Comparativo de Tempo de Execucao

| Cenario                                   | Tempo de execucao (s) |
|-------------------------------------------|----------------------:|
| Mesma maquina - Log desabilitado          |                39.350 |
| Mesma maquina - Log no console            |                49.530 |
| Mesma maquina - Log em arquivo e console  |                57.090 |
| Distribuido via Wi-Fi com log no console  |               254.280 |
