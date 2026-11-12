# Vê AI · Roteiro do vídeo pitch (até 5 min)

> Os tempos seguem a rubrica de Java Advanced. As falas são uma sugestão: leia em voz alta e ajuste ao seu jeito.
> O vídeo também é usado na premiação: dê mais peso ao problema e ao impacto do que ao código.

**Antes de gravar**
- Rode `docker compose up -d` e suba a API, de preferência a do deploy na Azure, para mostrar a nuvem.
- Tenha o app aberto com a **Ana** (cidadã) logada, e a **Marina** (gestora) logada no Swagger ou na tela do gestor.
- Configure `VEAI_IA_PROVEDOR=openai` (a IA real) e escolha um trecho de calçada com um obstáculo fixo bem visível (um poste no meio da calçada é o ideal), sem trânsito de pedestres atrapalhando.
- Grave a caminhada em primeira pessoa, com o protótipo (celular no suporte de peito e fone de condução óssea) e o **modo automático** ligado. O áudio da narração é a parte principal da demonstração: grave o som do celular, ou capture a tela com áudio e edite junto.
- Faça uma ou duas caminhadas de ensaio: veja em que distância o poste é anunciado e quando aparece a pergunta do relato.
- Ative o leitor de tela (TalkBack/VoiceOver) no celular.

---

## 0:00 – 0:45 · Problema

**Na tela:** imagens de calçadas com buraco, poste no meio do caminho, piso tátil interrompido. Pode ser um vídeo em primeira pessoa, andando pela calçada.

> "No Brasil, cerca de 6,5 milhões de pessoas têm deficiência visual. Para quem se locomove sozinho, cada saída de casa tem obstáculos que ninguém mapeia: um buraco, um poste no meio da calçada, uma obra sem sinalização.
> E quando alguém percebe o problema, não há um caminho simples para avisar quem pode resolver. A barreira continua lá, para a próxima pessoa."

## 0:45 – 1:30 · Solução

**Na tela:** logo do Vê AI e o diagrama de três passos: caminhada → narração → relato à rede de apoio. Quando falar do fone, mostre o protótipo: o suporte de peito com o celular e o fone de condução óssea.

> "O Vê AI é um assistente em português que acompanha a pessoa enquanto ela anda. Com o celular no peito, o app fotografa o caminho a cada poucos segundos e a inteligência artificial **narra o que está à frente da pessoa, começando pelos perigos**: um poste a três metros, um degrau, uma bicicleta vindo, a entrada de uma loja mais para a direita.
> Conforme ela anda, a narração acompanha: quando surge um objeto novo, um perigo chega mais perto ou o ambiente muda, como uma esquina ou a entrada num prédio, o app avisa. Quando nada muda, ele só confirma em uma frase curta, como 'o poste continua no mesmo lugar, à sua frente', para a pessoa saber que o app está atento sem ter os sons da rua encobertos por uma descrição longa.
> A narração chega por um fone de condução óssea, que transmite o som pelos ossos do rosto e deixa o ouvido livre. Isso importa porque a pessoa cega se orienta pela audição: o barulho dos carros, o sinal sonoro do semáforo, os passos de quem vem na direção dela. Um fone comum tamparia justamente esse sentido.
> A qualquer momento, a pessoa também pode pedir uma descrição completa ou a leitura de um texto, como uma placa ou a caixa de um remédio.
> Quando a IA reconhece uma barreira permanente, o app pergunta, uma vez só: 'Quer avisar a rede de apoio?'. O relato vai com a localização para o técnico da ONG ou do CRAS da região, que faz a triagem e encaminha à prefeitura.
> Se outra pessoa relatar a mesma barreira, a menos de 30 metros, o relato ganha força em vez de se repetir."

## 1:30 – 3:00 · Demonstração (o trecho mais importante)

| Tempo | Ação | O que falar |
|---|---|---|
| 1:30 | Vídeo em primeira pessoa: a Ana liga o **modo automático** e começa a andar pela calçada. O celular narra: "Bem à sua frente, a cerca de 5 metros, há um poste no meio da calçada…" | "O app fotografa a cada poucos segundos e a primeira coisa que fala é o perigo, com a direção e a distância." |
| 1:45 | Ela continua andando. O celular confirma "O poste continua no mesmo lugar, à sua frente." e, logo depois, "O poste está agora a menos de 1 metro, bem à sua frente." | "Ele não repete a descrição inteira a cada foto. A API compara a cena com a anterior: quando nada muda, confirma em uma frase; quando algo muda, como o poste chegando mais perto, avisa. Cada foto é respondida em cerca de 3 segundos." |
| 2:00 | O app pergunta "Parece haver calçada obstruída. Quer avisar a rede de apoio?" e a Ana confirma. | "A pergunta aparece uma vez só, mesmo com o poste em várias fotos seguidas. Quem decide que é uma barreira é a IA, com Spring AI e saída estruturada. Perigos momentâneos, como um carro ou uma bicicleta, nunca viram relato." |
| 2:10 | Outro cidadão (Diego) relata o mesmo problema a poucos metros. A resposta é "APOIO_REGISTRADO". | "A API reconhece que é o mesmo problema e soma um apoio. A prioridade sobe." |
| 2:25 | Troque para a **Marina**: a fila do gestor mostra o relato no topo. Ela muda para **EM_TRIAGEM** e depois para **ENCAMINHADO** com o protocolo SP156. | "A gestora só vê as regiões dela, ordenadas por prioridade. Cada mudança fica no histórico." |
| 2:45 | Volte para a Ana: o app lê a notificação "Seu relato foi encaminhado ao órgão responsável. Protocolo…". | "A notificação é assíncrona: a mudança publica um evento no RabbitMQ e um consumidor grava o aviso para a Ana e para todos que apoiaram." |

**Plano B se a internet falhar:** mostre a mesma sequência no Swagger (`/swagger-ui.html`) com o provedor de IA `falso`: envie uma foto, depois a mesma foto com `anteriorId` (a resposta vem com `novidade: false`, uma confirmação curta e sem repetir a sugestão de relato).

## 3:00 – 4:00 · Modelo de negócio

**Na tela:** dois lados, cidadão e instituição.

> "Para o cidadão, o Vê AI é **gratuito**, sempre.
> Quem paga são as instituições: ONGs e prefeituras assinam o **painel de gestão** e os **relatórios de acessibilidade**, que mostram onde estão as barreiras, quantas pessoas são afetadas e quanto tempo cada uma leva para ser resolvida.
> No modo automático são muitas fotos por caminhada, então o custo foi pensado desde o início: a foto é reduzida antes de ir para a IA, o que corta o custo por foto em cerca de 5 vezes, o app só manda a próxima foto quando recebe a resposta e a API limita o uso por pessoa.
> Para chegar a quem precisa, queremos buscar parcerias com instituições que já atendem pessoas com deficiência visual, como a Fundação Dorina Nowill."

## 4:00 – 5:00 · Impacto e ODS

**Na tela:** os ícones dos ODS 10, 11, 3 e 9 e as três métricas.

> "Medimos o impacto com três números: **relatos resolvidos**, **tempo médio até a resolução** e **taxa de descrições avaliadas como úteis**.
> O Vê AI contribui para os ODS 10 (redução das desigualdades), 11 (cidades inclusivas), 3 (saúde e bem-estar) e 9 (inovação).
> O próximo passo são os óculos inteligentes: a câmera passa para a altura dos olhos e acompanha para onde a pessoa vira a cabeça, enxerga melhor os obstáculos na altura do rosto, que a bengala não alcança, e o som sai pelas próprias hastes, sem suporte nem fone extra.
> Vê AI: a cidade descrita para quem não pode vê-la, e cada barreira avisada a quem pode removê-la."

---

**Checklist da gravação**
- [ ] Duração total de até 5:00
- [ ] Áudio limpo e tela legível (aumente a fonte do Swagger e do app)
- [ ] Demonstração com o app real, e não só slides
- [ ] Vídeo público ou não listado no YouTube, com o link no README e no `.txt` de entrega
