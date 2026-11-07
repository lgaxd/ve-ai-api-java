# Vê AI · Roteiro do vídeo pitch (até 5 min)

> Os tempos seguem a rubrica de Java Advanced. As falas são uma sugestão: leia em voz alta e ajuste ao seu jeito.
> O vídeo também é usado na premiação: dê mais peso ao problema e ao impacto do que ao código.

**Antes de gravar**
- Rode `docker compose up -d` e suba a API, de preferência a do deploy na Azure, para mostrar a nuvem.
- Tenha o app aberto com a **Ana** (cidadã) logada, e a **Marina** (gestora) logada no Swagger ou na tela do gestor.
- Se a demonstração for usar a IA real, configure `VEAI_IA_PROVEDOR=openai` e separe 2 ou 3 fotos: uma calçada com poste no meio, uma placa de texto e uma rua livre.
- Ative o leitor de tela (TalkBack/VoiceOver) no celular: o som da descrição é parte da demonstração.
- Confirme o número do Censo 2022 do IBGE antes de gravar (está marcado como ⚠️ abaixo).

---

## 0:00 – 0:45 · Problema

**Na tela:** imagens de calçadas com buraco, poste no meio do caminho, piso tátil interrompido. Pode ser um vídeo em primeira pessoa, andando pela calçada.

> "No Brasil, milhões de pessoas têm deficiência visual ⚠️ *(confirmar o número do Censo 2022 do IBGE)*. Para quem se locomove sozinho, cada saída de casa tem obstáculos que ninguém mapeia: um buraco, um poste no meio da calçada, uma obra sem sinalização.
> E quando alguém percebe o problema, não há um caminho simples para avisar quem pode resolver. A barreira continua lá, para a próxima pessoa."

## 0:45 – 1:30 · Solução

**Na tela:** logo do Vê AI e o diagrama de três passos: foto → áudio → relato à rede de apoio.

> "O Vê AI é um assistente em português que descreve o ambiente por áudio. A pessoa aponta o celular, e a inteligência artificial fala **primeiro os perigos** e depois o resto.
> Quando a IA reconhece uma barreira permanente, o app pergunta: 'Quer avisar a rede de apoio?'. O relato vai com a localização para o técnico da ONG ou do CRAS da região, que faz a triagem e encaminha à prefeitura.
> Se outra pessoa relatar a mesma barreira, a menos de 30 metros, o relato ganha força em vez de se repetir."

## 1:30 – 3:00 · Demonstração (o trecho mais importante)

| Tempo | Ação | O que falar |
|---|---|---|
| 1:30 | No app, a Ana toca em **Descrever** e fotografa a calçada com o poste. O celular fala a descrição. | "Repare que o primeiro aviso é o perigo: poste no meio da calçada, a três metros." |
| 1:50 | O app pergunta "Parece haver calçada obstruída. Quer avisar a rede de apoio?" e a Ana confirma. | "Quem decide que é uma barreira é a IA, com Spring AI e saída estruturada. Perigos momentâneos, como um carro ou uma bicicleta, nunca viram relato." |
| 2:10 | Outro cidadão (Diego) relata o mesmo problema a poucos metros. A resposta é "APOIO_REGISTRADO". | "A API reconhece que é o mesmo problema e soma um apoio. A prioridade sobe." |
| 2:25 | Troque para a **Marina**: a fila do gestor mostra o relato no topo. Ela muda para **EM_TRIAGEM** e depois para **ENCAMINHADO** com o protocolo SP156. | "A gestora só vê as regiões dela, ordenadas por prioridade. Cada mudança fica no histórico." |
| 2:45 | Volte para a Ana: o app lê a notificação "Seu relato foi encaminhado ao órgão responsável. Protocolo…". | "A notificação é assíncrona: a mudança publica um evento no RabbitMQ e um consumidor grava o aviso para a Ana e para todos que apoiaram." |

**Plano B se a internet falhar:** mostre a mesma sequência no Swagger (`/swagger-ui.html`) com o provedor de IA `falso`.

## 3:00 – 4:00 · Modelo de negócio

**Na tela:** dois lados, cidadão e instituição.

> "Para o cidadão, o Vê AI é **gratuito**, sempre.
> Quem paga são as instituições: ONGs e prefeituras assinam o **painel de gestão** e os **relatórios de acessibilidade**, que mostram onde estão as barreiras, quantas pessoas são afetadas e quanto tempo cada uma leva para ser resolvida.
> O custo por foto é de frações de centavo, e a API limita o uso por pessoa para manter o serviço sustentável.
> Parceiros como a Fundação Dorina Nowill ajudam a levar o app a quem precisa."

## 4:00 – 5:00 · Impacto e ODS

**Na tela:** os ícones dos ODS 10, 11, 3 e 9 e as três métricas.

> "Medimos o impacto com três números: **relatos resolvidos**, **tempo médio até a resolução** e **taxa de descrições avaliadas como úteis**.
> O Vê AI contribui para os ODS 10 (redução das desigualdades), 11 (cidades inclusivas), 3 (saúde e bem-estar) e 9 (inovação).
> O próximo passo são os óculos inteligentes: a mesma descrição, sem precisar segurar o celular.
> Vê AI: a cidade descrita para quem não pode vê-la, e cada barreira avisada a quem pode removê-la."

---

**Checklist da gravação**
- [ ] Duração total de até 5:00
- [ ] Áudio limpo e tela legível (aumente a fonte do Swagger e do app)
- [ ] Demonstração com o app real, e não só slides
- [ ] Vídeo público ou não listado no YouTube, com o link no README e no `.txt` de entrega
