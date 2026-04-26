#!/usr/bin/env bash
#
# Demo end-to-end: categoria → agent (file codice) → dataset input (metadata + upload file)
# → progetto → job → execute → dettaglio job (outputDatasetId) → query SQL esempio.
#
# Prerequisiti: bash, jq, java 8+, uber-jar CLI, config.cfg (vedi RunWebRobotCli).
#
#   export WEBROBOT_CLI_JAR=/path/to/org.webrobot.eu.spark.job-0.3-uber.jar
#   cd /path/to/webrobot-cli && ./scripts/demo-etl-pipeline.sh
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLI_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "$CLI_ROOT"

JAR="${WEBROBOT_CLI_JAR:-${CLI_ROOT}/target/org.webrobot.eu.spark.job-0.3-uber.jar}"

if [[ ! -f "$JAR" ]]; then
  echo "JAR non trovato: $JAR" >&2
  echo "Esegui: mvn package -DskipTests  oppure  export WEBROBOT_CLI_JAR=..." >&2
  exit 1
fi

if [[ ! -f "config.cfg" ]]; then
  echo "Serve config.cfg in $CLI_ROOT (api_endpoint, apikey o jwt)." >&2
  exit 1
fi

run_cli() { java -jar "$JAR" "$@"; }

# OK su stderr; comandi che stampano JSON lo fanno su stdout → adatto a jq.
json() { run_cli "$@" 2>/dev/null | jq .; }

SUFFIX="$(date +%s)"
DEMO_DIR="$(mktemp -d "/tmp/webrobot-demo-${SUFFIX}.XXXXXX")"
cleanup() { rm -rf "$DEMO_DIR"; }
trap cleanup EXIT

AGENT_CODE="${DEMO_DIR}/agent_stub.py"
cat >"$AGENT_CODE" <<'PY'
# Stub agent: sostituire con pipeline ETL reale (PySpark).
def run(spark, config):
    spark.range(1, 10).createOrReplaceTempView("demo")
    return "ok"
PY

INPUT_CSV="${DEMO_DIR}/input.csv"
cat >"$INPUT_CSV" <<'CSV'
id,name
1,alpha
2,beta
CSV

echo "=== 1) Categoria ==="
CAT_JSON=$(json category add -n "demo-cat-${SUFFIX}" -d "Categoria demo pipeline")
CATEGORY_ID=$(echo "$CAT_JSON" | jq -r '.id // empty')
[[ -n "$CATEGORY_ID" && "$CATEGORY_ID" != "null" ]] || { echo "$CAT_JSON" >&2; exit 1; }
echo "categoryId=$CATEGORY_ID"

echo "=== 2) Agent ==="
AG_JSON=$(json agent add -n "demo-agent-${SUFFIX}" -d "Agent demo" -c "$CATEGORY_ID" -f "$AGENT_CODE")
AGENT_ID=$(echo "$AG_JSON" | jq -r '.id // empty')
[[ -n "$AGENT_ID" && "$AGENT_ID" != "null" ]] || { echo "$AG_JSON" >&2; exit 1; }
echo "agentId=$AGENT_ID"

echo "=== 3a) Dataset input (metadata DB) ==="
DS_IN_JSON=$(json dataset add -n "demo-input-${SUFFIX}" -d "Input metadata")
INPUT_DATASET_ID=$(echo "$DS_IN_JSON" | jq -r '.id // empty')
[[ -n "$INPUT_DATASET_ID" && "$INPUT_DATASET_ID" != "null" ]] || { echo "$DS_IN_JSON" >&2; exit 1; }
echo "inputDatasetId=$INPUT_DATASET_ID"

echo "=== 3b) Upload CSV (MinIO /datasets/...) ==="
UP_JSON=$(json dataset upload -F "$INPUT_CSV" -t input -n "demo-upload-${SUFFIX}")
echo "$UP_JSON" | jq .
STORAGE_PATH=$(echo "$UP_JSON" | jq -r '.storagePath // empty')
echo "storagePath=$STORAGE_PATH"

echo "=== 4) Progetto ==="
PR_JSON=$(json project add -n "demo-project-${SUFFIX}" -d "Progetto demo")
PROJECT_ID=$(echo "$PR_JSON" | jq -r '.id // empty')
[[ -n "$PROJECT_ID" && "$PROJECT_ID" != "null" ]] || { echo "$PR_JSON" >&2; exit 1; }
echo "projectId=$PROJECT_ID"

echo "=== 5) Job ==="
JOB_JSON=$(json job add -p "$PROJECT_ID" -n "demo-job-${SUFFIX}" -d "Job demo" -a "$AGENT_ID" -i "$INPUT_DATASET_ID")
JOB_ID=$(echo "$JOB_JSON" | jq -r '.id // empty')
[[ -n "$JOB_ID" && "$JOB_ID" != "null" ]] || { echo "$JOB_JSON" >&2; exit 1; }
echo "jobId=$JOB_ID"

echo "=== 6) Execute ==="
EX_JSON=$(json job execute -p "$PROJECT_ID" -j "$JOB_ID" -b '{}')
echo "$EX_JSON" | jq .

echo "=== 7) Dettaglio job (outputDatasetId, stato, ...) ==="
JOB_DETAIL=$(json job get -p "$PROJECT_ID" -j "$JOB_ID")
echo "$JOB_DETAIL" | jq .
OUTPUT_DATASET_ID=$(echo "$JOB_DETAIL" | jq -r '.outputDatasetId // empty')

if [[ -n "$OUTPUT_DATASET_ID" && "$OUTPUT_DATASET_ID" != "null" ]]; then
  echo "outputDatasetId=$OUTPUT_DATASET_ID"
  echo "=== 8) Dataset output (GET) ==="
  json dataset get -d "$OUTPUT_DATASET_ID" | jq .
  echo "=== 9) Query SQL esempio (adatta tabelle al catalogo reale) ==="
  json dataset query -q "SELECT 1 AS probe" --catalog minio --schema default --limit 5 || true
else
  echo "(outputDatasetId assente finché il job non scrive output — controlla log e riesegui job get)" >&2
  echo "=== 9) Query SQL esempio (catalogo di default) ==="
  json dataset query -q "SELECT 1 AS probe" --catalog minio --schema default --limit 5 || true
fi

echo "=== 10) Log job (tail) ==="
json job logs -p "$PROJECT_ID" -j "$JOB_ID" --tail 50 || true

echo ""
echo "Variabili utili:"
echo "  CATEGORY_ID=$CATEGORY_ID  AGENT_ID=$AGENT_ID  INPUT_DATASET_ID=$INPUT_DATASET_ID"
echo "  PROJECT_ID=$PROJECT_ID  JOB_ID=$JOB_ID"
