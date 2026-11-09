# Vê AI · API Java (Spring Boot)

Global Solution 2026/2 · FIAP 2TDSPX · **Java Advanced**

O **Vê AI** é um assistente em português para pessoas com deficiência visual. Enquanto a pessoa anda, o celular fotografa o caminho a cada poucos segundos e a IA narra por áudio **o que muda, com os perigos primeiro**, e confirma em uma frase curta quando nada mudou (modo automático). A pessoa também pode pedir uma descrição na hora ou a leitura de um texto, como placa ou embalagem (modo manual). Quando a IA identifica uma barreira estrutural (buraco, calçada obstruída, rampa irregular, falta de piso tátil, semáforo sem som), o app pergunta se a pessoa quer avisar a rede de apoio. O relato chega com GPS ao gestor da região (ONG/CRAS), que faz a triagem e o encaminha ao órgão público até a resolução.

Esta é a API consumida pelo app mobile (React Native). O mesmo contrato também é implementado em .NET.

| Integrante | RM |
|---|---:|
| Lucas Grillo Alcântara | 561413 |
| Pietro Ferreira Gomes Abrahamian | 561469 |
| Pedro Peres Benitez | 561792 |
| Lucca Ramos Mussumecci | 562027 |

| Link | |
|---|---|
| Deploy da API (Swagger) | `___` |
| Vídeo pitch | `___` |
| Contrato OpenAPI | [docs/openapi.yaml](docs/openapi.yaml) |
| Coleção Postman | [postman/](postman) (62 requisições com testes automáticos) |
| Decisões de arquitetura | [docs/decisoes-arquitetura.md](docs/decisoes-arquitetura.md) |

---

## 1. Requisitos da disciplina

| # | Critério | Onde está |
|---|---|---|
| 01 | API REST com 3+ entidades relacionadas, CRUD com JPA e paginação | 8 entidades JPA ([usuario/](src/main/java/br/com/fiap/veai/usuario), [regiao/](src/main/java/br/com/fiap/veai/regiao), [descricao/](src/main/java/br/com/fiap/veai/descricao), [relato/](src/main/java/br/com/fiap/veai/relato), [notificacao/](src/main/java/br/com/fiap/veai/notificacao)); `PaginaResposta` com `page`/`size` em descrições, relatos, fila do gestor e notificações |
| 02 | Bean Validation nos DTOs + `@RestControllerAdvice` | DTOs `record` com `@NotBlank`, `@Size`, `@DecimalMin`…; [TratadorErros](src/main/java/br/com/fiap/veai/comum/TratadorErros.java) no formato `{timestamp, status, erro, mensagem, campos[]}` |
| 03 | Spring Security + JWT com 2+ papéis | JWT HS256 nativo do Spring Security ([SegurancaConfig](src/main/java/br/com/fiap/veai/config/SegurancaConfig.java)); papéis `CIDADAO` e `GESTOR` com `@PreAuthorize`; o gestor só acessa as **suas regiões** |
| 04 | Spring AI integrado ao fluxo | [OpenAiDescritor](src/main/java/br/com/fiap/veai/ia/OpenAiDescritor.java): a foto vai como `Media` e a resposta volta como `DescricaoIA` (saída estruturada). A IA decide se o app sugere um relato (barreira com confiança ≥ 0,7): faz parte do fluxo e não é um chat solto |
| 05 | Distribuída | **Opção A: RabbitMQ** ([mensageria/](src/main/java/br/com/fiap/veai/mensageria)) |
| 06 | Swagger, README e integração com o Mobile | Swagger em `/swagger-ui.html`; contrato em [docs/openapi.yaml](docs/openapi.yaml); CORS e mensagens de erro pensadas para leitura em voz alta |

### Opção escolhida no requisito 5: **A (mensageria assíncrona com RabbitMQ)**

```
API ──(depois do commit)──▶ exchange veai.relatos (topic)
                              │ relato.criado · relato.apoiado · relato.status
                              ▼
                        fila veai.notificacoes ──▶ @RabbitListener (ack manual, 3 tentativas)
                              │ falhou de vez                       │
                              ▼                                     ▼
                  veai.relatos.dlx ──▶ veai.notificacoes.dlq    tabela NOTIFICACAO
```

| Evento | Quem é notificado | Exemplo de mensagem (lida em voz alta pelo app) |
|---|---|---|
| `relato.criado` | gestores da região | "Novo relato de calçada danificada em Pinheiros." |
| `relato.apoiado` | autor | "Mais uma pessoa confirmou seu relato de calçada danificada. Agora 3 pessoas já confirmaram." |
| `relato.status` | autor e apoiadores | "Seu relato de problema na rampa foi encaminhado ao órgão responsável. Protocolo SP156-2026-0099999." |

- O evento só é publicado **depois do commit**, para nunca notificar algo que foi desfeito.
- A fila é durável e o ack é manual. Uma mensagem que falha 3 vezes vai para a **dead-letter queue**.

---

## 2. Como rodar

A avaliação usa a API **na nuvem** (link no topo). Para testar na própria máquina, há duas opções:

### Opção A · Tudo com Docker (só precisa de Docker)

API Java, Oracle XE 21 e RabbitMQ sobem juntos, sem instalar Java nem Maven:

```bash
# Coloque na raiz do projeto o .env fornecido pela equipe (ou: cp .env.example .env)
docker compose up -d --build   # 1ª vez: baixa as imagens (~1,5 GB) e compila a API; leva alguns minutos
docker compose ps              # aguarde api, oracle e rabbitmq ficarem "healthy" (1 a 3 min)
```

- **Swagger:** http://localhost:8080/swagger-ui.html. Faça login em `POST /auth/login` com `ana.souza@veai.dev` / `Veai@2026` e use o token no botão **Authorize**.
- **Saúde:** http://localhost:8080/health
- **RabbitMQ:** http://localhost:15672 (`veai`/`veai`)
- **Oracle:** `localhost:1521`, serviço `XEPDB1`, usuário `veai`, senha `veai` (SQL Developer)
- **Parar:** `docker compose down`. Para apagar também os dados: `docker compose down -v`.

Dentro do compose, a API usa **sempre** o Oracle e o RabbitMQ dos containers, mesmo que o `.env` aponte para a FIAP. Do `.env` ela usa só a chave da OpenAI (`VEAI_IA_PROVEDOR=openai` + `OPENAI_API_KEY`), o `JWT_SECRET` e os opcionais. Sem `.env`, tudo sobe do mesmo jeito, com a IA no modo `falso` (respostas fixas, sem custo).

Se alguma porta estiver ocupada, defina no `.env`, por exemplo: `API_PORT=18080`, `ORACLE_PORT=11521`, `RABBITMQ_PORT=15673` e `RABBITMQ_PAINEL_PORT=15674`.

> A imagem do Oracle XE só existe para `linux/amd64`. Em Mac com Apple Silicon ela roda emulada, mais devagar.

### Opção B · Desenvolvimento (Java 21 + Maven)

A API roda pela IDE ou pelo Maven, e o Docker sobe só a infraestrutura. Há duas opções de banco:

| Opção | Perfil | Quando usar |
|---|---|---|
| Oracle da FIAP (`oracle.fiap.com.br`) | `fiap` | entrega e apresentação |
| Oracle XE 21 no Docker (`docker-compose.yml`) | `local` (padrão) | desenvolvimento, sem depender da rede da FIAP |

```bash
# 0. Configuração (o .env não vai para o git)
cp .env.example .env
#    edite: SPRING_PROFILES_ACTIVE, DB_USER/DB_PASSWORD (FIAP), JWT_SECRET, OPENAI_API_KEY...

# 1a. Banco local: só Oracle + RabbitMQ (sem o container da API)
docker compose up -d oracle rabbitmq

# 1b. ...ou banco da FIAP: no .env, SPRING_PROFILES_ACTIVE=fiap e as suas credenciais.
docker compose up -d rabbitmq

# 2. API: o Flyway cria (ou completa) o schema e carrega o seed de demonstração
./mvnw spring-boot:run

# 3. Swagger: http://localhost:8080/swagger-ui.html
```

Por padrão a IA roda no modo **`falso`**, com respostas fixas e sem custo. Para usar o modelo real, defina no `.env`:

```bash
VEAI_IA_PROVEDOR=openai
OPENAI_API_KEY=sk-...
```

**Testes:** `./mvnw verify`, com 57 testes. Os testes de integração sobem **Oracle XE e RabbitMQ reais** com Testcontainers, então é preciso ter Docker. No Windows com Docker no WSL, rode de dentro do WSL. A 1ª execução demora mais por causa do download da imagem do Oracle.

**Perfis:**

| Perfil | Banco | Broker | Uso |
|---|---|---|---|
| `local` | Oracle XE 21 (Docker) | RabbitMQ (Docker) | desenvolvimento |
| `fiap` | Oracle da FIAP | RabbitMQ (Docker) ou CloudAMQP | entrega de Java Advanced |
| `azure` | Azure SQL | CloudAMQP | entrega de DevOps |

Todas as variáveis estão em [.env.example](.env.example). Nenhum segredo fica no código. Variáveis de ambiente reais têm prioridade sobre o `.env`.

### Banco Oracle e o repositório `database`

As migrações ficam em [src/main/resources/db/migration/oracle](src/main/resources/db/migration/oracle):

| Migração | Conteúdo |
|---|---|
| `V1__schema.sql` | as 8 tabelas + `AUDITORIA_RELATO`, **idênticas** ao `oracle/01_ddl.sql` do repositório `database` (mesmos tipos, constraints e índices) |
| `V2__plsql.sql` | cópia de `02_funcoes`, `03_procedures`, `04_triggers` e `05_pacotes` (sem os comandos de SQL*Plus) |
| `V3__extensoes_api.sql` | o que só a API usa: `REGIAO.latitude/longitude` (centro do bairro) e a tabela `IDEMPOTENCIA` |
| `db/seed/oracle/R__seed_demo.sql` | o mesmo seed do `06_seed.sql` (mesmos IDs), com a senha `Veai@2026` válida; só insere se `USUARIO` estiver vazia |

As migrações **não apagam nada** e só criam o que falta. Por isso a API pode subir:

- num schema **vazio** (Oracle do Docker ou usuário novo na FIAP): cria tudo;
- num schema da FIAP **onde o `00_instalar.sql` do repositório `database` já rodou**: mantém tabelas e dados, recompila o PL/SQL, acrescenta as colunas e a tabela da API e troca o hash ilustrativo das senhas pelo de `Veai@2026`.

**Schema compartilhado com outro projeto:** os nomes do Vê AI (tabelas, constraints, índices, funções, procedures, gatilhos e pacotes) não coincidem com os do PetFlow (Java e .NET), então os dois podem ficar no mesmo usuário Oracle. O histórico do Flyway do Vê AI fica numa tabela própria, `veai_flyway_historico`, separada do `flyway_schema_history` padrão que o PetFlow usa. Se o schema tiver uma tabela `USUARIO` de outro projeto, a V1 para com o erro `ORA-20100`.

Os gatilhos do banco valem também para a API: o `trg_auditoria_relato` registra em `AUDITORIA_RELATO` cada mudança feita pela API, e o `trg_conta_apoio` soma o apoio em `total_apoios` (a entidade grava o mesmo valor, então nada é contado em dobro).

> Se você rodar o `00_instalar.sql` de novo no mesmo schema depois da API, ele recria as tabelas sem as colunas da V3. Antes de subir a API outra vez, apague o histórico do Flyway: `DROP TABLE veai_flyway_historico PURGE;` (nunca a `flyway_schema_history`, que pode ser do PetFlow)

---

## 3. Usuários de teste

Todos têm a senha **`Veai@2026`**. Eles vêm do seed, que é o mesmo dataset do repositório `database` (Oracle e MongoDB).

| Nome | E-mail | Papel | Observação |
|---|---|---|---|
| Ana Souza | `ana.souza@veai.dev` | CIDADAO | pergunta livre por voz ativa |
| Bruno Lima | `bruno.lima@veai.dev` | CIDADAO | |
| Carla Mendes | `carla.mendes@veai.dev` | CIDADAO | |
| Diego Rocha | `diego.rocha@veai.dev` | CIDADAO | |
| Marina Alves | `marina.alves@ong-veai.dev` | GESTOR | atende Pinheiros e Sé |
| Rafael Costa | `rafael.costa@cras-veai.dev` | GESTOR | atende Vila Mariana |

O seed também carrega 3 regiões de São Paulo, 20 descrições e 15 relatos em todos os status, para a fila do gestor não começar vazia.

---

## 4. Endpoints principais

Todas as rotas, exceto `/auth/**`, `/health` e o Swagger, exigem `Authorization: Bearer <token>`.

| Método e rota | Papel | O que faz |
|---|---|---|
| `POST /auth/registrar` | público | cria uma conta de cidadão |
| `POST /auth/login` | público | devolve o JWT (8 h) e o papel |
| `GET /usuarios/me` · `PUT /usuarios/me/preferencias` | logado | perfil, velocidade da voz e pergunta livre |
| `POST /descricoes` | CIDADAO | multipart `imagem` (JPEG/PNG/WebP, até 5 MB) + `modo` + `anteriorId` (opcional, modo automático). Devolve perigos, `descricaoCompleta` (o texto falado), `novidade` e `sugestaoRelato`. Limite de 20 por minuto (**429** + `Retry-After`) |
| `GET /descricoes` · `GET /descricoes/{id}` · `PATCH /descricoes/{id}/avaliacao` · `DELETE /descricoes/{id}` | CIDADAO | histórico paginado, avaliação "foi útil?" e exclusão |
| `POST /descricoes/{id}/pergunta` | CIDADAO | pergunta livre por voz sobre a foto (nada é gravado) |
| `POST /relatos` | CIDADAO | **regra dos 30 m**: `201 CRIADO`, ou `200 APOIO_REGISTRADO` / `JA_REGISTRADO`. Aceita o header `Idempotency-Key` |
| `GET /relatos/meus` · `GET /relatos/{id}` · `PUT /relatos/{id}` · `DELETE /relatos/{id}` | CIDADAO | acompanhar; editar e excluir só enquanto `ABERTO` (exclusão bloqueada com 409 se houver apoios) |
| `GET /gestor/relatos?status&regiaoId&page&size` | GESTOR | fila das **suas** regiões, por prioridade |
| `GET /gestor/relatos/{id}` · `PATCH /gestor/relatos/{id}/status` · `GET /gestor/relatos/{id}/historico` | GESTOR | detalhe, mudança de status e histórico |
| `GET /notificacoes` · `PATCH /notificacoes/{id}/lida` | logado | notificações geradas pelo consumidor RabbitMQ |
| `GET /regioes` | logado | regiões atendidas |
| `GET /health` | público | status geral; com login, mostra também `db`, `rabbit` e `openai` |

### Regras de negócio

- **Prioridade:** peso da gravidade (BAIXA 1, MÉDIA 3, ALTA 5) × (1 + apoios) + dias em aberto. Vale 0 para relatos finalizados. É recalculada a cada mudança e todo dia às 3h. É a mesma fórmula da `fn_prioridade_relato` do Oracle.
- **Status:** `ABERTO → EM_TRIAGEM → ENCAMINHADO → RESOLVIDO`, sem pular etapa e sem voltar. `DESCARTADO` pode vir de qualquer status não final e exige motivo. `ENCAMINHADO` exige o protocolo do órgão. Transição inválida responde **422**.
- **Deduplicação:** um relato **não finalizado**, do **mesmo tipo** e a **até 30 m** (Haversine) recebe +1 apoio em vez de virar um relato novo.
- **Modo automático:** o app fotografa a cada poucos segundos e manda em `anteriorId` o id da resposta anterior. A IA compara a cena nova com a anterior: quando algo muda, a `descricaoCompleta` traz só a mudança, começando pelos perigos; quando nada muda, ela é uma confirmação curta ("O poste e o carro continuam no mesmo lugar, à sua frente."). O app fala as duas, e o campo `novidade` diz qual é o caso. Um perigo novo sempre é novidade. A mesma sugestão de relato não se repete por 2 minutos, e uma resposta anterior com mais de 2 minutos é ignorada (a cena é descrita inteira de novo). Sem `anteriorId` (modo manual), a descrição é sempre completa.
- **Latência:** a foto é reduzida para 1024 px antes de ir para a OpenAI e o modelo responde sem raciocínio estendido (`OPENAI_REASONING_EFFORT=none`): cerca de 3 s por foto, contra 18 s com a foto original e o padrão do modelo, e com cerca de 5 vezes menos tokens.
- **Privacidade:** a foto nunca é gravada (nem em banco, nem em disco, nem em log) e o GPS só é guardado no relato.

### Coleção Postman

A pasta [postman/](postman) tem a coleção `ve-ai.postman_collection.json`, com **62 requisições e 139 testes automáticos**, os ambientes `local` e `nuvem` e as duas fotos usadas nas descrições.

| Pasta | O que cobre |
|---|---|
| 00 · Saúde | `/health` público |
| 01 · Autenticação | login dos 4 usuários do seed, cadastro de cidadão, e-mail repetido (409), validação (400), senha errada e rota sem token (401) |
| 02 · Usuário e regiões | perfil, regiões da gestora, preferências de voz (e limite inválido) |
| 03 · Descrições (IA) | foto da calçada (AMBIENTE), foto seguinte no modo automático (`anteriorId`, `novidade`, sugestão sem repetir), placa (TEXTO), histórico paginado, avaliação, pergunta livre, exclusão, 400/403/404 |
| 04 · Relatos | regra dos 30 m (CRIADO, APOIO_REGISTRADO, JA_REGISTRADO), `Idempotency-Key` (repetição e conflito), edição, exclusão (409 com apoios) |
| 05 · Gestor | fila por prioridade e filtros, gestor de outra região (403), máquina de estados com protocolo, transições inválidas (422) e histórico |
| 06 · Notificações | avisos gerados pelo consumidor RabbitMQ para autor, apoiador e gestora; marcar como lida |

**No Postman:** importe a coleção e um dos ambientes e rode a coleção inteira, na ordem (*Run collection*). Cada pasta usa os tokens e os relatos criados nas anteriores. Se o Postman não achar as fotos, selecione os arquivos da pasta `postman/` no campo `imagem` das requisições de descrição, ou configure essa pasta como *Working Directory* em *Settings → General*.

**Pela linha de comando** (com Node.js):

```bash
npx newman run postman/ve-ai.postman_collection.json -e postman/ve-ai-local.postman_environment.json --working-dir postman
```

A coleção pode ser executada várias vezes seguidas: cada execução cria um cidadão novo e relatos em pontos aleatórios. Ela funciona com a IA real e no modo `falso`.

### Exemplo rápido (curl)

```bash
TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"diego.rocha@veai.dev","senha":"Veai@2026"}' | jq -r .token)

# Diego, a ~10 m do buraco do relato #1 (Rua dos Pinheiros), vira +1 apoio
curl -s -X POST localhost:8080/relatos -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -H 'Idempotency-Key: 7f1c-demo' \
  -d '{"tipo":"CALCADA_DANIFICADA","gravidade":"ALTA","texto":"Buraco enorme","latitude":-23.56620,"longitude":-46.68436}'
# → 200 {"resultado":"APOIO_REGISTRADO","apoioRegistrado":true,"relato":{"id":1,"totalApoios":3,...}}
```

---

## 5. Arquitetura

```
App (React Native) ──HTTPS + JWT──▶ API Spring Boot ──JPA/Flyway──▶ Oracle (FIAP) | Azure SQL
                                      │   │
                                      │   └──Spring AI──▶ OpenAI (gpt-6-luna, saída estruturada)
                                      │   └──RestClient (opcional, 3 s)──▶ Serviço RAG NBR 9050 (Python)
                                      └──AMQP──▶ RabbitMQ (CloudAMQP) ──▶ consumidor na própria API
```

- **Código por funcionalidade:** `auth`, `usuario`, `regiao`, `descricao`, `relato`, `notificacao`, `ia`, `mensageria`, `config` e `comum`.
- **DTOs:** todos são `record`. Os controllers nunca expõem entidades.
- **Schema:** versionado com **Flyway**, com uma pasta por banco (`db/migration/oracle` e `db/migration/sqlserver`). No Oracle, o schema segue o DDL do repositório `database` (`NUMBER`, `DATE`, `CLOB`), então o Hibernate não valida tipo a tipo (`ddl-auto=none`) e quem garante o schema são o Flyway e os testes de integração contra Oracle real. No SQL Server, o Hibernate valida (`ddl-auto=validate`).
- **Descritor de imagem:** a interface `DescritorImagem` tem duas implementações, `openai` e `falso`. A troca é feita por `VEAI_IA_PROVEDOR`, sem mudar código.
- **RAG opcional:** depois que o relato é criado, a API chama o serviço do Pedro de forma assíncrona. Se o serviço não existir ou falhar, o relato segue sem `normaReferencia`.

As decisões e os motivos de cada uma estão em [docs/decisoes-arquitetura.md](docs/decisoes-arquitetura.md).

---

## 6. Declaração de uso de IA generativa

| Ferramenta | Para quê |
|---|---|
| Claude Code (Anthropic) | Apoio na escrita do código, dos testes, das migrações SQL e da documentação, a partir das decisões de arquitetura tomadas pelo grupo (registradas em [docs/decisoes-arquitetura.md](docs/decisoes-arquitetura.md)). Todo o código foi revisado, executado e testado pelo grupo. |
| OpenAI GPT-6 Luna (via Spring AI) | Funcionalidade do produto: descrição do ambiente a partir da foto, com saída estruturada. |
