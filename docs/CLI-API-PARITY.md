# Parità CLI ↔ API (OpenAPI + Jersey)

Riferimento: `WebRobot.Sdk/.../openapi-sanitized.json` e implementazioni Jersey sotto `rest-api/.../org.webrobot.eu.apis.jersey`.

## Riepilogo esito verifica

| Gruppo | Allineamento path/verbo | Body / query | Note |
|--------|-------------------------|--------------|------|
| `project` | OK | `JobProjectDto`: CLI invia `name`, `description` (add/update). `ProjectScheduleRequest`: allineato; `jobId` richiesto lato Jersey se `enabled=true` (descrizione CLI aggiornata). `executionRequestJson` + flag `--elastic-browser-*` mergiati. | `project get`: ora `-i`/`--projectId` e alias `-n`. |
| `category` | OK | `JobCategoryDto`: campi principali; mancano in CLI `visibility`, `agentIds` (opzionali nello schema). | |
| `agent` | OK | `AgentDto`: CLI copre CRUD base; molti campi schema (es. `type`, `executionMode`, `enabled`, `config`, …) non esposti — ok per uso minimo, estendere se serve. | Delete senza `categoryId` come spec. |
| `job` | OK | `JobDto`: add con `cloudCredentialId`, `jobType` opzionali. **update**: GET + merge JSON (allineato a `JobServiceImpl.updateJob` partial update, evita `enabled` implicito). Opzioni `agentId`, `inputDatasetId`, `cloudCredentialId`, `enabled`, `jobType`. `logs`: query `taskId`, `podType`, `executorIndex`, `podName`, `tail` come OpenAPI. | `execute`: body libero `-b`; stessi campi execute (es. `elasticBrowserVmCount`) possono essere aggiunti come flag dedicati in un secondo momento come per `schedule-set`. |
| `dataset` | OK | `DatasetDto` molto più ricco dello schema; CLI solo `name`/`description` su add. | PUT `updateDataset` non in CLI. |
| `package` | OK | Flussi export/import come spec. | |

## Dettaglio per schema OpenAPI

### `JobProjectDto` (project add/update)

- CLI: `name`, `description`.  
- Schema include anche `id`, `cronSchedule`, `enabled`, `jobIds`, … — non inviati da add/update progetto base: coerente con uso CRUD semplice.

### `ProjectScheduleRequest`

- Campi: `cronSchedule`, `enabled`, `timezone`, `jobId`, `executionRequestJson`.  
- CLI: tutti coperti + merge flag elastic browser nel JSON string (coerente con `ProjectServiceImpl`).

### `JobDto`

- Add: `projectId`, `name`, `description`, `agentId`, `inputDatasetId` + opzionali `cloudCredentialId`, `jobType` (`BATCH`/`STREAMING`).  
- Update: merge da GET; campi opzionali sovrascrivono solo se passati. Allineato al comportamento “update only non-null” lato mapper/servizio, evitando PUT minimal che potevano alterare `enabled`.

### `AgentDto`

- Estensioni future: `--type`, `--execution-mode`, `--enabled`, file `config`, ecc., se i flussi ETL li richiedono sempre da CLI.

### `DatasetDto`

- Estensioni: `datasetType`, `sourceUrl`, `storagePath`, … per pipeline dati.

### `JobCategoryDto`

- Estensioni: `--visibility`, lista `agentIds` se l’API li usa in create/update.

---

## Helper di alto livello (proposta, non implementati)

Obiettivo: comandi che esprimono la **pipeline ETL** (stadi semantici) invece di sequenze manuali di REST.

Possibili direzioni (da definire con contratto unico, es. YAML o JSON):

1. **`webrobot pipeline validate --file pipeline.yaml`**  
   Controlla ordine stadi (es. ingest → transform → export), riferimenti a `categoryId` / `projectId` / dataset, e coerenza con entitlements (es. `elasticBrowserVmCount` vs piano).

2. **`webrobot pipeline apply --file pipeline.yaml`**  
   Orchestrazione: crea/aggiorna category, agent, dataset, project, job, schedule in ordine con idempotenza (nomi → lookup GET esistente).

3. **`webrobot etl init`**  
   Template minimi allineati al modello dominio documentato in `CLI-COMMANDS.md` (category ≠ project, schedule = un jobId attivo).

4. Integrazione **stage registry** (se esiste già nel backend ETL plugin): validare nomi stage contro catalogo versionato.

Implementazione consigliata: modulo separato o sottocomando `webrobot compose` che non duplica logica Jersey ma chiama gli stessi path già coperti dalla CLI “bassa”.

---

## Storia modifiche recenti (parità)

- `job update`: merge GET→PUT.  
- `job logs`: query OpenAPI.  
- `job add`: `cloudCredentialId`, `jobType`.  
- `project get`: flag `-i` + alias `-n`.  
- `schedule-set`: testo su `jobId` obbligatorio se enabled; flag execute body elastic browser.
