#!/usr/bin/env bash

set -eu

echo "========================================"
echo "AGENT PREREQUISITES"
echo "========================================"

echo "User: $(whoami)"
echo "UID: $(id -u)"
echo "GID: $(id -g)"
echo "HOME: $HOME"
echo "WORKSPACE: ${WORKSPACE:-$(pwd)}"


# ============================================================
# CHECK OS
# ============================================================

if [ ! -f /etc/os-release ]; then
    echo "ERROR: Cannot identify Linux distribution."
    exit 1
fi

. /etc/os-release

echo "OS: $PRETTY_NAME"

case "$ID" in
    ubuntu|debian)
        ;;
    *)
        echo "ERROR: Only Ubuntu/Debian is supported."
        exit 1
        ;;
esac


# ============================================================
# CHECK SUDO
# ============================================================

if ! sudo -n true >/dev/null 2>&1; then
    echo "ERROR: Passwordless sudo is required."
    echo ""
    echo "Configure once:"
    echo "$(whoami) ALL=(ALL) NOPASSWD: ALL"
    exit 1
fi


APT_UPDATED=false

apt_update_once() {

    if [ "$APT_UPDATED" = "false" ]; then
        echo "Running apt-get update..."
        sudo apt-get update
        APT_UPDATED=true
    fi
}


# ============================================================
# BASE PACKAGES
# ============================================================

echo "========================================"
echo "BASE PACKAGES"
echo "========================================"

MISSING_PACKAGES=""

for package in \
    ca-certificates \
    curl \
    git \
    gnupg \
    lsb-release \
    apt-transport-https \
    unzip \
    jq
do

    if dpkg -s "$package" >/dev/null 2>&1; then
        echo "$package already installed - SKIP"
    else
        echo "$package missing"
        MISSING_PACKAGES="$MISSING_PACKAGES $package"
    fi

done

if [ -n "$MISSING_PACKAGES" ]; then

    apt_update_once

    sudo DEBIAN_FRONTEND=noninteractive \
        apt-get install -y $MISSING_PACKAGES
fi


# ============================================================
# JAVA
# ============================================================

echo "========================================"
echo "JAVA"
echo "========================================"

if command -v java >/dev/null 2>&1; then

    echo "Java already installed - SKIP"
    java -version

else

    echo "Java missing - installing OpenJDK 17"

    apt_update_once

    sudo DEBIAN_FRONTEND=noninteractive \
        apt-get install -y openjdk-17-jdk

    java -version
fi


# ============================================================
# AZURE CLI
# ============================================================

echo "========================================"
echo "AZURE CLI"
echo "========================================"

if command -v az >/dev/null 2>&1; then

    echo "Azure CLI already installed - SKIP"

    echo "Azure CLI path:"
    command -v az

    az version

else

    echo "Azure CLI missing - installing"

    curl -sL \
        https://aka.ms/InstallAzureCLIDeb \
        -o /tmp/install-azure-cli.sh

    sudo bash /tmp/install-azure-cli.sh

    rm -f /tmp/install-azure-cli.sh

    if ! command -v az >/dev/null 2>&1; then
        echo "ERROR: Azure CLI installation failed."
        exit 1
    fi

    az version
fi


# ============================================================
# DOCKER
# ============================================================

echo "========================================"
echo "DOCKER"
echo "========================================"

if command -v docker >/dev/null 2>&1; then

    echo "Docker already installed - SKIP"
    docker --version

else

    echo "Docker missing - installing"

    sudo install \
        -m 0755 \
        -d /etc/apt/keyrings

    sudo curl \
        -fsSL \
        https://download.docker.com/linux/ubuntu/gpg \
        -o /etc/apt/keyrings/docker.asc

    sudo chmod \
        a+r \
        /etc/apt/keyrings/docker.asc

    . /etc/os-release

    cat <<EOF | sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: ${UBUNTU_CODENAME:-$VERSION_CODENAME}
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF

    sudo apt-get update

    sudo DEBIAN_FRONTEND=noninteractive \
        apt-get install -y \
            docker-ce \
            docker-ce-cli \
            containerd.io \
            docker-buildx-plugin \
            docker-compose-plugin

    docker --version
fi


# ============================================================
# DOCKER SERVICE
# ============================================================

echo "========================================"
echo "DOCKER SERVICE"
echo "========================================"

if sudo systemctl is-active --quiet docker; then

    echo "Docker already running - SKIP"

else

    echo "Starting Docker"
    sudo systemctl start docker
fi

sudo systemctl enable docker >/dev/null 2>&1 || true


# ============================================================
# DOCKER GROUP ACCESS
# ============================================================

echo "========================================"
echo "DOCKER USER ACCESS"
echo "========================================"

if id -nG "$(whoami)" | grep -qw docker; then

    echo "$(whoami) already in docker group - SKIP"

else

    echo "Adding $(whoami) to docker group"

    sudo usermod -aG docker "$(whoami)"

    echo ""
    echo "Docker permission configured."
    echo "Restart Jenkins agent once and rerun pipeline."
    echo ""

    exit 1
fi


# ============================================================
# DOCKER COMPOSE
# ============================================================

echo "========================================"
echo "DOCKER COMPOSE"
echo "========================================"

if docker compose version >/dev/null 2>&1; then

    echo "Docker Compose already installed - SKIP"
    docker compose version

else

    echo "Docker Compose missing - installing"

    sudo apt-get update

    sudo DEBIAN_FRONTEND=noninteractive \
        apt-get install -y docker-compose-plugin

    docker compose version
fi


# ============================================================
# KUBECTL / KUBELOGIN
# ============================================================

echo "========================================"
echo "KUBECTL / KUBELOGIN"
echo "========================================"

NEED_AKS_TOOLS=false

if command -v kubectl >/dev/null 2>&1; then

    echo "kubectl already installed - SKIP"
    kubectl version --client

else

    echo "kubectl missing"
    NEED_AKS_TOOLS=true
fi


if command -v kubelogin >/dev/null 2>&1; then

    echo "kubelogin already installed - SKIP"
    kubelogin --version

else

    echo "kubelogin missing"
    NEED_AKS_TOOLS=true
fi


if [ "$NEED_AKS_TOOLS" = "true" ]; then

    echo "Installing AKS CLI tools"

    BOOTSTRAP_DIR="${WORKSPACE:-$(pwd)}/.agent-bootstrap"

    rm -rf "$BOOTSTRAP_DIR"
    mkdir -p "$BOOTSTRAP_DIR"

    az aks install-cli \
        --install-location "$BOOTSTRAP_DIR/kubectl" \
        --kubelogin-install-location "$BOOTSTRAP_DIR/kubelogin"

    test -x "$BOOTSTRAP_DIR/kubectl"
    test -x "$BOOTSTRAP_DIR/kubelogin"

    sudo install \
        -m 0755 \
        "$BOOTSTRAP_DIR/kubectl" \
        /usr/local/bin/kubectl

    sudo install \
        -m 0755 \
        "$BOOTSTRAP_DIR/kubelogin" \
        /usr/local/bin/kubelogin

    rm -rf "$BOOTSTRAP_DIR"
fi


# ============================================================
# VERIFY KUSTOMIZE
# ============================================================

echo "========================================"
echo "KUSTOMIZE"
echo "========================================"

if kubectl kustomize --help >/dev/null 2>&1; then

    echo "kubectl kustomize available"

else

    echo "ERROR: kubectl kustomize unavailable."
    exit 1
fi


# ============================================================
# VERIFY DOCKER ACCESS
# ============================================================

echo "========================================"
echo "DOCKER ACCESS"
echo "========================================"

if docker info >/dev/null 2>&1; then

    echo "Docker access OK"

else

    echo "ERROR: Current Jenkins agent cannot access Docker."
    echo "Restart Jenkins agent after docker group assignment."
    exit 1
fi


# ============================================================
# FINAL VALIDATION
# ============================================================

echo "========================================"
echo "FINAL VALIDATION"
echo "========================================"

for command in \
    git \
    curl \
    java \
    docker \
    az \
    kubectl \
    kubelogin
do

    if command -v "$command" >/dev/null 2>&1; then
        echo "$command -> $(command -v "$command")"
    else
        echo "ERROR: $command missing."
        exit 1
    fi

done


echo "========================================"
echo "VERSIONS"
echo "========================================"

git --version

java -version

docker --version

docker compose version

az version

kubectl version --client

kubelogin --version


echo "========================================"
echo "ALL PREREQUISITES PASSED"
echo "========================================"
