#!/usr/bin/env sh
# Rebuild the mod and drop it into the local summerBuddies client (PrismLauncher) for voice testing.
# Usage:  ./dev-deploy-client.sh
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLIENT_MODS="$HOME/.local/share/PrismLauncher/instances/summerBuddies/.minecraft/mods"

echo ">> building..."
"$PROJECT_DIR/gradlew" -p "$PROJECT_DIR" build --no-daemon -q

JAR="$(ls -t "$PROJECT_DIR"/build/libs/voicetrans-*.jar | head -1)"
echo ">> deploying $(basename "$JAR") -> $CLIENT_MODS"
rm -f "$CLIENT_MODS"/voicetrans-*.jar
cp "$JAR" "$CLIENT_MODS"/

echo ">> done. Relaunch the summerBuddies instance in PrismLauncher."
echo ">> remember the sidecar must be running:  cd sidecar && .venv/bin/uvicorn app:app --port 8200"
