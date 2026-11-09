# Vê AI · API Java · Decisões de arquitetura

> Repositório `ve-ai-api-java` · versão de 07/11/2026 · decisões fechadas nas rodadas Q1–Q29.
> Referências: `escopo-ve-ai.md` (v1.2) e `guia-disciplinas-ve-ai.md`, seção 6 (Java Advanced).

Este documento registra **o que foi decidido e por quê**. Onde ele diverge do escopo, a seção 12 lista a mudança e quem precisa saber dela.

---

## 1. Stack

| Item | Decisão | Motivo |
|---|---|---|
| Linguagem | **Java 21** (LTS) | É a versão suportada pelo Azure App Service. O JDK 26 da máquina não é usado. |
| Framework | **Spring Boot 4.1.x** | Linha estável atual. O Boot 3.5 já está fora do suporte gratuito. |
| IA | **Spring AI 2.0.1** (`spring-ai-starter-model-openai`) | É a linha do Spring AI feita para o Boot 4. A 2.0.1 é a versão que o Spring Initializr indica para o Boot 4.1.2. |
| Build | Maven (com `mvnw`) | |
| Persistência | Spring Data JPA + Hibernate 7 | |
| Migrações | **Flyway** | Schema versionado; o Hibernate só valida (`ddl-auto=validate`). |
| Segurança | Spring Security + `oauth2-resource-server` (JWT HS256) | Suporte nativo, sem biblioteca extra. |
| Mensageria | Spring AMQP + RabbitMQ | Opção **A** do requisito 5. |
| Documentação | springdoc-openapi (Swagger UI) | |
| Testes | JUnit 5 + MockMvc + **Testcontainers** | Sem H2: os testes de integração usam Oracle XE e RabbitMQ reais. |

## 2. Organização do código

Por **funcionalidade**, e não por camada, para que cada parte do domínio seja fácil de mostrar na apresentação:

```
src/main/java/br/com/fiap/veai/
  auth/          login, registro, emissão de JWT
  usuario/       perfil e preferências
  regiao/        regiões e escolha da região mais próxima
  descricao/     descrição por IA, histórico, avaliação, pergunta livre
  relato/        relatos, apoios, deduplicação, status, prioridade, fila do gestor
  notificacao/   leitura e marcação de notificações
  ia/            DescritorImagem (OpenAI e falso) + cliente opcional do RAG
  mensageria/    exchange, filas, publicação e @RabbitListener
  config/        segurança, OpenAPI, CORS, rate limit
  comum/         erros, PaginaResposta, Haversine, utilitários
```

- **DTOs são `record`**, tanto os de entrada (com Bean Validation) quanto os de saída. Também são records os tipos da saída estruturada da IA (`DescricaoIA`, `Perigo`, `Barreira`).
- **Entidades JPA são classes**, porque o JPA não aceita `record` como entidade.
- Os controllers nunca expõem entidades.

## 3. Ambientes e perfis

| Perfil | Banco | Broker | Uso |
|---|---|---|---|
| `local` | Oracle XE 21 no Docker (WSL) | RabbitMQ 4 no Docker (WSL) | Desenvolvimento |
| `fiap` | Oracle da FIAP (`oracle.fiap.com.br:1521:ORCL`) | RabbitMQ (Docker) ou CloudAMQP | Entrega de Java Advanced |
| `azure` | Azure SQL | CloudAMQP | Entrega de DevOps |

- O ambiente local sobe com `docker compose up -d --build`: API (imagem do `Dockerfile`, multi-stage com Maven e JRE 21), Oracle XE 21 e RabbitMQ com o painel de gestão. Dentro do compose a API usa sempre os containers, mesmo que o `.env` aponte para a FIAP, para o professor poder testar tudo localmente com o `.env` da equipe. Para desenvolver, `docker compose up -d oracle rabbitmq` sobe só a infraestrutura. O XE 21 é a versão gratuita mais próxima do 19c da FIAP: não tem `BOOLEAN` nativo, então o Hibernate grava booleanos em `NUMBER(1)`, como no DDL. No WSL, é preciso instalar o plugin com `sudo apt install docker-compose-v2`.
- Os containers do WSL ficam acessíveis pelo `localhost` do Windows.
- Todos os segredos vêm de variáveis de ambiente ou de um `.env` na raiz (lido pelo `spring.config.import`): `DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBITMQ_URL`, `JWT_SECRET`, `OPENAI_API_KEY`. O perfil também pode vir do `.env` (`SPRING_PROFILES_ACTIVE`). Nenhum segredo vai para o repositório. Há um `.env.example` com os nomes, sem valores.

## 4. Banco de dados

- **Flyway** com uma pasta por fornecedor:
  - `db/migration/oracle`: a fonte principal. A V1 é o mesmo DDL do `oracle/01_ddl.sql` do repositório `database` e a V2 é o PL/SQL de `02` a `05`; a V3 acrescenta só o que a API usa (`REGIAO.latitude/longitude` e `IDEMPOTENCIA`). As migrações são **idempotentes** (criam só o que falta) e o Flyway usa `baseline-on-migrate` com versão 0 e um histórico próprio (`veai_flyway_historico`, para não se misturar com o `flyway_schema_history` do PetFlow quando o schema da FIAP é compartilhado), então a API sobe tanto num schema vazio quanto num schema da FIAP onde o `00_instalar.sql` já rodou.
  - `db/migration/sqlserver`: Azure SQL. Ela também serve como `script-bd.sql` da entrega de DevOps.
- No SQL Server, `ddl-auto=validate`: se uma entidade não bater com a tabela, a aplicação não sobe. No Oracle, `ddl-auto=none`: o DDL do repositório `database` usa `NUMBER`, `DATE` e `CLOB`, que não batem tipo a tipo com o que o Hibernate geraria (`float`, `timestamp`). O schema é garantido pelo Flyway, e os testes de integração rodam contra Oracle real.
- **Datas no Oracle:** as colunas são `DATE` (como no DDL); o `Instant` é gravado como `TIMESTAMP` simples (`hibernate.type.preferred_instant_jdbc_type`), no fuso `DB_TIME_ZONE` (padrão UTC).
- **Seed de demo** numa migração separada (`R__seed_demo`), ligada nos perfis `local`, `fiap` e `azure`, e aplicada somente se o banco estiver vazio. No Oracle, ela é gerada do `06_seed.sql` (mesmos IDs; os apoios são contados pelo `trg_conta_apoio`) e também troca o hash ilustrativo do `06_seed.sql` pelo da senha `Veai@2026`. Ela contém as mesmas 3 regiões e os mesmos 6 usuários do repositório `database` (cidadãos Ana, Bruno, Carla e Diego; gestores Marina e Rafael), além de alguns relatos e descrições, para a fila do gestor no app não começar vazia. A senha de todos é `Veai@2026`, documentada no README.
- **Campos JSON** (`perigos` e `objetos`) são gravados como texto JSON por um `AttributeConverter` (`ListaJsonConverter`): `CLOB CHECK (... IS JSON)` no Oracle e `nvarchar(max)` no SQL Server. Na entidade são `List<Perigo>` e `List<String>`. O `@JdbcTypeCode(SqlTypes.JSON)` não serve no Oracle: no 21+ o Hibernate grava JSON binário (OSON) e no 19c um BLOB, e nenhum dos dois cabe numa coluna `CLOB`.
- **Gatilhos do banco valem para a API:** o `trg_auditoria_relato` audita as mudanças feitas pela API; o `trg_conta_apoio` soma o apoio em `total_apoios` no `INSERT` do apoio, e o Hibernate grava depois o mesmo valor absoluto calculado na entidade, então o apoio não é contado em dobro.
- **Tamanhos:** os DTOs seguem as colunas do Oracle: `endereco` 200, `protocoloOrgao` 40, `motivoDescarte` 300; a `normaReferencia` do RAG é cortada em 200.

## 5. Segurança

- `POST /auth/login` devolve um JWT HS256 assinado com `JWT_SECRET`, válido por **8 h**, sem refresh token.
- O claim `papel` vira `ROLE_CIDADAO` ou `ROLE_GESTOR`. A autorização usa `@PreAuthorize` por papel.
- O `POST /auth/registrar` cria apenas CIDADAO. Os gestores vêm do seed.
- O gestor só vê e altera relatos das regiões vinculadas a ele em `GestorRegiao`. Fora delas, recebe **403**.
- Senhas com BCrypt.
- Rotas públicas: `/auth/**`, `/health`, Swagger.

## 6. Contrato HTTP

- O **contrato vem primeiro**: os DTOs, os controllers e o Swagger são entregues **até 08/11**, junto com `docs/openapi.yaml` exportado e commitado para o Lucca (.NET) e o Pietro (Mobile). A implementação real vem depois.
- **JSON** em camelCase, em português. Enums são strings em maiúsculas.
- **Paginação:** os parâmetros são `page` (começando em 0) e `size` (padrão 10, máximo 50). A resposta é um record próprio, porque serializar o `Page` do Spring é desaconselhado e deixaria o contrato instável:
  ```json
  { "conteudo": [], "pagina": 0, "tamanho": 10, "totalElementos": 0, "totalPaginas": 0 }
  ```
- **Erros** (`@RestControllerAdvice`), com mensagens em português curtas, pensadas para serem lidas em voz alta pelo app:
  ```json
  { "timestamp": "...", "status": 400, "erro": "Bad Request", "mensagem": "...", "campos": [{ "campo": "texto", "mensagem": "..." }] }
  ```

## 7. Descrições (IA)

- A interface **`DescritorImagem`** tem duas implementações, escolhidas por `veai.ia.provedor`:
  - `openai`: usa o `ChatClient` do Spring AI com a imagem como `Media` e `.entity(DescricaoIA.class)` para a saída estruturada da seção 5.1 do escopo. O modelo é definido em `veai.ia.modelo=gpt-6-luna`.
  - `falso` (padrão): devolve respostas fixas e verossímeis, sem gastar créditos.
- O schema da seção 5.1 é usado desde já. Se o prompt do Pedro chegar, muda só o texto do prompt.
- **Imagem:** aceita JPEG, PNG e WebP de até 5 MB. Fora disso, responde 413/415. A imagem fica só na memória durante a chamada e **nunca é gravada**, nem em log.
- **Redução da foto** (`RedutorImagem`): JPEG e PNG maiores que `veai.ia.lado-maximo-foto` (1024 px) são reduzidos em memória antes de ir para o modelo, aplicando a rotação do EXIF (fotos de celular em pé vêm "deitadas" com a tag Orientation). WebP e arquivos que o Java não decodifica seguem como vieram.
- **Latência medida** com o gpt-6-luna e uma foto de calçada:

  | Configuração | Tempo da IA | Tokens de entrada |
  |---|---|---|
  | Foto de 4032 px, raciocínio padrão do modelo | ~18 s | 16.550 |
  | Foto de 1024 px, `reasoning-effort: low` | 7 a 10 s | 3.107 |
  | Foto de 1024 px, `reasoning-effort: none` (padrão) | 2 a 4 s | 3.107 |

  O padrão é `none` (`OPENAI_REASONING_EFFORT`). No modo automático, a demora também é risco: com 8 s de atraso, a pessoa já andou cerca de 10 m quando ouve o aviso. Nos testes de caminhada, o `none` também acertou melhor quando a cena não tinha mudado. O `low` repetia avisos porque a distância estimada variava entre duas fotos iguais.
- No modo TEXTO, o `textoLido` da IA é guardado em `descricaoCompleta`.
- **Sugestão de relato:** quando `barreira.detectada = true` e `confianca ≥ veai.ia.confianca-minima` (padrão 0,7), a resposta traz `sugestaoRelato: {tipo, gravidade, pergunta}`. Perigos momentâneos nunca geram sugestão.
- **Modo automático** (fotos a cada poucos segundos enquanto a pessoa anda):
  - o app manda `anteriorId`, o id da **última descrição que falou**. A API envia ao modelo o texto dela ("DESCRIÇÃO ANTERIOR") e as regras de CONTINUIDADE do prompt;
  - o modelo responde `novidade`. Com `true`, a `descricaoCompleta` traz só o que mudou (perigo novo, perigo bem mais perto, mudança de lugar). Com `false`, traz uma frase curta para o histórico, e o app não fala;
  - rede de segurança na API: se antes não havia perigo e agora há, `novidade` é `true`, diga o modelo o que disser;
  - a descrição anterior só vale no modo AMBIENTE e por até `veai.continuo.validade-contexto` (2 min). Depois disso, a cena é descrita inteira de novo (reorientação periódica);
  - a mesma sugestão de relato (mesmo tipo de barreira) não se repete por `veai.continuo.janela-sugestao` (2 min), para o app não interromper a caminhada a cada foto (`MemoriaSugestoes`, em memória como o rate limit). No modo manual, a sugestão sempre aparece;
  - `novidade` não é gravada (não há coluna no Oracle): vem só na resposta do POST e é `null` no histórico. Assim o schema do repositório `database` não muda;
  - sem `anteriorId` (modo manual, ou primeira foto da caminhada), tudo funciona como antes e `novidade` é sempre `true`. A escolha entre automático e manual fica só no app.
- **Pergunta livre:** `POST /descricoes/{id}/pergunta`, em multipart com `imagem` (o app reenvia a última foto) e `pergunta`, devolve `{resposta}`. Nada é gravado. A rota exige `perguntaLivreAtiva = true` e conta no rate limit.
- **Rate limit:** janela fixa em memória, com **20 requisições por minuto por usuário** em `POST /descricoes` e na pergunta livre. Acima disso, responde **429** com `Retry-After`. O motivo é controlar o custo da OpenAI e manter o mesmo contrato da versão .NET.

## 8. Relatos

- **Região:** a `Regiao` ganha `latitude` e `longitude` (o centro do bairro). A API atribui o relato à região de centro mais próximo. O `regiaoId` no `POST /relatos` é opcional e, se vier, prevalece.
- **Deduplicação (30 m):** a consulta JPA pré-filtra por um quadrado em volta do ponto, calculado a partir do raio (cerca de ±0,00027° de latitude e ±0,00029° de longitude em São Paulo, porque o grau de longitude encolhe com a latitude), entre relatos do **mesmo tipo** e com status **não final** (ABERTO, EM_TRIAGEM, ENCAMINHADO). Nos candidatos, a distância é calculada com Haversine em Java. É a mesma regra do PL/SQL e funciona igual em Oracle e SQL Server.
- **Respostas do `POST /relatos`**, iguais ao resultado do procedimento Oracle:
  - `201` `{resultado: "CRIADO", apoioRegistrado: false, relato}`
  - `200` `{resultado: "APOIO_REGISTRADO", apoioRegistrado: true, relato}`
  - `200` `{resultado: "JA_REGISTRADO", apoioRegistrado: false, relato}`, quando quem envia é o próprio autor ou alguém que já apoiou.
- **Idempotência:** o header `Idempotency-Key` em `POST /relatos` é guardado numa tabela com a chave, o usuário e a resposta, por **24 h**. O caso real é uma conexão ruim ou um toque duplo.
  - Uma repetição devolve a mesma resposta (mesmo status e mesmo corpo) com o header `Idempotent-Replayed: true`.
  - A mesma chave com outro conteúdo responde **422**. O conteúdo é comparado por um hash SHA-256 do corpo.
  - O registro da chave e a ação acontecem na mesma transação. Se duas requisições iguais chegarem juntas, a segunda viola a chave única, é desfeita por inteiro e devolve a resposta da primeira.
  - A chave é por usuário: a mesma chave usada por duas pessoas não colide.
- **Edição pelo cidadão:**
  - O `PUT /relatos/{id}` só funciona com o relato `ABERTO` e altera `tipo`, `gravidade`, `texto` e `endereco`. As coordenadas **não** mudam, porque isso quebraria a deduplicação.
  - O `DELETE /relatos/{id}` só funciona com o relato `ABERTO` e **sem apoios de outras pessoas**. Caso contrário, responde 409 "Outras pessoas já confirmaram este relato".
  - O `GET /relatos/{id}` funciona para o autor e para quem apoiou.
- **Status**, com a mesma máquina de estados do Oracle:
  - O fluxo é `ABERTO → EM_TRIAGEM → ENCAMINHADO → RESOLVIDO`, sem pular etapa e sem voltar.
  - `DESCARTADO` pode vir de qualquer status não final e exige `motivoDescarte`.
  - `ENCAMINHADO` exige `protocoloOrgao`.
  - `RESOLVIDO` e `DESCARTADO` são finais.
  - Cada mudança grava uma `AtualizacaoRelato` e publica `relato.status`.
- **Prioridade (gravada):** é calculada como peso da gravidade (1/3/5) × (1 + totalApoios) + dias em aberto, e vale 0 nos status finais. É recalculada ao criar o relato, ao apoiar, ao mudar o status e por um `@Scheduled` diário às 3h, por causa dos dias em aberto.
- **RAG (opcional):** um cliente `RestClient` com timeout de 3 s é chamado de forma assíncrona **depois** de o relato ser criado, e só se `veai.rag.url` estiver preenchida. Se a URL estiver vazia ou o serviço falhar, `normaReferencia` fica `null` e nada quebra.

## 9. Mensageria (RabbitMQ)

- Topic exchange `veai.relatos` com as routing keys `relato.criado`, `relato.apoiado` e `relato.status`.
- A fila durável `veai.notificacoes` (binding `relato.*`) é consumida por um `@RabbitListener` na própria API. O ack é manual, com retry e dead-letter queue `veai.notificacoes.dlq`.
- O evento é publicado **depois do commit** da transação, para nunca notificar algo que foi desfeito.
- **Destinatários das notificações** (texto curto, para leitura em voz alta):

  | Evento | Quem recebe | Exemplo |
  |---|---|---|
  | `relato.criado` | Gestores da região | "Novo relato de rampa em Pinheiros." |
  | `relato.apoiado` | Autor do relato | "Mais uma pessoa confirmou seu relato." |
  | `relato.status` | Autor e apoiadores | "Seu relato foi encaminhado à prefeitura." |

## 10. Saúde e observabilidade

- `GET /health` vem do Actuator, mapeado para `/health` e público. Os detalhes só aparecem para usuário autenticado.
- Indicadores: `db`, `rabbit` e um `openai` próprio. O `openai` só verifica a chave e o alcance da API, sem gastar tokens, e aparece como desabilitado quando o provedor é `falso`.

## 11. Testes

- **Unitários**, sem container: fórmula de prioridade, Haversine, máquina de estados, escolha da região mais próxima, regra de sugestão de relato, sugestão repetida no modo automático, validade do contexto, redução e rotação EXIF da foto.
- **Integração** com Testcontainers (`@ServiceConnection`, usando Oracle XE 21 e RabbitMQ reais): login, 401/403, gestor fora da região, CRUD de relatos, deduplicação e apoio, idempotência, transições de status, notificações consumidas da fila, 429 e modo automático (`anteriorId`, `novidade`, sugestão sem repetir, 404 para `anteriorId` alheio).
- O Maven roda **dentro do WSL** (`wsl mvn verify`), onde está o socket do Docker. O mesmo `mvn verify` roda no CI do Azure DevOps, cujo agente Ubuntu tem Docker.

## 12. Mudanças em relação ao escopo e quem precisa saber

| Mudança | Impacto |
|---|---|
| Spring Boot **4** + Spring AI 2 (o escopo dizia Boot 3) | Atualizar `escopo-ve-ai.md` |
| Banco da entrega de Java: **Oracle da FIAP** (perfil `fiap`) no lugar do PostgreSQL na Azure; local com Oracle XE no Docker | Repositório `database` (o schema é o mesmo) e quem roda a API localmente |
| Limites de texto alinhados ao Oracle: `endereco` 200, `protocoloOrgao` 40, `motivoDescarte` 300 | **Lucca** (.NET) e **Pietro** (Mobile) |
| `Regiao` com `latitude`/`longitude` e `regiaoId` opcional no `POST /relatos` | **Lucca** (.NET), **Pietro** (Mobile) e o repositório `database` (DDL, dataset e Mongo) |
| Nova rota `POST /descricoes/{id}/pergunta` | **Lucca** e **Pietro** |
| Modo automático: `anteriorId` opcional em `POST /descricoes`, campo `novidade` na resposta e sugestão de relato que não se repete por 2 min | **Lucca** (.NET) e **Pietro** (Mobile: loop sequencial de fotos, falar só com `novidade = true`, tratar 429/502 sem falar) |
| Resposta do `POST /relatos` com `resultado` (`CRIADO`/`APOIO_REGISTRADO`/`JA_REGISTRADO`) | **Lucca** e **Pietro** |
| `DELETE /relatos/{id}` bloqueado quando há apoios de terceiros (409) | **Lucca**, **Pietro** e Test Cases de QA |
| Paginação no formato `PaginaResposta` | **Lucca** e **Pietro** |
| Notificação para gestores em `relato.criado` | **Pietro** (tela de notificações do gestor) |
| Nova rota `GET /gestor/relatos/{id}` (detalhe para a tela do gestor, com o mesmo controle por região) | **Lucca** e **Pietro** |
| Header `Idempotent-Replayed: true` na repetição e 422 para chave reaproveitada com outro conteúdo | **Lucca** e Test Cases de QA |
| Centros das regiões no seed: Pinheiros (-23.5662, -46.6890), Sé (-23.5503, -46.6339) e Vila Mariana (-23.5889, -46.6355) | repositório `database` (DDL e dataset), **Lucca** |

## 14. Achados da implementação

- **Conflito de versões do Swagger:** o Spring AI 2.0.1 traz `swagger-annotations` 2.2.31/2.2.38, e o springdoc 3.2 precisa da 2.2.55. O `/v3/api-docs` falhava com `NoSuchMethodError`. As duas bibliotecas ficaram fixadas na 2.2.55 pelo `dependencyManagement` do `pom.xml`.
- **Texto longo no SQL Server:** a coluna `corpo_resposta` usa `SqlTypes.LONG32NVARCHAR`, que vira `NCLOB` no Oracle e `NVARCHAR(MAX)` no SQL Server. Com `LONG32VARCHAR`, o `validate` do Hibernate recusava o SQL Server.
- **Validação nos dois bancos:** as migrações foram testadas contra Oracle XE 21 (Testcontainers e Docker, num schema vazio e num schema onde o `00_instalar.sql` do repositório `database` já tinha rodado) e, com o `validate` do Hibernate, contra SQL Server 2022 em container, com o perfil `azure`. O SQL Server foi testado com seed, acentos, colunas JSON, idempotência e notificações.
- **Seed gerado do dataset do repositório `database`:** os mesmos IDs, textos, datas relativas e apoios. Os dados batem com os do Oracle e do MongoDB.

## 13. Entregáveis do repositório

- O código, o `docker-compose.yml`, o `.env.example` e o `docs/openapi.yaml`.
- O README para o professor, com: como rodar, usuários de teste, endpoints principais, opção A do requisito 5, link do deploy e declaração de uso de IA.
- O `integrantes.txt` e o `docs/pitch.md`, com o roteiro do vídeo de 5 min (o pitch é escrito depois que a API estiver funcionando).
- Commits locais por etapa. O push fica com o Lucas.
