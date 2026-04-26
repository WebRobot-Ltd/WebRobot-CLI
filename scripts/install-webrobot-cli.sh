#!/usr/bin/env bash
# Installa la CLI WebRobot (uber-jar) da WEBROBOT_CLI_JAR_URL e crea il wrapper `webrobot` sotto PREFIX.
#
# Obbligatorio:
#   WEBROBOT_CLI_JAR_URL   URL HTTP(S) diretto al .jar (curl; per token Bearer usa header custom o URL firmato).
#
# Opzionale:
#   PREFIX                     default: $HOME/.local  (jar in PREFIX/share/webrobot-cli, bin in PREFIX/bin)
#   WEBROBOT_JAVA              se impostato, prova per primo questo eseguibile (path assoluto consigliato)
#   WEBROBOT_AUTO_JRE          default: true — se true e non c’è una JVM idonea, scarica Eclipse Temurin JRE
#                              sotto PREFIX/share/webrobot-cli/jre (senza sudo)
#   WEBROBOT_JRE_FEATURE_VERSION  major Temurin da installare se serve (default: 17)
#   WEBROBOT_JVM_MIN_MAJOR     versione major minima accettata (default: 8)
#
# Dipendenze host: curl, tar. (gzip incluso in tar -xzf)
#
set -euo pipefail

usage() {
  sed -n '1,17p' "$0" | tail -n +2
  exit 0
}

case "${1:-}" in -h|--help|help) usage ;; esac

: "${WEBROBOT_CLI_JAR_URL:?Imposta WEBROBOT_CLI_JAR_URL con URL del jar uber (vedi --help)}"

PREFIX="${PREFIX:-$HOME/.local}"
INSTALL_DIR="${PREFIX}/share/webrobot-cli"
BIN_DIR="${PREFIX}/bin"
JRE_HOME="${INSTALL_DIR}/jre"
JAR_PATH="${INSTALL_DIR}/webrobot-cli.jar"

WEBROBOT_AUTO_JRE="${WEBROBOT_AUTO_JRE:-true}"
WEBROBOT_JRE_FEATURE_VERSION="${WEBROBOT_JRE_FEATURE_VERSION:-17}"
WEBROBOT_JVM_MIN_MAJOR="${WEBROBOT_JVM_MIN_MAJOR:-8}"

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
curl -fsSL "$WEBROBOT_CLI_JAR_URL" -o "$TMP"

mkdir -p "$INSTALL_DIR" "$BIN_DIR"
install -m0644 "$TMP" "$JAR_PATH"

cat > "${BIN_DIR}/webrobot" <<EOF
#!/usr/bin/env bash
exec "${JAVA_ABS}" -jar "${JAR_PATH}" webrobot "\$@"
EOF
chmod 0755 "${BIN_DIR}/webrobot"

trap - EXIT
rm -f "$TMP"

echo "Installato:"
echo "  Java: ${JAVA_ABS}"
echo "  JAR:  ${JAR_PATH}"
echo "  Bin:  ${BIN_DIR}/webrobot"
echo "Aggiungi al PATH se serve: export PATH=\"${BIN_DIR}:\$PATH\""
echo "Test: ${BIN_DIR}/webrobot --help"
