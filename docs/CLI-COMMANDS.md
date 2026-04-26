# WebRobot CLI — comandi e parametri

Documentazione dei comandi **webrobot-cli** (Picocli) allineata al codice sorgente e verificata contro lo **OpenAPI** pubblicato in **WebRobot.Sdk** (`src/main/resources/openapi/openapi-sanitized.json`, versione coerente col JAR `webrobot.eu:org.webrobot.sdk`).

**Entry point:** `org.webrobot.cli.RunWebRobotCli`  
**Invocazione:** `java -jar … <gruppo> <sottocomando> [opzioni]` (dopo `mvn package` l’uber-jar è in `target/`).

---

## Configurazione e autenticazione

### File `config.cfg` (directory di lavoro) o classpath

Il processo carica `webrobot.api.gateway.credentials` (vedi `RunWebRobotCli.scala`).

| Chiave | Ruolo |
|--------|--------|
| `api_endpoint` | Base URL dell’API (default `https://api.webrobot.eu`). Le richieste usano path assoluti tipo `/webrobot/api/...`. |
| `jwt` / `bearer` / `token` | Se valorizzati, inviato header `Authorization: Bearer <valore>`. |
| `apikey` / `apiKey` | Se non c’è Bearer, chiave API: header `X-API-Key` e `Authorization: ApiKey <valore>`. |

**Help globale:** `webrobot -h` / `webrobot --help`. **Help gruppo:** `webrobot project --help`, ecc.

---

## Modello dominio (allineamento Jersey / API)

Verificato su **Jersey** (`org.webrobot.eu.apis.jersey`):

| Concetto | API | Ruolo |
|----------|-----|--------|
| **Category** | `CategoryApiV10` → `/webrobot/api/categories` | Categoria job (`JobCategoryDto` / ORM `Category`). Contenitore logico a cui sono legati gli **agent** tramite `categoryId`. **Non** è il progetto. |
| **Project** | `ProjectApiV10` → `/webrobot/api/projects/...` | Progetto ETL: contiene **più job** sotto `.../projects/id/{projectId}/jobs`. |
| **Schedule sul progetto** | `GET/PUT .../projects/id/{projectId}/schedule` | Implementazione `ProjectServiceImpl.setProjectSchedule`: il progetto memorizza **un solo** job ETL schedulato alla volta (`scheduledEtlJobId` + espressione cron; CronJob K8s verso `.../jobs/{jobId}/execute`). Con `enabled=true` il **`jobId` è obbligatorio**. Per schedulare un altro job si aggiorna lo schedule con l’altro `jobId` — non è una coda multi-job nativa sulla stessa risorsa schedule. |
| **Agent** | `AgentApiV10` → `/webrobot/api/agents/{categoryId}/...` | Path con **`categoryId`** (id numerico categoria), coerente con `AgentServiceImpl` / FK su `Category`. |

Prima mancava il gruppo CLI **`category`**; è stato aggiunto per allinearsi a `CategoryApiV10` e all’OpenAPI (`getAllCategories`, `createCategory`, …).

---

## Gruppo `project`

| Sottocomando | HTTP | Path OpenAPI | Opzioni CLI |
|--------------|------|----------------|-------------|
| `list` | GET | `/webrobot/api/projects` | nessuna |
| `add` | POST | `/webrobot/api/projects` | `-n` / `--name` (obbl.), `-d` / `--description` (obbl.) — body `JobProjectDto` |
| `get` | GET | `/webrobot/api/projects/id/{projectId}` | `-i` / `--projectId` (obbl.); alias `-n` |
| `update` | PUT | `/webrobot/api/projects/id/{projectId}` | `-i` / `--id` (obbl.), `-n` / `--name` (obbl.), `-d` / `--description` (obbl.) |
| `delete` | DELETE | `/webrobot/api/projects/id/{projectId}` | `-i` / `--projectId` (obbl.) |
| `schedule-get` | GET | `/webrobot/api/projects/id/{projectId}/schedule` | `-i` / `--projectId` (obbl.) |
| `schedule-set` | PUT | `/webrobot/api/projects/id/{projectId}/schedule` | `-i` (obbl.); opz. `-j`/`--jobId`, `-c`/`--cron`, `-e`/`--enabled`, `-z`/`--timezone`, `--execution-json` (oggetto JSON), **`--elastic-browser-vm-count`**, **`--elastic-browser-cloud-credential-id`**, **`--elastic-browser-infra-provider`**, **`--use-ephemeral-elastic-browser-vms`** — body `ProjectScheduleRequest`; i flag `elastic-browser-*` e `use-ephemeral-*` vengono **mergiati** in `executionRequestJson` (come `elasticBrowserVmCount` nel POST execute lato Jersey). Se manca `executionMode` viene impostato `SCHEDULED`. |

**Verifica OpenAPI:** `getAllProjects`, `createProject`, `getProject`, `updateProject`, `deleteProject`, `getProjectSchedule`, `setProjectSchedule` sui path indicati — **allineato** a Jersey (`ProjectApiV10`).

---

## Gruppo `category`

Allineato a **`CategoryApiV10`** (`/webrobot/api/categories`) e OpenAPI `JobCategoryDto`.

| Sottocomando | HTTP | Path | Opzioni CLI |
|--------------|------|------|-------------|
| `list` | GET | `/webrobot/api/categories` | nessuna |
| `get` | GET | `/webrobot/api/categories/id/{categoryId}` | `-i` / `--categoryId` (obbl.) |
| `getbyname` | GET | `/webrobot/api/categories/{categoryName}` | `-n` / `--name` (obbl.) |
| `add` | POST | `/webrobot/api/categories` | `-n` / `--name` (obbl.); opz. `-d`/`--description`, `--icon`, `-e`/`--enabled` (default `true`) |
| `update` | PUT | `/webrobot/api/categories/id/{categoryId}` | `-i` (obbl.), `-n` (obbl.); opz. `-d`, `--icon`, `-e` |
| `delete` | DELETE | `/webrobot/api/categories/id/{categoryId}` | `-i` (obbl.) |

**Nota:** l’endpoint di test `GET /webrobot/api/categories/test` non è esposto dalla CLI.

---

## Gruppo `agent`

Il path API usa **`{categoryId}`** (Jersey: `AgentApiV10`). Nella CLI: **`-c` / `--categoryId`** (preferito); restano alias **`-p` / `--projectId`** per compatibilità con script storici (**non** sono l’id del progetto ETL, salvo casi in cui coincidano numericamente in DB).

| Sottocomando | HTTP | Path OpenAPI | Opzioni CLI |
|--------------|------|----------------|-------------|
| `list` | GET | `/webrobot/api/agents/{categoryId}` | `-c` / `--categoryId` o `-p` / `--projectId` (obbl.) |
| `get` | GET | `/webrobot/api/agents/{categoryId}/{agentId}` | `-c` o `-p`, `-i` / `--id` agent (obbl.) |
| `getfromname` | GET | `/webrobot/api/agents/{categoryId}/name/{agentName}` | `-c` o `-p`, `-n` / `--name` (obbl.) |
| `add` | POST | `/webrobot/api/agents` | `-n`, `-d` (obbl.), `-c` o `-p` (obbl.); opz. **`-f` / `--codeFile`** → `AgentDto.code` |
| `update` | PUT | `/webrobot/api/agents/{categoryId}/{agentId}` | `-c` o `-p`, `-i`, `-n`, `-d` (obbl.); opz. **`-f` / `--codeFile`** |
| `delete` | DELETE | `/webrobot/api/agents/{agentId}` | `-i` / `--id` = solo agent id (OpenAPI `deleteAgent`) |

**Breaking change (rispetto a versioni molto vecchie della CLI):** il file codice agent non usa più lo short `-c` (ora riservato a **category**); usare **`-f` / `--codeFile`**.

**Verifica OpenAPI:** `getAllAgents`, `getAgent`, `getAgentFromName`, `createAgent`, `updateAgent`, `deleteAgent` — **allineato** a Jersey (`AgentApiV10`).

---

## Gruppo `job`

Tutti i job sono sotto progetto: `/webrobot/api/projects/id/{projectId}/jobs/...`.

| Sottocomando | HTTP | Path OpenAPI | Opzioni CLI |
|--------------|------|----------------|-------------|
| `list` | GET | `.../projects/id/{projectId}/jobs` | `-p` / `--projectId` (obbl.) |
| `add` | POST | `.../jobs` | `-p`, `-n`, `-a`, `-i` / `--inputDatasetId` (obbl.); opz. `-d`, **`--cloud-credential-id`**, **`--job-type`** (`BATCH`\|`STREAMING`) — body `JobDto` |
| `update` | PUT | `.../jobs/{jobId}` | `-p`, `-j`, `-n` (obbl.); opz. `-d`, **`-a`/`--agentId`**, **`-i`/`--inputDatasetId`**, **`--cloud-credential-id`**, **`-e`/`--enabled`**, **`--job-type`** — la CLI fa **GET** del job poi **merge** nel JSON (non azzera `enabled`/relazioni se omessi). |
| `delete` | DELETE | `.../jobs/{jobId}` | `-p`, `-j` (obbl.) |
| `execute` | POST | `.../jobs/{jobId}/execute` | `-p`, `-j` (obbl.); opz. `-b` / `--bodyJson` (default `{}` oggetto JSON) |
| `stop` | POST | `.../jobs/{jobId}/stop` | `-p`, `-j` (obbl.) — body `{}` |
| `logs` | GET | `.../jobs/{jobId}/logs` | `-p`, `-j` (obbl.); opz. **`--task-id`**, **`--pod-type`**, **`--executor-index`**, **`--pod-name`**, **`--tail`** (query OpenAPI) |

**Verifica OpenAPI:** `getProjectJobs`, `addJobToProject`, `updateJob`, `removeJobFromProject`, `executeJob`, stop su `.../stop`, `getJobLogs` — **allineato** su metodo e path.

**Query logs:** allineate allo spec OpenAPI per `getJobLogs`.

---

## Gruppo `dataset`

| Sottocomando | HTTP | Path OpenAPI | Opzioni CLI |
|--------------|------|----------------|-------------|
| `list` | GET | `/webrobot/api/datasets` | Opz. query: `-t`/`--type`, `--indexed`, `-f`/`--format` (solo se valorizzati) |
| `get` | GET | `/webrobot/api/datasets/{datasetId}` | `-d` / `--datasetId` (obbl.) |
| `add` | POST | `/webrobot/api/datasets` | `-n` / `--name` (obbl.); opz. `-d` / `--description` |
| `delete` | DELETE | `/webrobot/api/datasets/{datasetId}` | `-d` / `--datasetId` (obbl.) |

**Verifica OpenAPI:** `getAllDatasets_1`, `getDataset_1`, `createDataset`, `deleteDataset_1` — **allineato**.

**Gap:** lo spec include anche **PUT** `updateDataset` su `/webrobot/api/datasets/{datasetId}` e altri path (`/fields`, `/index`, `/upload`, `/query`, …). La CLI **non** espone `dataset update` né upload/query.

---

## Gruppo `package` (import / export)

| Sottocomando | Flusso CLI | Path OpenAPI coinvolti |
|--------------|------------|-------------------------|
| `exportproject` | GET → URL nel JSON → download file | GET `/webrobot/api/package/export/id/{projectId}` (`start_export_project`) |
| `exportall` | GET → URL → download | GET `/webrobot/api/package/export/all` (`start_export_All`) |
| `importproject` | GET URL upload → **PUT** file zip su URL presigned → GET import | GET `/webrobot/api/package/upload` (`getUrlUpload`); GET `/webrobot/api/package/import/id/{projectId}` (`start_import_project`) |
| `importall` | stesso upload → GET import all | GET `/webrobot/api/package/upload`; GET `/webrobot/api/package/import/all` (`start_import_All`) |

**Opzioni**

| Sottocomando | Opzioni |
|--------------|---------|
| `exportproject` | `-f` / `--folder` percorso destinazione zip (obbl.), `-p` / `--projectId` (obbl.) |
| `exportall` | `-f` (obbl.) |
| `importproject` | `-z` / `--zip` file sorgente (obbl.), `-p` (obbl.) |
| `importall` | `-z` (obbl.) |

**Verifica OpenAPI:** path e metodi corrispondono. L’URL di download/upload è ricavato dal JSON con `JsonCliUtil.extractDownloadUrl` (chiavi tipo `result`, `url`, `stringResult.result`, …): se il backend cambia forma della risposta, va aggiornata quella logica.

**Gap:** lo spec definisce anche import/export per organizzazione, POST con `ImportOptionsDto` / `ExportOptionsDto`, ecc. La CLI copre solo i flussi sopra.

---

## Riferimento tecnico (implementazione)

- Client HTTP: `eu.webrobot.openapi.client.ApiClient` tramite `WebRobot.Cli.Sdk.openapi.OpenApiSdkAdapter` (`BaseSubCommand`).
- Molte risposte OpenAPI sono senza schema esplicito (`application/json: {}`); il codice usa `OpenApiHttp` + `TypeReference<JsonNode>` per deserializzare (vedi `OpenApiHttp.scala`).
- Download zip: `WebRobot.Cli.Sdk.Utils.Utils.downloadFile`; upload presigned: `BaseSubCommand.uploadFile` (HTTP PUT stream).

---

## Riepilogo allineamento

| Area | Stato |
|------|--------|
| Path e verbi HTTP dei comandi documentati | Coerenti con `openapi-sanitized.json` del **WebRobot.Sdk** e con Jersey dove applicabile. |
| `category` | Aggiunto; prima gli agent erano l’unico modo indiretto di ragionare sulla categoria (`-p` fuorviante). |
| `project` vs `category` | Documentato: progetto aggrega job; categoria aggrega agent; schedule progetto = **un** `jobId` attivo (vedi `ProjectServiceImpl`). |
| `project get` | `-i`/`--projectId` preferito; alias `-n` per compatibilità. |
| `job logs` | Path + query opzionali come OpenAPI. |
| `dataset` | CRUD parziale (manca update e sotto-risorse). |
| `package` | Sottoinsieme degli endpoint package dello spec. |

Per l’elenco completo degli endpoint generati, aprire lo stesso file OpenAPI o il `DefaultApi` generato nel modulo SDK.
