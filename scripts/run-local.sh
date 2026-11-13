#!/usr/bin/env bash
#
# LocalMind Development Orchestrator & Local Stack Launcher
#
# Usage:
#   ./scripts/run-local.sh
#
# Starts Docker infrastructure, verifies Ollama models, and launches
# the Spring Boot backend and Next.js frontend in separate terminal windows.

set -Eeuo pipefail

ROOT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$ROOT_DIR/backend"
FRONTEND_DIR="$ROOT_DIR/frontend"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.yml"

OLLAMA_URL="http://127.0.0.1:11434"
CHAT_MODEL="gemma3:1b"
EMBEDDING_MODEL="nomic-embed-text"

# -----------------------------------------------------------------------------
# Logging and helpers
# -----------------------------------------------------------------------------
if [[ -t 1 ]]; then
  CYAN=$'\033[36m'; GREEN=$'\033[32m'; YELLOW=$'\033[33m'
  MAGENTA=$'\033[35m'; RED=$'\033[31m'; RESET=$'\033[0m'
else
  CYAN=''; GREEN=''; YELLOW=''; MAGENTA=''; RED=''; RESET=''
fi

write_header() {
  printf '\n%s=================================================================%s\n' "$CYAN" "$RESET"
  printf '%s  %s%s\n' "$CYAN" "$1" "$RESET"
  printf '%s=================================================================%s\n\n' "$CYAN" "$RESET"
}
success() { printf ' %s[OK]%s %s\n' "$GREEN" "$RESET" "$1"; }
info()    { printf ' %s[..]%s %s\n' "$YELLOW" "$RESET" "$1"; }
warning() { printf ' %s[WARN]%s %s\n' "$MAGENTA" "$RESET" "$1"; }
failure() { printf ' %s[ERR]%s %s\n' "$RED" "$RESET" "$1" >&2; }

die() { failure "$1"; exit 1; }

command_exists() { command -v "$1" >/dev/null 2>&1; }

port_in_use() {
  local host="$1" port="$2"
  if command_exists nc; then
    nc -z -w 1 "$host" "$port" >/dev/null 2>&1
  else
    (echo >/dev/tcp/"$host"/"$port") >/dev/null 2>&1
  fi
}

wait_for_port() {
  local host="$1" port="$2" service="$3" timeout="${4:-30}"
  local elapsed=0
  info "Verifying $service connectivity on port $port..."
  while ((elapsed < timeout)); do
    if port_in_use "$host" "$port"; then
      success "$service is reachable on port $port."
      return 0
    fi
    sleep 1
    ((elapsed+=1))
  done
  failure "Timed out waiting for $service on port $port."
  return 1
}

require_command() {
  command_exists "$1" || die "Required command '$1' was not found. Please install it and retry."
}

# Returns success if an installed Ollama model name starts with the requested
# model name. Python is used only to parse the Ollama /api/tags JSON response.
model_is_installed() {
  local requested="$1" json="$2"
  python3 - "$requested" "$json" <<'PY'
import json, sys
requested, payload = sys.argv[1], sys.argv[2]
try:
    models = json.loads(payload).get("models", [])
    names = [m.get("name", "") for m in models]
    sys.exit(0 if any(name.startswith(requested) for name in names) else 1)
except Exception:
    sys.exit(1)
PY
}

ensure_model() {
  local model="$1" label="$2" tags_json="$3"
  if model_is_installed "$model" "$tags_json"; then
    success "$label model '$model' is available."
  else
    info "$label model '$model' not found. Pulling via 'ollama pull $model'..."
    ollama pull "$model" || die "Failed to pull $label model '$model'."
    success "$label model '$model' successfully pulled."
  fi
}

# Start a command in a separate terminal when a supported terminal emulator is
# available. Otherwise run it in the background and write output to a log file.
launch_in_terminal() {
  local title="$1" workdir="$2" command="$3" log_file="$4"
  local escaped_dir
  escaped_dir="$(printf '%q' "$workdir")"
  local bash_cmd="cd $escaped_dir && echo '=== $title ===' && $command; exec bash"

  if [[ "$(uname -s)" == "Darwin" ]] && command_exists osascript; then
    osascript <<APPLESCRIPT
tell application "Terminal"
  activate
  do script "cd $escaped_dir; printf '\\n%s\\n' $(printf '%q' "$title"); $command"
end tell
APPLESCRIPT
    success "$title launched in a separate Terminal window."
  elif command_exists mintty; then
    mintty -t "$title" -e bash -lc "$bash_cmd" &
    success "$title launched in a dedicated Git Bash window."
  elif command_exists wt.exe; then
    wt.exe -w new --title "$title" bash -lc "$bash_cmd" >/dev/null 2>&1 &
    success "$title launched in a new Windows Terminal window."
  elif command_exists powershell.exe; then
    powershell.exe -Command "Start-Process bash -ArgumentList '-lc', '\"$bash_cmd\"'" >/dev/null 2>&1 &
    success "$title launched in a dedicated console window."
  elif command_exists gnome-terminal; then
    gnome-terminal --title="$title" -- bash -lc "$bash_cmd" >/dev/null 2>&1 &
    success "$title launched in a separate GNOME Terminal window."
  elif command_exists konsole; then
    konsole --new-tab -p tabtitle="$title" -e bash -lc "$bash_cmd" >/dev/null 2>&1 &
    success "$title launched in a new Konsole tab/window."
  elif command_exists xfce4-terminal; then
    xfce4-terminal --title="$title" --working-directory="$workdir" --command="bash -lc '$bash_cmd'" >/dev/null 2>&1 &
    success "$title launched in a separate XFCE Terminal window."
  elif command_exists alacritty; then
    alacritty --title "$title" --working-directory "$workdir" -e bash -lc "$bash_cmd" >/dev/null 2>&1 &
    success "$title launched in a separate Alacritty window."
  elif command_exists kitty; then
    kitty --title "$title" --directory "$workdir" bash -lc "$bash_cmd" >/dev/null 2>&1 &
    success "$title launched in a separate Kitty window."
  elif command_exists xterm; then
    (cd "$workdir" && xterm -T "$title" -e bash -lc "$bash_cmd") >/dev/null 2>&1 &
    success "$title launched in an xterm window."
  else
    warning "No supported terminal emulator found; starting $title in the background."
    (cd "$workdir" && nohup bash -lc "$command" >"$log_file" 2>&1 </dev/null &)
    success "$title started in background. Log: $log_file"
  fi
}

# -----------------------------------------------------------------------------
# Banner
# -----------------------------------------------------------------------------
cat <<'BANNER'
  _                     _ __  __ _           _
 | |    ___   ___  __ _| |  \/  (_)_ __   __| |
 | |   / _ \ / __|/ _` | | |\/| | | '_ \ / _` |
 | |__| (_) | (__| (_| | | |  | | | | | (_| |
 |_____\___/ \___|\__,_|_|_|  |_|_|_| |_|\__,_|
 Sovereign, Air-Gapped AI Orchestration Suite
BANNER

# -----------------------------------------------------------------------------
# 1. Start Docker Infrastructure
# -----------------------------------------------------------------------------
write_header "Step 1: Starting Docker Infrastructure"
require_command docker

if ! docker info >/dev/null 2>&1; then
  die "Docker daemon is not running or is not reachable. Please start Docker and rerun."
fi
success "Docker daemon is responsive."

[[ -f "$COMPOSE_FILE" ]] || die "Docker compose file not found at: $COMPOSE_FILE"

info "Starting containers via docker compose with healthcheck synchronization..."
docker compose -f "$COMPOSE_FILE" up -d --wait ||
  die "Failed to start Docker Compose infrastructure."
success "Docker Compose services launched and healthy."

wait_for_port 127.0.0.1 3306 "MySQL" 30 || die "MySQL did not become ready."
wait_for_port 127.0.0.1 6333 "Qdrant HTTP" 30 || die "Qdrant did not become ready."
wait_for_port 127.0.0.1 8081 "SearXNG" 30 || die "SearXNG did not become ready."

# -----------------------------------------------------------------------------
# 2. Check local Ollama connectivity and models
# -----------------------------------------------------------------------------
write_header "Step 2: Checking Ollama Engine & Models"
require_command curl
require_command python3
require_command ollama

if ! tags_json="$(curl --fail --silent --show-error --max-time 5 "$OLLAMA_URL/api/tags" 2>&1)"; then
  failure "Unable to connect to Ollama at $OLLAMA_URL: $tags_json"
  echo "Please ensure Ollama is installed and running ('ollama serve')." >&2
  exit 1
fi
success "Ollama is responding at $OLLAMA_URL."

ensure_model "$CHAT_MODEL" "Chat" "$tags_json"
ensure_model "$EMBEDDING_MODEL" "Embedding" "$tags_json"

# -----------------------------------------------------------------------------
# 3. Launch backend and frontend
# -----------------------------------------------------------------------------
[[ -d "$BACKEND_DIR" ]] || die "Backend directory not found: $BACKEND_DIR"
[[ -x "$BACKEND_DIR/mvnw" ]] || die "Maven wrapper not found or not executable: $BACKEND_DIR/mvnw (run: chmod +x backend/mvnw)"
[[ -d "$FRONTEND_DIR" ]] || die "Frontend directory not found: $FRONTEND_DIR"
require_command npm

port_in_use 127.0.0.1 8080 && warning "Port 8080 is already occupied. Ensure no previous backend instance is running."
port_in_use 127.0.0.1 3000 && warning "Port 3000 is already occupied. Next.js may fall back to port 3001."

write_header "Step 3: Launching Backend & Frontend Services"

info "Launching Spring Boot backend..."
launch_in_terminal \
  "LocalMind Backend (Spring Boot :8080)" \
  "$BACKEND_DIR" \
  "./mvnw spring-boot:run" \
  "$ROOT_DIR/backend.log"

if [[ ! -d "$FRONTEND_DIR/node_modules" ]]; then
  info "Installing frontend dependencies (first-time setup)..."
  (cd "$FRONTEND_DIR" && npm install) || die "Frontend npm install failed."
fi

info "Launching Next.js frontend..."
launch_in_terminal \
  "LocalMind Frontend (Next.js :3000)" \
  "$FRONTEND_DIR" \
  "npm run dev" \
  "$ROOT_DIR/frontend.log"

write_header "LocalMind Stack Launched Successfully!"
cat <<EOF
Access Points:
  * LocalMind Web Application : http://localhost:3000
  * Backend REST API          : http://localhost:8080/api
  * Qdrant Vector Console     : http://localhost:6333/dashboard
  * SearXNG Search Engine     : http://localhost:8081
  * Local Ollama Instance     : $OLLAMA_URL

Quick Commands:
  - Run backend unit/e2e tests : cd backend && ./mvnw test
  - Run frontend test suite   : cd frontend && npm test
  - Stop docker infrastructure: docker compose -f docker/docker-compose.yml down
EOF
