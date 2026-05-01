#!/usr/bin/env bash
# Installa la CLI WebRobot (uber-jar) da WEBROBOT_CLI_JAR_URL e crea il wrapper `webrobot` sotto PREFIX.
# Installa anche i prerequisiti Python per il wizard probe (browser-use + camoufox).
#
# Obbligatorio:
#   WEBROBOT_CLI_JAR_URL   URL HTTP(S) diretto al .jar (curl).
#   URL pubblico senza token: asset GitHub Release con nome fisso, es.
#   https://github.com/WebRobot-Ltd/WebRobot-CLI/releases/latest/download/webrobot-cli-uber.jar
#   (pubblicazione: workflow .github/workflows/release-cli-jar.yml su GitHub Actions)
#
# Opzionale:
#   PREFIX                        default: $HOME/.local  (jar in PREFIX/share/webrobot-cli, bin in PREFIX/bin)
#   WEBROBOT_JAVA                 se impostato, prova per primo questo eseguibile (path assoluto consigliato)
#   WEBROBOT_AUTO_JRE             default: true — se true e non c’è una JVM idonea, scarica Eclipse Temurin JRE
#                                 sotto PREFIX/share/webrobot-cli/jre (senza sudo)
#   WEBROBOT_JRE_FEATURE_VERSION  major Temurin da installare se serve (default: 17)
#   WEBROBOT_JVM_MIN_MAJOR        versione major minima accettata (default: 8)
#   WEBROBOT_INSTALL_BROWSER_DEPS default: true — installa browser-use, camoufox e provider LLM Python
#   WEBROBOT_PYTHON               interprete Python da usare (default: python3)
#   WEBROBOT_PYTHON_MIN_MINOR     versione minore minima Python 3.x richiesta (default: 11)
#   WEBROBOT_SKIP_CAMOUFOX_FETCH  default: false — se true salta il download del binario camoufox (~100 MB)
#
# Dipendenze host: curl, tar. (gzip incluso in tar -xzf)
#
set -euo pipefail

usage() {
  sed -n '1,17p' "$0" | tail -n +2
  exit 0
}

case "${1:-}" in -h|--help|help) usage ;; esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOCAL_JAR="$(ls "${SCRIPT_DIR}/../target/webrobot-cli-"*"-uber.jar" 2>/dev/null | tail -1)"
WEBROBOT_CLI_JAR_URL="${WEBROBOT_CLI_JAR_URL:-}"
if [[ -z "$WEBROBOT_CLI_JAR_URL" ]]; then
  if [[ -n "$LOCAL_JAR" && -f "$LOCAL_JAR" ]]; then
    echo "WEBROBOT_CLI_JAR_URL non impostato — uso JAR locale: $LOCAL_JAR"
    WEBROBOT_CLI_JAR_URL="file://${LOCAL_JAR}"
  else
    echo "Errore: WEBROBOT_CLI_JAR_URL non impostato e nessun JAR locale trovato in target/." >&2
    echo "Compila prima con Maven oppure imposta WEBROBOT_CLI_JAR_URL." >&2
    exit 1
  fi
fi

PREFIX="${PREFIX:-$HOME/.local}"
INSTALL_DIR="${PREFIX}/share/webrobot-cli"
BIN_DIR="${PREFIX}/bin"
JRE_HOME="${INSTALL_DIR}/jre"
JAR_PATH="${INSTALL_DIR}/webrobot-cli.jar"

WEBROBOT_AUTO_JRE="${WEBROBOT_AUTO_JRE:-true}"
WEBROBOT_JRE_FEATURE_VERSION="${WEBROBOT_JRE_FEATURE_VERSION:-17}"
WEBROBOT_JVM_MIN_MAJOR="${WEBROBOT_JVM_MIN_MAJOR:-8}"
WEBROBOT_INSTALL_BROWSER_DEPS="${WEBROBOT_INSTALL_BROWSER_DEPS:-true}"
WEBROBOT_PYTHON="${WEBROBOT_PYTHON:-python3}"
WEBROBOT_PYTHON_MIN_MINOR="${WEBROBOT_PYTHON_MIN_MINOR:-11}"
WEBROBOT_SKIP_CAMOUFOX_FETCH="${WEBROBOT_SKIP_CAMOUFOX_FETCH:-false}"

command -v curl >/dev/null 2>&1 || { echo "curl non trovato" >&2; exit 1; }
command -v tar >/dev/null 2>&1 || { echo "tar non trovato" >&2; exit 1; }

# Estrae la major Java da "java -version" (1.8.x -> 8, 17.x -> 17).
java_major_version() {
  local java_exe="$1"
  local line
  line="$("$java_exe" -version 2>&1 | head -n1)" || return 1
  if [[ "$line" =~ version\ \"1\.([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi
  if [[ "$line" =~ version\ \"([0-9]+) ]]; then
    echo "${BASH_REMATCH[1]}"
    return 0
  fi
  return 1
}

java_meets_minimum() {
  local java_exe="$1"
  local maj
  maj="$(java_major_version "$java_exe")" || return 1
  [[ "$maj" -ge "$WEBROBOT_JVM_MIN_MAJOR" ]]
}

pick_java() {
  local candidates=()
  [[ -n "${WEBROBOT_JAVA:-}" ]] && candidates+=("$WEBROBOT_JAVA")
  candidates+=("java")
  [[ -x "${JRE_HOME}/bin/java" ]] && candidates+=("${JRE_HOME}/bin/java")

  local c
  for c in "${candidates[@]}"; do
    [[ -z "$c" ]] && continue
    if command -v "$c" >/dev/null 2>&1 || [[ -x "$c" ]]; then
      if java_meets_minimum "$c"; then
        printf '%s' "$c"
        return 0
      fi
    fi
  done
  return 1
}

install_temurin_jre() {
  local os arch url tmpd archive top
  case "$(uname -s)" in
    Linux)  os=linux ;;
    Darwin) os=mac ;;
    *) echo "Sistema operativo non supportato per install JRE automatica: $(uname -s)" >&2; return 1 ;;
  esac
  case "$(uname -m)" in
    x86_64|amd64)   arch=x64 ;;
    aarch64|arm64)  arch=aarch64 ;;
    *) echo "Architettura non supportata per install JRE automatica: $(uname -m)" >&2; return 1 ;;
  esac

  url="https://api.adoptium.net/v3/binary/latest/${WEBROBOT_JRE_FEATURE_VERSION}/ga/${os}/${arch}/jre/hotspot/normal/eclipse"
  echo "Download JRE Temurin ${WEBROBOT_JRE_FEATURE_VERSION} (${os}/${arch}) in ${JRE_HOME}..."
  tmpd="$(mktemp -d)"
  archive="${tmpd}/temurin-jre.tar.gz"
  if ! curl -fsSL "$url" -o "$archive"; then
    rm -rf "${tmpd}"
    echo "Download Temurin fallito (rete o URL). Imposta WEBROBOT_JAVA o riprova." >&2
    return 1
  fi
  if ! tar -xzf "$archive" -C "$tmpd"; then
    rm -rf "${tmpd}"
    echo "Estrazione archivio JRE fallita." >&2
    return 1
  fi
  top="$(find "$tmpd" -maxdepth 1 -mindepth 1 -type d ! -name '.*' | head -1)"
  if [[ -z "$top" || ! -x "${top}/bin/java" ]]; then
    rm -rf "${tmpd}"
    echo "Archivio JRE inatteso dopo estrazione." >&2
    return 1
  fi
  rm -rf "${JRE_HOME}"
  mkdir -p "${INSTALL_DIR}"
  mv "$top" "${JRE_HOME}"
  rm -rf "${tmpd}"
  [[ -x "${JRE_HOME}/bin/java" ]] || { echo "JRE installata ma java non eseguibile: ${JRE_HOME}/bin/java" >&2; return 1; }
  echo "JRE installata: ${JRE_HOME}"
}

JAVA_BIN=""
if picked="$(pick_java 2>/dev/null)"; then
  JAVA_BIN="$picked"
else
  if [[ "${WEBROBOT_AUTO_JRE}" == "true" ]] || [[ "${WEBROBOT_AUTO_JRE}" == "1" ]] || [[ "${WEBROBOT_AUTO_JRE}" == "yes" ]]; then
    install_temurin_jre
    JAVA_BIN="${JRE_HOME}/bin/java"
    java_meets_minimum "$JAVA_BIN" || { echo "JRE installata ma versione < ${WEBROBOT_JVM_MIN_MAJOR}" >&2; exit 1; }
  else
    echo "Nessuna JVM >= ${WEBROBOT_JVM_MIN_MAJOR} trovata. Installa Java, imposta WEBROBOT_JAVA, oppure WEBROBOT_AUTO_JRE=true." >&2
    exit 1
  fi
fi

# Path assoluto per il wrapper (evita ambiguità se PATH cambia).
JAVA_ABS="$(command -v "$JAVA_BIN" 2>/dev/null || true)"
if [[ -z "$JAVA_ABS" ]]; then
  JAVA_ABS="$JAVA_BIN"
fi
if [[ "${JAVA_ABS}" != /* ]]; then
  echo "WEBROBOT_JAVA deve essere un path assoluto se non è su PATH: $JAVA_ABS" >&2
  exit 1
fi

TMP="$(mktemp)"
cleanup_tmp() { rm -f "${TMP}"; }
trap cleanup_tmp EXIT

echo "Download CLI: $WEBROBOT_CLI_JAR_URL"
if [[ "$WEBROBOT_CLI_JAR_URL" == file://* ]]; then
  cp "${WEBROBOT_CLI_JAR_URL#file://}" "$TMP"
else
  curl -fsSL "$WEBROBOT_CLI_JAR_URL" -o "$TMP"
fi

mkdir -p "$INSTALL_DIR" "$BIN_DIR"
install -m0644 "$TMP" "$JAR_PATH"

cat > "${BIN_DIR}/webrobot" <<EOF
#!/usr/bin/env bash
exec "${JAVA_ABS}" -jar "${JAR_PATH}" webrobot "\$@"
EOF
chmod 0755 "${BIN_DIR}/webrobot"

trap - EXIT
rm -f "$TMP"

# ── Python / browser-use / camoufox ──────────────────────────────────────────

python_minor_version() {
  local py="$1"
  local out
  out="$("$py" -c 'import sys; print(sys.version_info.minor)' 2>/dev/null)" || return 1
  echo "$out"
}

python_meets_minimum() {
  local py="$1"
  local minor
  minor="$(python_minor_version "$py")" || return 1
  [[ "$minor" -ge "$WEBROBOT_PYTHON_MIN_MINOR" ]]
}

pick_python() {
  local candidates=("$WEBROBOT_PYTHON" python3 python3.13 python3.12 python3.11)
  local c
  for c in "${candidates[@]}"; do
    [[ -z "$c" ]] && continue
    if command -v "$c" >/dev/null 2>&1; then
      if python_meets_minimum "$c"; then
        printf '%s' "$c"
        return 0
      fi
    fi
  done
  return 1
}

install_browser_deps() {
  local py="$1"
  local pip_packages=(
    "browser-use"
    "camoufox[geoip]"
    "langchain-anthropic"
    "langchain-openai"
    "langchain-groq"
  )

  echo ""
  echo "▶ [Python] Installazione browser-use + camoufox (wizard probe)..."
  if ! "$py" -m pip install --upgrade "${pip_packages[@]}"; then
    echo "⚠ Installazione pacchetti pip fallita. Il wizard probe non sarà disponibile." >&2
    echo "  Riprova manualmente: $py -m pip install browser-use 'camoufox[geoip]'" >&2
    return 0
  fi
  echo "✓ Pacchetti Python installati"

  if [[ "${WEBROBOT_SKIP_CAMOUFOX_FETCH}" == "true" ]] || [[ "${WEBROBOT_SKIP_CAMOUFOX_FETCH}" == "1" ]]; then
    echo "  (download binario camoufox saltato — WEBROBOT_SKIP_CAMOUFOX_FETCH=true)"
    echo "  Scarica in seguito con: $py -m camoufox fetch"
    return 0
  fi

  echo "▶ [Python] Download binario camoufox (~100 MB, Firefox anti-detection)..."
  if ! "$py" -m camoufox fetch; then
    echo "⚠ Download binario camoufox fallito. Scarica in seguito con: $py -m camoufox fetch" >&2
  else
    echo "✓ Binario camoufox scaricato"
  fi
}

PYTHON_BIN=""
if [[ "${WEBROBOT_INSTALL_BROWSER_DEPS}" == "true" ]] || [[ "${WEBROBOT_INSTALL_BROWSER_DEPS}" == "1" ]]; then
  if PYTHON_BIN="$(pick_python 2>/dev/null)"; then
    install_browser_deps "$PYTHON_BIN"
  else
    echo ""
    echo "⚠ Python >= 3.${WEBROBOT_PYTHON_MIN_MINOR} non trovato — browser-use e camoufox non installati." >&2
    echo "  Installa Python 3.11+ e riesegui, oppure imposta WEBROBOT_PYTHON=/path/to/python3." >&2
    echo "  Per saltare: WEBROBOT_INSTALL_BROWSER_DEPS=false" >&2
  fi
fi

# ── riepilogo ─────────────────────────────────────────────────────────────────

echo ""
echo "Installato:"
echo "  Java:   ${JAVA_ABS}"
echo "  JAR:    ${JAR_PATH}"
echo "  Bin:    ${BIN_DIR}/webrobot"
[[ -n "$PYTHON_BIN" ]] && echo "  Python: ${PYTHON_BIN} (browser-use + camoufox)"
echo ""
echo "Aggiungi al PATH se serve: export PATH=\"${BIN_DIR}:\$PATH\""
echo "Test: ${BIN_DIR}/webrobot --help"
