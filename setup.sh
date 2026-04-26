#!/usr/bin/env bash
# =============================================================================
# CHIEF Setup Script
# Controller Farm for Clouds of Software-Defined Community Networks
# =============================================================================
# Usage:
#   ./setup.sh              # Full setup (infra + build + ODL)
#   ./setup.sh --infra-only # Start Docker services only (skip build/ODL)
#   ./setup.sh --build-only # Build CHIEF only (Docker + ODL must be running)
#   ./setup.sh --stop       # Stop and remove all Docker containers
# =============================================================================

set -euo pipefail

# ── Colour helpers ─────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}${BOLD}[INFO]${RESET}  $*"; }
success() { echo -e "${GREEN}${BOLD}[OK]${RESET}    $*"; }
warn()    { echo -e "${YELLOW}${BOLD}[WARN]${RESET}  $*"; }
error()   { echo -e "${RED}${BOLD}[ERROR]${RESET} $*" >&2; }
die()     { error "$*"; exit 1; }

# ── Configuration ──────────────────────────────────────────────────────────────
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ODL_VERSION="0.3.4-Lithium-SR4"
ODL_TARBALL="distribution-karaf-${ODL_VERSION}.tar.gz"
ODL_URL="https://nexus.opendaylight.org/content/repositories/opendaylight.release/org/opendaylight/integration/distribution-karaf/${ODL_VERSION}/${ODL_TARBALL}"
ODL_DIR="${SCRIPT_DIR}/distribution-karaf-${ODL_VERSION}"

ACTIVEMQ_BROKER_PORT=61616
HADOOP_NAMENODE_PORT=9870

# ── Argument parsing ────────────────────────────────────────────────────────────
MODE="full"
case "${1:-}" in
  --infra-only)  MODE="infra"  ;;
  --build-only)  MODE="build"  ;;
  --stop)        MODE="stop"   ;;
  "")            MODE="full"   ;;
  *) die "Unknown option '${1}'. Use --infra-only | --build-only | --stop" ;;
esac

# ── Helper: wait for a TCP port ─────────────────────────────────────────────────
wait_for_port() {
  local host="$1" port="$2" label="$3"
  local retries=30 delay=5
  info "Waiting for ${label} on ${host}:${port} …"
  for i in $(seq 1 "${retries}"); do
    if nc -z "${host}" "${port}" 2>/dev/null; then
      success "${label} is up."
      return 0
    fi
    echo -n "  attempt ${i}/${retries} … "
    sleep "${delay}"
  done
  die "${label} did not become available after $(( retries * delay ))s."
}

# ═════════════════════════════════════════════════════════════════════════════
# STOP MODE
# ═════════════════════════════════════════════════════════════════════════════
if [[ "${MODE}" == "stop" ]]; then
  info "Stopping CHIEF Docker services …"
  cd "${SCRIPT_DIR}"
  docker compose down
  success "All services stopped."
  exit 0
fi

# ═════════════════════════════════════════════════════════════════════════════
# PREREQUISITES CHECK
# ═════════════════════════════════════════════════════════════════════════════
check_prerequisites() {
  echo ""
  echo -e "${BOLD}━━━ Checking Prerequisites ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"

  # Java 8
  local J8_PATH="/usr/lib/jvm/java-8-openjdk-amd64"
  if [[ ! -d "${J8_PATH}" ]]; then
    info "Java 8 not found. Attempting to install …"
    sudo apt update && sudo apt install openjdk-8-jdk -y || die "Failed to install Java 8. Please install it manually."
  fi

  export JAVA_HOME="${J8_PATH}"
  export PATH="${JAVA_HOME}/bin:${PATH}"
  
  # ── Generate environment helper script ──────────────────────────────────
  cat > "${SCRIPT_DIR}/env.sh" <<EOF
# CHIEF Environment Configuration
export JAVA_HOME="${J8_PATH}"
export PATH="\${JAVA_HOME}/bin:\${PATH}"
EOF
  chmod +x "${SCRIPT_DIR}/env.sh"

  JAVA_VER=$("${JAVA_HOME}/bin/java" -version 2>&1 | awk -F '"' '/version/ {print $2}')
  if [[ "${JAVA_VER}" != 1.8* ]]; then
    die "Java 8 was not correctly configured (found ${JAVA_VER})."
  fi
  success "Using Java 8 (${JAVA_VER}) from ${JAVA_HOME}"

  # Maven
  if ! command -v mvn &>/dev/null; then
    die "Apache Maven not found. Please install Maven 3.3.9+ (e.g. 'sudo apt install maven')."
  fi
  MVN_VER=$(mvn -q --version 2>&1 | head -1)
  success "Maven found: ${MVN_VER}"

  # Docker
  if ! command -v docker &>/dev/null; then
    die "Docker not found. Please install Docker (https://docs.docker.com/get-docker/)."
  fi
  success "Docker found: $(docker --version)"

  # Docker Compose (v2 plugin or standalone)
  if docker compose version &>/dev/null 2>&1; then
    success "Docker Compose found: $(docker compose version)"
  elif command -v docker-compose &>/dev/null; then
    warn "docker compose plugin not found; falling back to 'docker-compose'."
    # Alias for the rest of the script
    docker() { if [[ "$1" == "compose" ]]; then shift; command docker-compose "$@"; else command docker "$@"; fi; }
    export -f docker
  else
    die "Docker Compose not found. Please install it (https://docs.docker.com/compose/install/)."
  fi

  # nc (netcat) for port probing
  if ! command -v nc &>/dev/null; then
    warn "'nc' (netcat) not found. Port readiness checks will be skipped."
    wait_for_port() { warn "Skipping port check for $3 (nc unavailable)."; }
  fi

  # wget or curl (for ODL download)
  if ! command -v wget &>/dev/null && ! command -v curl &>/dev/null; then
    warn "Neither wget nor curl found. ODL download will be skipped."
  fi
}

# ═════════════════════════════════════════════════════════════════════════════
# INFRA: Start Docker services (Hadoop + ActiveMQ)
# ═════════════════════════════════════════════════════════════════════════════
# ── Helper: check if a local TCP port is already bound ────────────────────────
port_in_use() {
  local port="$1"
  # Try nc first, fall back to /proc/net/tcp6
  if command -v nc &>/dev/null; then
    nc -z localhost "${port}" 2>/dev/null
  else
    grep -q ":$(printf '%04X' "${port}" )" /proc/net/tcp6 /proc/net/tcp 2>/dev/null
  fi
}

start_infra() {
  echo ""
  echo -e "${BOLD}━━━ Starting Docker Infrastructure ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  cd "${SCRIPT_DIR}"

  # Detect whether ActiveMQ ports are already occupied (e.g. by cassowary-broker
  # or any other container from a sibling project).
  local skip_activemq=false
  if port_in_use "${ACTIVEMQ_BROKER_PORT}" || port_in_use 8161; then
    warn "Ports ${ACTIVEMQ_BROKER_PORT}/8161 are already in use — an ActiveMQ broker is running."
    warn "CHIEF will reuse the existing broker. Skipping the activemq service."
    skip_activemq=true
  fi

  if [[ "${skip_activemq}" == "true" ]]; then
    info "Starting Hadoop services only (activemq scaled to 0) …"
    docker compose up -d --scale activemq=0 namenode datanode resourcemanager nodemanager historyserver
  else
    info "Pulling images and starting all containers …"
    docker compose up -d
  fi
  success "Containers started."

  wait_for_port "localhost" "${ACTIVEMQ_BROKER_PORT}" "ActiveMQ (AMQP)"
  wait_for_port "localhost" "${HADOOP_NAMENODE_PORT}" "Hadoop NameNode"

  echo ""
  success "Infrastructure is ready."
  info  "  ActiveMQ Web Console : http://localhost:8161  (admin / admin)"
  info  "  ActiveMQ Broker      : tcp://localhost:61616"
  info  "  Hadoop NameNode UI   : http://localhost:9870"
}

# ═════════════════════════════════════════════════════════════════════════════
# MESSAGING4TRANSPORT: Clone and build external dependency
# ═════════════════════════════════════════════════════════════════════════════
build_messaging4transport() {
  echo ""
  echo -e "${BOLD}━━━ Building messaging4transport ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"

  if [[ ! -d "messaging4transport" ]]; then
    info "Cloning messaging4transport …"
    git clone https://github.com/KathiraveluLab/messaging4transport.git || die "Failed to clone messaging4transport."
  fi

  cd "messaging4transport"
  info "Compiling and installing messaging4transport …"
  mvn clean install -DskipTests || die "Failed to build messaging4transport."
  cd ..
  success "messaging4transport built and installed."
}

# ═════════════════════════════════════════════════════════════════════════════
# BUILD: Maven build of CHIEF
# ═════════════════════════════════════════════════════════════════════════════
build_chief() {
  build_messaging4transport
  echo ""
  echo -e "${BOLD}━━━ Building CHIEF ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  cd "${SCRIPT_DIR}"
  info "Running: mvn clean install -DskipTests"
  mvn clean install -DskipTests
  success "CHIEF build complete."
}

# ═════════════════════════════════════════════════════════════════════════════
# ODL: Download and configure OpenDaylight Karaf
# ═════════════════════════════════════════════════════════════════════════════
setup_odl() {
  echo ""
  echo -e "${BOLD}━━━ Setting up OpenDaylight (Lithium SR4) ━━━━━━━━━━━━━━━━━━━━━━━${RESET}"

  if [[ -d "${ODL_DIR}" ]]; then
    info "OpenDaylight directory already exists: ${ODL_DIR}"
  else
    if [[ -f "${SCRIPT_DIR}/${ODL_TARBALL}" ]]; then
      info "Found existing tarball: ${ODL_TARBALL}"
    else
      info "Downloading OpenDaylight ${ODL_VERSION} …"
      if command -v wget &>/dev/null; then
        wget -q --show-progress -O "${SCRIPT_DIR}/${ODL_TARBALL}" "${ODL_URL}" \
          || die "Download failed. Check your network or get the tarball manually from:\n  ${ODL_URL}"
      elif command -v curl &>/dev/null; then
        curl -L --progress-bar -o "${SCRIPT_DIR}/${ODL_TARBALL}" "${ODL_URL}" \
          || die "Download failed. Check your network or get the tarball manually from:\n  ${ODL_URL}"
      else
        warn "Cannot download ODL (no wget/curl). Please place '${ODL_TARBALL}' in ${SCRIPT_DIR} and re-run."
        return
      fi
    fi
    info "Extracting ${ODL_TARBALL} …"
    tar -xzf "${SCRIPT_DIR}/${ODL_TARBALL}" -C "${SCRIPT_DIR}"
    success "Extracted to ${ODL_DIR}"
  fi

  # ── Patch Karaf for modern Java compatibility ────────────────────────────
  local karaf_bin="${ODL_DIR}/bin/karaf"
  if [[ -f "${karaf_bin}" ]]; then
    info "Patching Karaf for modern Java compatibility …"
    # Remove incompatible VM flag
    sed -i 's/-XX:+UnsyncloadClass//g' "${karaf_bin}"
    # Fix version detection logic that fails on Java 9+
    sed -i 's/if \[ "$VERSION" -lt "60" \]/if \[ "100" -lt "60" \]/g' "${karaf_bin}"
    success "Karaf script patched."
  fi

  # ── Write Messaging4Transport config ──────────────────────────────────────
  local cfg_dir="${ODL_DIR}/etc"
  local cfg_file="${cfg_dir}/org.opendaylight.messaging4transport.cfg"
  if [[ ! -f "${cfg_file}" ]]; then
    info "Writing Messaging4Transport config …"
    cat > "${cfg_file}" <<EOF
brokerUrl=tcp://localhost:${ACTIVEMQ_BROKER_PORT}
username=admin
password=admin
EOF
    success "Created ${cfg_file}"
  else
    info "Messaging4Transport config already present."
  fi

  # ── Drop CHIEF feature repo into ODL's initial feature boot list ──────────
  local feat_cfg="${ODL_DIR}/etc/org.apache.karaf.features.cfg"
  if [[ -f "${feat_cfg}" ]] && ! grep -q "chief-features" "${feat_cfg}"; then
    info "Registering CHIEF features in Karaf boot config …"
    # Append the CHIEF feature repo URL to featureRepositories
    sed -i 's|^featureRepositories=|featureRepositories=mvn:org.opendaylight.chief/chief-features/1.0-SNAPSHOT/xml/features,|' \
      "${feat_cfg}" 2>/dev/null || true
    # Add odl-chief-all to boot features
    sed -i 's|^featuresBoot=|featuresBoot=odl-chief-all,|' \
      "${feat_cfg}" 2>/dev/null || true
    success "CHIEF features registered."
  fi

  # ── Configure Karaf to use local .m2 repository ──────────────────────────
  local mvn_cfg="${ODL_DIR}/etc/org.ops4j.pax.url.mvn.cfg"
  if [[ -f "${mvn_cfg}" ]]; then
    info "Configuring Karaf to use local .m2 repository …"
    # Point localRepository to ~/.m2/repository
    sed -i 's|^org.ops4j.pax.url.mvn.localRepository=.*|org.ops4j.pax.url.mvn.localRepository=${user.home}/.m2/repository|' "${mvn_cfg}"
    # Ensure defaultLocalRepoAsRemote is true to help resolution
    sed -i 's|^org.ops4j.pax.url.mvn.defaultLocalRepoAsRemote=.*|org.ops4j.pax.url.mvn.defaultLocalRepoAsRemote=true|' "${mvn_cfg}"
    # Force HTTPS for all repositories (Central now requires HTTPS)
    sed -i 's|http://|https://|g' "${mvn_cfg}"
    # Add ODL Nexus repositories
    sed -i '/org.ops4j.pax.url.mvn.repositories=/a \    https://nexus.opendaylight.org/content/repositories/public/@id=opendaylight.public, \\' "${mvn_cfg}"
    sed -i '/opendaylight.public/a \    https://nexus.opendaylight.org/content/repositories/opendaylight.release/@id=opendaylight.release, \\' "${mvn_cfg}"
    sed -i '/opendaylight.release/a \    https://nexus.opendaylight.org/content/repositories/opendaylight.snapshot/@id=opendaylight.snapshot@snapshots@noreleases, \\' "${mvn_cfg}"
    success "Karaf Maven configuration updated."
  fi
}

# ═════════════════════════════════════════════════════════════════════════════
# POST-SETUP INSTRUCTIONS
# ═════════════════════════════════════════════════════════════════════════════
print_next_steps() {
  echo ""
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  echo -e "${GREEN}${BOLD}  CHIEF setup complete!${RESET}"
  echo -e "${BOLD}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${RESET}"
  echo ""
  echo -e "  ${BOLD}1. Initialize environment (run this first!):${RESET}"
  echo -e "     ${CYAN}source ./env.sh${RESET}"
  echo ""
  echo -e "  ${BOLD}2. Start OpenDaylight Karaf:${RESET}"
  echo -e "     ${CYAN}${ODL_DIR}/bin/karaf${RESET}"
  echo ""
  echo -e "  ${BOLD}3. Inside the Karaf console, install CHIEF features:${RESET}"
  echo -e "     ${CYAN}feature:repo-add mvn:org.opendaylight.chief/chief-features/1.0-SNAPSHOT/xml/features${RESET}"
  echo -e "     ${CYAN}feature:install odl-chief-all${RESET}"
  echo ""
  echo -e "  ${BOLD}4. Service endpoints:${RESET}"
  echo -e "     ActiveMQ Web Console : ${CYAN}http://localhost:8161${RESET}  (admin / admin)"
  echo -e "     ActiveMQ Broker      : ${CYAN}tcp://localhost:61616${RESET}"
  echo -e "     Hadoop NameNode UI   : ${CYAN}http://localhost:9870${RESET}"
  echo ""
  echo -e "  ${BOLD}To stop all Docker services:${RESET}"
  echo -e "     ${CYAN}./setup.sh --stop${RESET}"
  echo ""
}

# ═════════════════════════════════════════════════════════════════════════════
# MAIN
# ═════════════════════════════════════════════════════════════════════════════
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║  CHIEF – Controller Farm for Clouds of SDC Networks              ║${RESET}"
echo -e "${BOLD}║  Setup Script                                                    ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════════════════╝${RESET}"

check_prerequisites

case "${MODE}" in
  full)
    start_infra
    build_chief
    setup_odl
    print_next_steps
    ;;
  infra)
    start_infra
    ;;
  build)
    build_chief
    ;;
esac
