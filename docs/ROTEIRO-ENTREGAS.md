# Roteiro das entregas · Java Advanced + DevOps Tools & Cloud Computing

Prazo no Portal FIAP: **19/11/2026 até 23h55, sem prorrogação.** Apresentações presenciais de 23 a 27/11.

---

## 1. Checklist do que precisa ser entregue

### Java Advanced (100 pts + apresentação 30%)
- [ ] Repositório **público no GitHub** com este código e o README preenchido: link do deploy e tabela de integrantes
- [ ] Link do **vídeo pitch de até 5 min** (YouTube não listado serve)
- [ ] **.zip** com: `identificacao.txt` (RM, nome completo e turma de cada integrante), link do repositório e link do vídeo
- [ ] O projeto compila e sobe seguindo o README (`./mvnw verify` e `./mvnw spring-boot:run`): **sem isso perde 30 pts**

### DevOps (70 pts + apresentação 30%)
- [ ] Projeto **privado** no Azure DevOps com Boards, Repos e Pipelines
- [ ] Professor convidado: **Organização = Basic** e **Projeto = Project Administrators** (confirme o e-mail com o professor)
- [ ] Main protegida: revisor obrigatório, work item vinculado e revisor padrão (seu RM)
- [ ] CI automática após merge na main e Release automática após o novo artefato
- [ ] Vídeo no YouTube (≥ 720p, **narrado por voz, sem legendas no lugar da fala**)
- [ ] **PDF** `NomeDoGrupo_rmXXXXX.pdf` com folha de rosto (nome do grupo, RM e nome completo dos integrantes), link da organização do Azure DevOps e link do vídeo

---

## 2. Subir a infraestrutura (uma vez)

No **Azure Cloud Shell (Bash)**, com o repositório clonado:

```bash
git clone <url-do-azure-repos-ou-github> saneia-api && cd saneia-api
export RM=rm123456                                   # RM do representante
export SQL_ADMIN_PASSWORD='Uma-Senha-Forte-2026'
export GEMINI_API_KEY='<chave do Google AI Studio>'  # opcional; sem ela, o resumo é calculado
export CLIENT_IP=$(curl -s ifconfig.me)              # libera o Query editor do portal para o seu IP
bash scripts/azure-infra.sh
```

Se a oferta gratuita do Azure SQL não estiver disponível na assinatura, rode de novo com `export SQL_TIER=basic`. Se a região recusar recursos, use `export LOCATION=eastus`.

**Guarde o resumo impresso no final**, mas não o coloque no repositório.

---

## 3. Configurar o Azure DevOps (passo a passo)

1. **Organização e projeto:** em https://dev.azure.com crie um projeto **privado** "Saneia", com Git e o processo Agile ou Scrum.
2. **Convidar o professor:** em *Organization settings → Users → Add users* coloque o e-mail com nível de acesso **Basic**. Depois, em *Project settings → Permissions → Project Administrators → Add*, adicione o professor.
3. **Repos:** em *Repos → Import repository* cole a URL do GitHub, ou faça `git remote add azure <url>` e `git push azure main`.
4. **Service connection:** em *Project settings → Service connections → New → Azure Resource Manager*, escolha *Workload identity federation (automatic)*, a assinatura e o resource group `rg-saneia-<rm>`. Dê o nome **`saneia-azure`**.
5. **Pipeline de Build (CI):** em *Pipelines → New pipeline → Azure Repos Git → Existing YAML file → `/azure-pipeline.yml`*. Salve e **renomeie para `saneia-api-ci`** (*⋮ → Rename*).
6. **Pipeline de Release (CD):**
   - Antes, edite `azure-pipeline-release.yml` e troque `WEBAPP_NAME` e `RESOURCE_GROUP` pelos nomes do resumo do script. Faça isso por PR, conforme o item 8.
   - Depois, em *Pipelines → New pipeline → Existing YAML file → `/azure-pipeline-release.yml`*, renomeie para `saneia-api-release`.
   - Na primeira execução, autorize o uso da service connection e do environment `saneia-producao` (*Permit*).
7. **Boards:** crie a **tarefa inicial**, por exemplo "Configurar CI/CD da Saneia API", e as tarefas do backlog.
8. **Política da main:** em *Repos → Branches → main → ⋮ → Branch policies*:
   - **Require a minimum number of reviewers** = 1, com **Allow requestors to approve their own changes** marcado (simulação permitida)
   - **Check for linked work items** = Required
   - **Automatically included reviewers** = o seu usuário/RM (revisor padrão)
   - Com a política ativa, ninguém faz push direto na main: tudo entra por PR, e o merge dispara a CI
9. **Testes manuais (Test Plans), se pedidos na disciplina de QA:** crie *Test Plans → New Test Plan* com os casos por feature.

### Variáveis usadas nas pipelines
- **CI:** `JAVA_VERSION`, `MAVEN_OPTS`, `MAVEN_CACHE_FOLDER`, `SPRING_PROFILES_ACTIVE` e `ARTIFACT_NAME`
- **Release:** `AZURE_SERVICE_CONNECTION`, `WEBAPP_NAME`, `RESOURCE_GROUP` e `API_BASE_URL`, mais `APP_BUILD`, que grava o número do build no App Service e aparece em `/api/about`
- As senhas (banco, RabbitMQ, JWT, chave do dispositivo) ficam como **variáveis de ambiente do App Service**, criadas pelo script, e não aparecem em nenhum arquivo

---

## 4. Roteiro do vídeo de DevOps (gravação contínua, ≥ 720p, narrado)

| Etapa obrigatória | O que mostrar |
| --- | --- |
| 1. README, solução e arquitetura | Abra o README no Azure Repos: o problema, o desenho `docs/arquitetura.png` e os fluxos |
| 2. Recursos criados pelo script | Portal Azure → `rg-saneia-<rm>`: Web App, App Service Plan, SQL Server, banco e Container Instance do RabbitMQ. Mostre também `scripts/azure-infra.sh` |
| 3. Nova tarefa no Boards | Crie a tarefa "Alterar mensagem de versão da API" |
| 4. Nova branch e alteração **no código** | A partir da tarefa, *Create branch* `feature/mensagem-versao`. Edite `src/main/java/br/com/saneia/api/about/AboutController.java`, troque `RELEASE_NOTE` e faça o commit citando a tarefa (`#ID`) |
| 5. PR e merge na main | Abra o PR, vincule a tarefa, aprove e complete o merge |
| 6. Pipelines automáticas | A CI `saneia-api-ci` inicia sozinha. Mostre as etapas: cache, Maven verify, testes e artefato |
| 7. Artefatos da CI | Aba *Tests* (resultados JUnit), *Code Coverage* (JaCoCo) e *Artifacts* (`saneia-api` com o jar e os scripts) |
| 8. Release automática | `saneia-api-release` dispara após a CI. Mostre o deploy e o smoke test |
| 9. Alteração publicada na nuvem | No navegador, `https://saneia-api-<rm>.azurewebsites.net/api/about`: a nova `releaseNote` e o `build` aparecem |
| 10. CRUD em 2 tabelas, conferido **direto no banco** | No Swagger: login de admin → **sn_sensors**: POST, GET, PATCH e DELETE. Depois de **cada** operação, rode o `SELECT` de `scripts/consultas-crud.sql` no *Query editor* do Azure SQL. Repita com **sn_reports** (login de morador: POST, GET, PATCH e DELETE; status pela operadora). **Não use o GET como prova de persistência: a prova é o SELECT** |
| 11. Tarefa concluída | Volte ao Boards: a tarefa em *Done*, com os links de branch, commit, PR e build |

Antes de entregar, assista ao vídeo inteiro no YouTube e confira os 11 itens. Cada item faltando custa 20 pts.

---

## 5. Roteiro do pitch de Java (até 5 min)

1. **Problema e público** (40 s): reservatórios de esgoto que extravasam sem aviso em comunidades com saneamento precário. Morador sem canal e operadora sem dados.
2. **Solução** (40 s): app + API + sensores. Mostre o desenho de arquitetura.
3. **Demo do morador** (60 s): cadastro, novo relato com foto, protocolo, regra de 20 dias (409 → complementar o protocolo aberto).
4. **Demo da operadora** (60 s): rode `scripts/simular-leituras.sh` (ou o Wokwi) e mostre o ponto passando para alerta e depois alarme no mapa. Abra a ficha e clique em **Insights** (Spring AI + Gemini). Atualize o protocolo até o encerramento e mostre a notificação do morador.
4b. **Painel admin + e-mail** (30 s): abra `/api/painel` com a área em **Risco máximo** piscando, mostre o **e-mail que chegou** no celular ou na caixa de entrada e o registro em *Alertas por e-mail*. Antes da gravação, cadastre seu e-mail em Alertas e use *Enviar e-mail de teste* para conferir o SMTP.
5. **Por dentro** (50 s): JWT com 3 papéis, Swagger, Bean Validation com o advice, paginação e RabbitMQ (painel com as filas `saneia.*` e a DLQ).
6. **Impacto e evolução** (30 s): ODS 6 e 11, e o módulo separador de resíduos no pitch.

---

## 6. Perguntas prováveis na apresentação

- **Por que RabbitMQ?** A leitura do sensor não precisa bloquear o dispositivo: a API responde 202, e o consumidor grava e avalia. As notificações também ficam desacopladas. Se o consumidor falhar 3 vezes, a mensagem vai para a DLQ.
- **Como evita evento perdido ou fantasma?** A publicação acontece depois do commit (`TransactionSynchronization.afterCommit`).
- **Como o JWT é validado?** Pelo resource server do Spring Security, com HS256 e segredo na variável `JWT_SECRET`. O papel vai na claim `role` e vira `ROLE_*`.
- **E se o LLM errar números?** Os números vêm do backend. O prompt proíbe inventar valores, e a resposta devolve os `indicators` para conferência. Sem chave, ou com falha, o resumo é calculado.
- **Como o e-mail de risco máximo é disparado?** O consumidor da leitura detecta o limite e publica `sensor.alert`. Outro consumidor, o de notificações, monta o HTML com Thymeleaf e envia via JavaMailSender. Cada envio fica em `sn_email_logs`, e um alarme contínuo gera um único e-mail.
- **Por que o painel tem outra cadeia de segurança?** O app usa JWT stateless. O painel web usa sessão com formulário e CSRF, porque é navegação no browser. São dois `SecurityFilterChain`, e o do painel tem `@Order(1)` e `securityMatcher("/painel/**")`.
- **Por que exclusão lógica?** O contrato exige preservar histórico e vínculos, e a regra dos 20 dias continua valendo após excluir.
- **Como a Release sabe que tem artefato novo?** Pelo `resources.pipelines` com trigger na CI `saneia-api-ci` (branch main).
