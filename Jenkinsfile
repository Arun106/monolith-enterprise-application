pipeline {
    agent { label 'PVM1' }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')
        buildDiscarder(logRotator(numToKeepStr: '20', artifactNumToKeepStr: '10'))
    }

    triggers {
        githubPush()
    }

    parameters {
        string(
            name: 'REPOSITORY_URL',
            defaultValue: 'https://github.com/Arun106/monolith-enterprise-application.git',
            description: 'Git repository to build'
        )
        string(
            name: 'REPOSITORY_BRANCH',
            defaultValue: 'master',
            description: 'Git branch to build'
        )
        choice(
            name: 'DEPLOY_TARGET',
            choices: ['none', 'acr', 'aks'],
            description: 'CD target: CI only, publish images to ACR, or publish and deploy to AKS'
        )
        string(
            name: 'NVD_API_CREDENTIALS_ID',
            defaultValue: '',
            description: 'Optional Jenkins secret-text credential containing an NVD API key'
        )
        choice(
            name: 'SECURITY_GATE_MODE',
            choices: ['report-only', 'strict'],
            description: 'Report legacy vulnerabilities without blocking CI, or enforce security thresholds'
        )
        string(name: 'ACR_LOGIN_SERVER', defaultValue: 'chunkhoundacr20260802.azurecr.io', description: 'AKS mode: <registry>.azurecr.io')
        string(name: 'ACR_CREDENTIALS_ID', defaultValue: 'acr-chunkhound', description: 'AKS mode: Jenkins username/password credential for ACR')
        string(name: 'AKS_RESOURCE_GROUP', defaultValue: 'Ar-RG', description: 'AKS mode: Azure resource group')
        string(name: 'AKS_CLUSTER_NAME', defaultValue: 'Ar-AKS', description: 'AKS mode: AKS cluster name')
        string(name: 'AZURE_TENANT_ID', defaultValue: 'pavip7547gmail.onmicrosoft.com', description: 'AKS mode: Microsoft Entra tenant ID')
        string(name: 'AZURE_SUBSCRIPTION_ID', defaultValue: 'ffcf8f61-5974-487d-95d9-9adf380c6233', description: 'AKS mode: Azure subscription ID')
        string(name: 'AZURE_CREDENTIALS_ID', defaultValue: 'azure-sp', description: 'AKS mode: service-principal username/password credential')
    }

    environment {
        CI = 'true'
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'
        PATH = "${env.HOME}/.local/bin:/usr/local/bin:/usr/lib/jvm/java-17-openjdk-amd64/bin:${env.PATH}"
        MAVEN_IMAGE = 'maven:3.9.13-eclipse-temurin-8-noble'
        SONARQUBE_ENV = 'naukri-sonarqube'
        SONAR_CREDENTIALS_ID = 'sonarqube-snowman-token'
        PVM1_SONAR_URL = 'http://127.0.0.1:9000'
        SONAR_PROJECT_KEY = 'snowman-enterprise-monolith'
        APPLICATION_IMAGE = 'monolith-enterprise-application'
        MIGRATION_IMAGE = 'monolith-enterprise-application-migration'
        KUBECONFORM_IMAGE = 'ghcr.io/yannh/kubeconform:v0.7.0'
        TRIVY_IMAGE = 'aquasec/trivy:0.65.0'
        DOCKER_COMPOSE_VERSION = '2.39.2'
        DEPLOYMENT_STARTED = 'false'
        SECURITY_GATE_MODE = "${params.SECURITY_GATE_MODE ?: 'report-only'}"
    }

    stages {
        stage('Checkout') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    if [ -d .trivy-cache ]; then
                        docker run --rm \
                            --entrypoint sh \
                            --volume "$WORKSPACE/.trivy-cache:/cache" \
                            "$TRIVY_IMAGE" \
                            -c "chown -R $(id -u):$(id -g) /cache"
                    fi
                '''
                deleteDir()
                git branch: params.REPOSITORY_BRANCH, url: params.REPOSITORY_URL
                script {
                    env.GIT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()
                    env.IMAGE_TAG = "sha-${env.GIT_COMMIT.take(12)}-${env.BUILD_NUMBER}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${env.GIT_COMMIT.take(8)}"
                }
            }
        }

        stage('Agent prerequisites') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    for command in git docker curl java mvn; do
                        command -v "$command" >/dev/null || {
                            echo "Required command is missing on PVM1: $command" >&2
                            exit 1
                        }
                    done
                    if ! command -v kubectl >/dev/null; then
                        mkdir -p "$HOME/.local/bin"
                        curl --fail --silent --show-error --location \
                            https://dl.k8s.io/release/v1.36.2/bin/linux/amd64/kubectl \
                            --output "$HOME/.local/bin/kubectl"
                        chmod 0755 "$HOME/.local/bin/kubectl"
                    fi
                    if ! docker compose version >/dev/null 2>&1; then
                        mkdir -p "$HOME/.docker/cli-plugins"
                        curl --fail --silent --show-error --location \
                            "https://github.com/docker/compose/releases/download/v${DOCKER_COMPOSE_VERSION}/docker-compose-linux-x86_64" \
                            --output "$HOME/.docker/cli-plugins/docker-compose"
                        chmod 0755 "$HOME/.docker/cli-plugins/docker-compose"
                    fi
                    if [ "${DEPLOY_TARGET:-none}" = aks ]; then
                        command -v az >/dev/null || {
                            echo "Azure CLI is required for AKS deployment" >&2
                            exit 1
                        }
                    fi
                    git --version
                    docker --version
                    docker compose version
                    kubectl version --client
                '''
            }
        }

        stage('Build and unit test') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    mkdir -p "$HOME/.m2"
                    docker run --rm \
                        --user "$(id -u):$(id -g)" \
                        --group-add "$(stat -c %g /var/run/docker.sock)" \
                        --env HOME=/tmp/jenkins-user \
                        --env MAVEN_CONFIG=/tmp/jenkins-user/.m2 \
                        --volume "$WORKSPACE:/workspace" \
                        --volume "$HOME/.m2:/tmp/jenkins-user/.m2" \
                        --workdir /workspace \
                        "$MAVEN_IMAGE" \
                        mvn --batch-mode --no-transfer-progress \
                            -Dliquibase.skip=true \
                            -Dmaven.repo.local=/tmp/jenkins-user/.m2/repository \
                            clean verify
                    test -s target/Snowman.jar
                    test -s target/site/jacoco/jacoco.xml
                '''
            }
            post {
                always {
                    junit allowEmptyResults: true,
                        testResults: 'target/surefire-reports/*.xml,target/failsafe-reports/*.xml'
                }
            }
        }

        stage('SonarQube analysis') {
            steps {
                withSonarQubeEnv("${SONARQUBE_ENV}") {
                    withCredentials([string(
                        credentialsId: env.SONAR_CREDENTIALS_ID,
                        variable: 'SONAR_TOKEN'
                    )]) {
                        sh '''#!/bin/bash
                            set -euo pipefail
                            mvn --batch-mode --no-transfer-progress \
                                -Dliquibase.skip=true \
                                org.sonarsource.scanner.maven:sonar-maven-plugin:5.2.0.4988:sonar \
                                -Dsonar.host.url="$PVM1_SONAR_URL" \
                                -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
                                -Dsonar.projectName="Snowman Enterprise Monolith" \
                                -Dsonar.projectVersion="1.0.$BUILD_NUMBER" \
                                -Dsonar.token="$SONAR_TOKEN" \
                                -Dsonar.java.binaries=target/classes \
                                -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                        '''
                    }
                }
            }
        }

        stage('OWASP dependency audit') {
            steps {
                script {
                    if (params.NVD_API_CREDENTIALS_ID?.trim()) {
                        withCredentials([string(
                            credentialsId: params.NVD_API_CREDENTIALS_ID,
                            variable: 'NVD_API_KEY'
                        )]) {
                            sh '''#!/bin/bash
                                set -euo pipefail
                                mvn --batch-mode --no-transfer-progress \
                                    -Dliquibase.skip=true \
                                    org.owasp:dependency-check-maven:12.1.8:check \
                                    -DskipTests \
                                    -Dformat=ALL \
                                    -DfailBuildOnCVSS="$([ "$SECURITY_GATE_MODE" = strict ] && printf 9.0 || printf 11.0)" \
                                    -DnvdApiKey="$NVD_API_KEY"
                            '''
                        }
                    } else {
                        echo 'NVD_API_CREDENTIALS_ID is empty; the initial NVD update may be rate limited.'
                        sh '''#!/bin/bash
                            set -euo pipefail
                            mvn --batch-mode --no-transfer-progress \
                                -Dliquibase.skip=true \
                                org.owasp:dependency-check-maven:12.1.8:check \
                                -DskipTests \
                                -Dformat=ALL \
                                -DfailBuildOnCVSS="$([ "$SECURITY_GATE_MODE" = strict ] && printf 9.0 || printf 11.0)"
                        '''
                    }
                }
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true,
                        artifacts: 'target/dependency-check-report.*'
                }
            }
        }

        stage('Quality gate') {
            steps {
                withCredentials([string(
                    credentialsId: env.SONAR_CREDENTIALS_ID,
                    variable: 'SONAR_TOKEN'
                )]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        task_file=target/sonar/report-task.txt
                        test -s "$task_file"
                        ce_task_url="$(awk '/^ceTaskUrl=/ { sub(/^[^=]*=/, ""); print }' "$task_file")"
                        test -n "$ce_task_url"

                        analysis_id=''
                        for attempt in $(seq 1 60); do
                            task_response="$(curl --fail --silent --show-error \
                                --user "$SONAR_TOKEN:" "$ce_task_url")"
                            task_status="$(printf '%s' "$task_response" \
                                | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)"
                            case "$task_status" in
                                SUCCESS)
                                    analysis_id="$(printf '%s' "$task_response" \
                                        | grep -o '"analysisId":"[^"]*"' | head -1 | cut -d'"' -f4)"
                                    break
                                    ;;
                                FAILED|CANCELED)
                                    echo "SonarQube compute task ended with status $task_status" >&2
                                    exit 1
                                    ;;
                                PENDING|IN_PROGRESS)
                                    sleep 5
                                    ;;
                                *)
                                    echo "Unexpected SonarQube compute-task status: $task_status" >&2
                                    exit 1
                                    ;;
                            esac
                        done
                        test -n "$analysis_id" || {
                            echo 'Timed out waiting for the SonarQube compute task' >&2
                            exit 1
                        }

                        sonar_base_url="${ce_task_url%%/api/ce/task*}"
                        gate_response="$(curl --fail --silent --show-error \
                            --user "$SONAR_TOKEN:" --get \
                            --data-urlencode "analysisId=$analysis_id" \
                            "$sonar_base_url/api/qualitygates/project_status")"
                        gate_status="$(printf '%s' "$gate_response" \
                            | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)"
                        echo "SonarQube quality gate status: $gate_status"
                        test "$gate_status" = OK
                    '''
                }
            }
        }

        stage('Validate delivery configuration') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    if docker compose version >/dev/null 2>&1; then
                        docker compose config --quiet
                    elif command -v docker-compose >/dev/null 2>&1; then
                        docker-compose config --quiet
                    else
                        echo 'Docker Compose is required on PVM1 (plugin or standalone command)' >&2
                        exit 1
                    fi
                    kubectl kustomize k8s/overlays/dev > snowman-dev.yaml
                    kubectl kustomize k8s/overlays/production > snowman-production.yaml
                    kubectl kustomize k8s/jobs > snowman-migration.yaml
                    for manifest in snowman-dev.yaml snowman-production.yaml snowman-migration.yaml; do
                        docker run --rm -i "$KUBECONFORM_IMAGE" \
                            -strict -summary -kubernetes-version 1.36.0 < "$manifest"
                    done
                '''
            }
        }

        stage('Build container images') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    docker build \
                        --tag "$APPLICATION_IMAGE:$IMAGE_TAG" \
                        --label "org.opencontainers.image.revision=$GIT_COMMIT" \
                        --label "org.opencontainers.image.version=$IMAGE_TAG" \
                        --file Dockerfile .
                    docker build \
                        --tag "$MIGRATION_IMAGE:$IMAGE_TAG" \
                        --label "org.opencontainers.image.revision=$GIT_COMMIT" \
                        --label "org.opencontainers.image.version=$IMAGE_TAG" \
                        --file Dockerfile.migration .
                '''
            }
        }

        stage('Container vulnerability scan') {
            steps {
                sh '''#!/bin/bash
                    set -euo pipefail
                    mkdir -p .trivy-cache
                    docker run --rm \
                        --user "$(id -u):$(id -g)" \
                        --group-add "$(stat -c %g /var/run/docker.sock)" \
                        --env HOME=/tmp \
                        --volume /var/run/docker.sock:/var/run/docker.sock \
                        --volume "$WORKSPACE/.trivy-cache:/tmp/trivy-cache" \
                        "$TRIVY_IMAGE" image \
                        --cache-dir /tmp/trivy-cache/trivy \
                        --ignore-unfixed \
                        --severity HIGH,CRITICAL \
                        --format json \
                        --output /tmp/trivy-cache/trivy-image-report.json \
                        "$APPLICATION_IMAGE:$IMAGE_TAG"
                    cp .trivy-cache/trivy-image-report.json trivy-image-report.json
                    if [ "$SECURITY_GATE_MODE" = strict ]; then
                        docker run --rm \
                            --user "$(id -u):$(id -g)" \
                            --group-add "$(stat -c %g /var/run/docker.sock)" \
                            --env HOME=/tmp \
                            --volume /var/run/docker.sock:/var/run/docker.sock \
                            --volume "$WORKSPACE/.trivy-cache:/tmp/trivy-cache" \
                            "$TRIVY_IMAGE" image \
                            --cache-dir /tmp/trivy-cache/trivy \
                            --ignore-unfixed \
                            --severity CRITICAL \
                            --exit-code 1 \
                            "$APPLICATION_IMAGE:$IMAGE_TAG"
                    else
                        echo 'Security scans are report-only for this legacy baseline; reports remain archived.'
                    fi
                '''
            }
            post {
                always {
                    archiveArtifacts allowEmptyArchive: true,
                        artifacts: 'trivy-image-report.json'
                }
            }
        }

        stage('Publish images to ACR') {
            when {
                expression { params.DEPLOY_TARGET in ['acr', 'aks'] }
            }
            steps {
                script {
                    if (!params.ACR_LOGIN_SERVER?.trim() || !params.ACR_CREDENTIALS_ID?.trim()) {
                        error('ACR_LOGIN_SERVER and ACR_CREDENTIALS_ID are required for AKS deployment')
                    }
                }
                withCredentials([usernamePassword(
                    credentialsId: params.ACR_CREDENTIALS_ID,
                    usernameVariable: 'ACR_USERNAME',
                    passwordVariable: 'ACR_PASSWORD'
                )]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        set +x
                        printf '%s' "$ACR_PASSWORD" | docker login "$ACR_LOGIN_SERVER" \
                            --username "$ACR_USERNAME" --password-stdin
                        set -x
                        docker tag "$APPLICATION_IMAGE:$IMAGE_TAG" \
                            "$ACR_LOGIN_SERVER/$APPLICATION_IMAGE:$IMAGE_TAG"
                        docker tag "$MIGRATION_IMAGE:$IMAGE_TAG" \
                            "$ACR_LOGIN_SERVER/$MIGRATION_IMAGE:$IMAGE_TAG"
                        docker push "$ACR_LOGIN_SERVER/$APPLICATION_IMAGE:$IMAGE_TAG"
                        docker push "$ACR_LOGIN_SERVER/$MIGRATION_IMAGE:$IMAGE_TAG"
                        docker logout "$ACR_LOGIN_SERVER"
                    '''
                }
            }
        }

        stage('Deploy to AKS') {
            when {
                expression { params.DEPLOY_TARGET == 'aks' }
            }
            steps {
                script {
                    def required = [
                        ACR_LOGIN_SERVER: params.ACR_LOGIN_SERVER,
                        AKS_RESOURCE_GROUP: params.AKS_RESOURCE_GROUP,
                        AKS_CLUSTER_NAME: params.AKS_CLUSTER_NAME,
                        AZURE_TENANT_ID: params.AZURE_TENANT_ID,
                        AZURE_SUBSCRIPTION_ID: params.AZURE_SUBSCRIPTION_ID
                    ]
                    def missing = required.findAll { !it.value?.trim() }.keySet()
                    if (missing) {
                        error("Missing AKS parameters: ${missing.join(', ')}")
                    }
                    env.DEPLOYMENT_STARTED = 'true'
                }
                withCredentials([usernamePassword(
                    credentialsId: params.AZURE_CREDENTIALS_ID,
                    usernameVariable: 'AZURE_CLIENT_ID',
                    passwordVariable: 'AZURE_CLIENT_SECRET'
                )]) {
                    sh '''#!/bin/bash
                        set -euo pipefail
                        set +x
                        az login --service-principal \
                            --username "$AZURE_CLIENT_ID" \
                            --password "$AZURE_CLIENT_SECRET" \
                            --tenant "$AZURE_TENANT_ID" >/dev/null
                        set -x
                        az account set --subscription "$AZURE_SUBSCRIPTION_ID"
                        az aks get-credentials \
                            --resource-group "$AKS_RESOURCE_GROUP" \
                            --name "$AKS_CLUSTER_NAME" \
                            --overwrite-existing

                        sed "s#ghcr.io/adikarthik/monolith-enterprise-application:latest#$ACR_LOGIN_SERVER/$APPLICATION_IMAGE:$IMAGE_TAG#g" \
                            snowman-production.yaml | kubectl apply -f -
                        kubectl delete job snowman-database-migration \
                            --namespace snowman --ignore-not-found=true
                        sed "s#ghcr.io/adikarthik/monolith-enterprise-application-migration:latest#$ACR_LOGIN_SERVER/$MIGRATION_IMAGE:$IMAGE_TAG#g" \
                            snowman-migration.yaml | kubectl apply -f -

                        if ! kubectl wait --namespace snowman \
                            --for=condition=complete job/snowman-database-migration \
                            --timeout=5m; then
                            kubectl logs --namespace snowman \
                                job/snowman-database-migration --all-containers=true || true
                            exit 1
                        fi
                        kubectl rollout status --namespace snowman \
                            deployment/snowman --timeout=5m
                        kubectl get pods,service,ingress --namespace snowman -o wide
                        kubectl delete pod "snowman-smoke-$BUILD_NUMBER" \
                            --namespace snowman --ignore-not-found=true
                        kubectl run "snowman-smoke-$BUILD_NUMBER" \
                            --namespace snowman \
                            --image=curlimages/curl:8.10.1 \
                            --restart=Never --rm --attach \
                            --command -- \
                            curl --fail --silent --show-error \
                                --retry 12 --retry-delay 5 \
                                http://snowman:8090/health
                        az logout
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "CI/CD completed for ${env.APPLICATION_IMAGE}:${env.IMAGE_TAG}"
        }
        failure {
            script {
                if (env.DEPLOYMENT_STARTED == 'true' && params.DEPLOY_TARGET == 'aks') {
                    sh '''#!/bin/bash
                        kubectl rollout undo --namespace snowman deployment/snowman || true
                        az logout >/dev/null 2>&1 || true
                    '''
                }
            }
        }
        always {
            archiveArtifacts allowEmptyArchive: true,
                artifacts: 'target/Snowman.jar,target/site/jacoco/jacoco.xml,target/dependency-check-report.*,trivy-image-report.json,snowman-*.yaml',
                fingerprint: true
        }
        cleanup {
            deleteDir()
        }
    }
}
