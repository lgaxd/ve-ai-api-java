# Vê AI · API Java (Spring Boot)

Global Solution 2026/2 · FIAP 2TDSPX · **Java Advanced**

O **Vê AI** é um assistente em português para pessoas com deficiência visual. A pessoa tira uma foto e a IA descreve o ambiente por áudio, **falando os perigos primeiro**. Quando a IA identifica uma barreira estrutural (buraco, calçada obstruída, rampa irregular, falta de piso tátil, semáforo sem som), o app pergunta se a pessoa quer avisar a rede de apoio. O relato chega com GPS ao gestor da região (ONG/CRAS), que faz a triagem e o encaminha ao órgão público até a resolução.

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

**Pré-requisitos:** Java 21, Maven 3.9+ (ou o `./mvnw` incluído) e Docker com o plugin Compose.

```bash
# 1. PostgreSQL 17 + RabbitMQ 4 (painel em http://localhost:15672, veai/veai)
docker compose up -d

# 2. API no perfil "local" (padrão): cria o schema com Flyway e carrega o seed de demonstração
./mvnw spring-boot:run

# 3. Abra o Swagger
#    http://localhost:8080/swagger-ui.html
```

Por padrão a IA roda no modo **`falso`**, com respostas fixas e sem custo. Para usar o modelo real:

```bash
export VEAI_IA_PROVEDOR=openai
export OPENAI_API_KEY=sk-...
./mvnw spring-boot:run
```

**Testes:** `./mvnw verify`, com 57 testes. Os testes de integração sobem **PostgreSQL e RabbitMQ reais** com Testcontainers, então é preciso ter Docker. No Windows com Docker no WSL, rode de dentro do WSL.

**Perfis:**

| Perfil | Banco | Broker | Uso |
|---|---|---|---|
| `local` | PostgreSQL (Docker) | RabbitMQ (Docker) | desenvolvimento |
| `java` | Azure Database for PostgreSQL | CloudAMQP | entrega de Java Advanced |
| `azure` | Azure SQL | CloudAMQP | entrega de DevOps |

Todas as variáveis estão em [.env.example](.env.example). Nenhum segredo fica no código.

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
| `POST /descricoes` | CIDADAO | multipart `imagem` (JPEG/PNG/WebP, até 5 MB) + `modo`. Devolve perigos, resumo e `sugestaoRelato`. Limite de 20 por minuto (**429** + `Retry-After`) |
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
- **Privacidade:** a foto nunca é gravada (nem em banco, nem em disco, nem em log) e o GPS só é guardado no relato.

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
App (React Native) ──HTTPS + JWT──▶ API Spring Boot ──JPA/Flyway──▶ PostgreSQL | Azure SQL
                                      │   │
                                      │   └──Spring AI──▶ OpenAI (gpt-6-luna, saída estruturada)
                                      │   └──RestClient (opcional, 3 s)──▶ Serviço RAG NBR 9050 (Python)
                                      └──AMQP──▶ RabbitMQ (CloudAMQP) ──▶ consumidor na própria API
```

- **Código por funcionalidade:** `auth`, `usuario`, `regiao`, `descricao`, `relato`, `notificacao`, `ia`, `mensageria`, `config` e `comum`.
- **DTOs:** todos são `record`. Os controllers nunca expõem entidades.
- **Schema:** versionado com **Flyway**, com uma pasta por banco (`db/migration/postgresql` e `db/migration/sqlserver`). O Hibernate só valida (`ddl-auto=validate`).
- **Descritor de imagem:** a interface `DescritorImagem` tem duas implementações, `openai` e `falso`. A troca é feita por `VEAI_IA_PROVEDOR`, sem mudar código.
- **RAG opcional:** depois que o relato é criado, a API chama o serviço do Pedro de forma assíncrona. Se o serviço não existir ou falhar, o relato segue sem `normaReferencia`.

As decisões e os motivos de cada uma estão em [docs/decisoes-arquitetura.md](docs/decisoes-arquitetura.md).

---

## 6. Declaração de uso de IA generativa

| Ferramenta | Para quê |
|---|---|
| Claude Code (Anthropic) | Apoio na escrita do código, dos testes, das migrações SQL e da documentação, a partir das decisões de arquitetura tomadas pelo grupo (registradas em [docs/decisoes-arquitetura.md](docs/decisoes-arquitetura.md)). Todo o código foi revisado, executado e testado pelo grupo. |
| OpenAI GPT-6 Luna (via Spring AI) | Funcionalidade do produto: descrição do ambiente a partir da foto, com saída estruturada. |
