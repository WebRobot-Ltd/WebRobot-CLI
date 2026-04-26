#!/usr/bin/env bash
# Installa la CLI WebRobot (uber-jar) da un URL HTTP(S) e crea il wrapper `webrobot` nel PREFIX.
#
# Obbligatorio: WEBROBOT_CLI_JAR_URL — URL diretto al file .jar (curl senza auth interattiva).
#
# Esempi di origine del JAR:
#   - GitHub Release: asset pubblico, es.
#     https://github.com/WebRobot-Ltd/WebRobot-CLI/releases/download/<tag>/webrobot-cli-uber.jar
#   - MinIO / S3 / CDN: URL pubblico o presigned
#   - Jenkins: .../lastSuccessfulBuild/artifact/target/webrobot-cli-uber.jar (spesso serve token)
#
# Opzionale:
#   PREFIX          default: $HOME/.local  (bin in $PREFIX/bin, jar in $PREFIX/share/webrobot-cli)
#   WEBROBOT_JAVA   default: java (deve essere su PATH, es. Java 8+)
#
set -euo pipefail

usage() {
  sed -n '1,22p' "$0" | tail -n +2
  exit 0
}

case "${1:-}" in -h|--help|help) usage ;; esac

: "${WEBROBOT_CLI_JAR_URL:?Imposta WEBROBOT_CLI_JAR_URL con l URL del jar uber (vedi --help)}"

PREFIX="${PREFIX:-$HOME/.local}"
JAVA_BIN="${WEBROBOT_JAVA:-java}"
INSTALL_DIR="${PREFIX}/share/webrobot-cli"
BIN_DIR="${PREFIX}/bin"
JAR_PATH="${INSTALL_DIR}/webrobot-cli.jar"

command -v curl >/dev/null 2>&1 || { echo "curl non trovato" >&2; exit 1; }
command -v "$JAVA_BIN" >/dev/null 2>&1 || { echo "Java non trovato ($JAVA_BIN). Imposta WEBROBOT_JAVA o PATH." >&2; exit 1; }

TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

echo "Download: $WEBROBOT_CLI_JAR_URL"
curl -fsSL "$WEBROBOT_CLI_JAR_URL" -o "$TMP"

mkdir -p "$INSTALL_DIR" "$BIN_DIR"
install -m0644 "$TMP" "$JAR_PATH"

cat > "${BIN_DIR}/webrobot" <<EOF
#!/usr/bin/env bash
exec "$JAVA_BIN" -jar "$JAR_PATH" webrobot "\$@"
EOF
chmod 0755 "${BIN_DIR}/webrobot"

trap - EXIT
rm -f "$TMP"

echo "Installato:"
echo "  JAR:  $JAR_PATH"
echo "  Bin:  ${BIN_DIR}/webrobot"
echo "Aggiungi al PATH se serve: export PATH=\"${BIN_DIR}:\$PATH\""
echo "Test: ${BIN_DIR}/webrobot --help"
