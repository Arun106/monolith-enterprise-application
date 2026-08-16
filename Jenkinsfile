pipeline {

    agent { label 'PVM1' }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 60, unit: 'MINUTES')

        buildDiscarder(
            logRotator(
                numToKeepStr: '20',
                artifactNumToKeepStr: '10'
            )
        )
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
            choices: [
                'none',
                'acr',
                'aks'
            ],
            description: 'none = CI only, acr = push images, aks = push and deploy'
        )

        string(
            name: 'NVD_API_CREDENTIALS_ID',
            defaultValue: '',
            description: 'Optional Jenkins secret-text credential containing an NVD API key'
        )

        choice(
            name: 'SECURITY_GATE_MODE',
            choices: [
                'report-only',
                'strict'
            ],
            description: 'Security scan mode'
        )

        string(
            name: 'ACR_LOGIN_SERVER',
            defaultValue: 'chunkhoundacr20260802.azurecr.io',
            description: 'Azure Container Registry login server'
        )

        string(
            name: 'ACR_CREDENTIALS_ID',
            defaultValue: 'acr-chunkhound',
            description: 'Jenkins ACR username/password credential'
        )

        string(
            name: 'AKS_RESOURCE_GROUP',
            defaultValue: 'Ar-RG',
            description: 'AKS resource group'
        )

        string(
            name: 'AKS_CLUSTER_NAME',
            defaultValue: 'Ar-AKS',
            description: 'AKS cluster name'
        )

        string(
            name: 'AZURE_TENANT_ID',
            defaultValue: 'pavip7547gmail.onmicrosoft.com',
            description: 'Azure tenant ID'
        )

        string(
            name: 'AZURE_SUBSCRIPTION_ID',
            defaultValue: 'ffcf8f61-5974-487d-95d9-9adf380c6233',
            description: 'Azure subscription ID'
        )

        string(
            name: 'AZURE_CREDENTIALS_ID',
            defaultValue: 'azure-sp',
            description: 'Azure service principal Jenkins credential'
        )
    }

    environment {

        CI = 'true'

        /*
         * Host Java is only used for Jenkins/agent utilities.
         * Maven runs inside Docker.
         */
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'

        /*
         * IMPORTANT:
         *
         * Do NOT define PATH here.
         *
         * Jenkins already has a working PATH.
         */
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


        // ============================================================
        // CHECKOUT
        // ============================================================

        stage('Checkout') {

            steps {

                deleteDir()

                git(
                    branch: params.REPOSITORY_BRANCH,
                    url: params.REPOSITORY_URL
                )

                script {

                    env.GIT_COMMIT = sh(
                        script: 'git rev-parse HEAD',
                        returnStdout: true
                    ).trim()

                    env.IMAGE_TAG =
                        "sha-${env.GIT_COMMIT.take(12)}-${env.BUILD_NUMBER}"

                    currentBuild.displayName =
                        "#${env.BUILD_NUMBER} ${env.GIT_COMMIT.take(8)}"
                }
            }
        }


        // ============================================================
        // AGENT PREREQUISITES
        // ============================================================

        stage('Agent prerequisites') {

            steps {

                sh '''#!/bin/bash
                    set -euo pipefail

                    echo "========================================"
                    echo "AGENT INFORMATION"
                    echo "========================================"

                    echo "User: $(whoami)"
                    echo "UID: $(id -u)"
                    echo "GID: $(id -g)"
                    echo "HOME: $HOME"
                    echo "WORKSPACE: $WORKSPACE"

                    echo "========================================"
                    echo "CHECKING REQUIRED COMMANDS"
                    echo "========================================"

                    for command in git docker curl java; do

                        if ! command -v "$command" >/dev/null 2>&1; then

                            echo "ERROR: Required command not found: $command"

                            exit 1

                        fi

                    done

                    /*
                     * Install kubectl for the Jenkins user if missing.
                     */

                    if ! command -v kubectl >/dev/null 2>&1; then

                        mkdir -p "$HOME/.local/bin"

                        curl \
                            --fail \
                            --silent \
                            --show-error \
                            --location \
                            https://dl.k8s.io/release/v1.36.2/bin/linux/amd64/kubectl \
                            --output "$HOME/.local/bin/kubectl"

                        chmod 0755 "$HOME/.local/bin/kubectl"

                    fi


                    /*
                     * Install Docker Compose plugin if missing.
                     */

                    if ! docker compose version >/dev/null 2>&1; then

                        mkdir -p "$HOME/.docker/cli-plugins"

                        curl \
                            --fail \
                            --silent \
                            --show-error \
                            --location \
                            "https://github.com/docker/compose/releases/download/v${DOCKER_COMPOSE_VERSION}/docker-compose-linux-x86_64" \
                            --output "$HOME/.docker/cli-plugins/docker-compose"

                        chmod 0755 "$HOME/.docker/cli-plugins/docker-compose"

                    fi


                    /*
                     * Azure CLI is required only for AKS deployment.
                     */

                    if [ "${DEPLOY_TARGET:-none}" = "aks" ]; then

                        if ! command -v az >/dev/null 2>&1; then

                            echo "ERROR: Azure CLI is required for AKS deployment"

                            exit 1

                        fi

                    fi


                    echo "========================================"
                    echo "TOOL VERSIONS"
                    echo "========================================"

                    git --version

                    docker --version

                    docker compose version

                    java -version

                    kubectl version --client
                '''
            }
        }


        // ============================================================
        // BUILD AND UNIT TEST
        //
        // Maven runs ONLY inside Docker.
        //
        // Host:
        //   $WORKSPACE/.m2
        //
        // Container:
        //   /maven-repository
        //
        // This prevents /root/.m2 permission problems.
        // ============================================================

        stage('Build and unit test') {

            steps {

                sh '''#!/bin/bash
                    set -euo pipefail

                    echo "========================================"
                    echo "MAVEN BUILD"
                    echo "========================================"

                    echo "Jenkins user: $(whoami)"
                    echo "UID: $(id -u)"
                    echo "GID: $(id -g)"
                    echo "HOME: $HOME"
                    echo "WORKSPACE: $WORKSPACE"
                    echo "MAVEN_IMAGE: $MAVEN_IMAGE"

                    echo "========================================"
                    echo "PREPARING MAVEN REPOSITORY"
                    echo "========================================"

                    maven_repo="$WORKSPACE/.m2"

                    rm -rf "$maven_repo"

                    mkdir -p "$maven_repo"

                    chmod 755 "$maven_repo"

                    echo "Maven repository:"
                    echo "$maven_repo"

                    echo "========================================"
                    echo "STARTING MAVEN CONTAINER"
                    echo "========================================"

                    docker run \
                        --rm \
                        --user "$(id -u):$(id -g)" \
                        --env HOME=/tmp/jenkins-user \
                        --volume "$WORKSPACE:/workspace" \
                        --volume "$maven_repo:/maven-repository" \
                        --workdir /workspace \
                        "$MAVEN_IMAGE" \
                        sh -c '
                            set -e

                            mkdir -p /tmp/jenkins-user

                            echo "========================================"
                            echo "CONTAINER INFORMATION"
                            echo "========================================"

                            echo "User:"
                            id

                            echo "HOME:"
                            echo "$HOME"

                            echo "========================================"
                            echo "JAVA VERSION"
                            echo "========================================"

                            java -version

                            echo "========================================"
                            echo "MAVEN VERSION"
                            echo "========================================"

                            mvn -version

                            echo "========================================"
                            echo "MAVEN REPOSITORY"
                            echo "========================================"

                            ls -ld /maven-repository

                            echo "========================================"
                            echo "RUNNING MAVEN"
                            echo "========================================"

                            mvn \
                                --batch-mode \
                                --no-transfer-progress \
                                -Duser.home=/tmp/jenkins-user \
                                -Dliquibase.should.run=false \
                                -Dmaven.repo.local=/maven-repository \
                                clean verify

                            echo "========================================"
                            echo "MAVEN BUILD SUCCESSFUL"
                            echo "========================================"
                        '


                    echo "========================================"
                    echo "CHECKING BUILD ARTIFACTS"
                    echo "========================================"

                    test -s target/Snowman.jar

                    test -s target/site/jacoco/jacoco.xml

                    echo "Snowman.jar:"
                    ls -lh target/Snowman.jar

                    echo "Jacoco report:"
                    ls -lh target/site/jacoco/jacoco.xml

                    echo "========================================"
                    echo "BUILD AND UNIT TEST SUCCESSFUL"
                    echo "========================================"
                '''
            }

            post {

                always {

                    junit(
                        allowEmptyResults: true,
                        testResults:
                            'target/surefire-reports/*.xml,target/failsafe-reports/*.xml'
                    )
                }
            }
        }


        // ============================================================
        // SONARQUBE
        // ============================================================

        stage('SonarQube analysis') {

            steps {

                withSonarQubeEnv("${SONARQUBE_ENV}") {

                    withCredentials([
                        string(
                            credentialsId: env.SONAR_CREDENTIALS_ID,
                            variable: 'SONAR_TOKEN'
                        )
                    ]) {

                        sh '''#!/bin/bash
                            set -euo pipefail

                            echo "========================================"
                            echo "SONARQUBE ANALYSIS"
                            echo "========================================"

                            maven_repo="$WORKSPACE/.m2"

                            mkdir -p "$maven_repo"

                            docker run \
                                --rm \
                                --user "$(id -u):$(id -g)" \
                                --env HOME=/tmp/jenkins-user \
                                --env SONAR_TOKEN="$SONAR_TOKEN" \
                                --env SONAR_HOST_URL="$PVM1_SONAR_URL" \
                                --env BUILD_NUMBER="$BUILD_NUMBER" \
                                --volume "$WORKSPACE:/workspace" \
                                --volume "$maven_repo:/maven-repository" \
                                --workdir /workspace \
                                "$MAVEN_IMAGE" \
                                sh -c '
                                    set -e

                                    mkdir -p /tmp/jenkins-user

                                    echo "Java:"
                                    java -version

                                    echo "Maven:"
                                    mvn -version

                                    echo "Running SonarQube..."

                                    mvn \
                                        --batch-mode \
                                        --no-transfer-progress \
                                        -Duser.home=/tmp/jenkins-user \
                                        -Dliquibase.should.run=false \
                                        -Dmaven.repo.local=/maven-repository \
                                        org.sonarsource.scanner.maven:sonar-maven-plugin:5.2.0.4988:sonar \
                                        -Dsonar.host.url="$SONAR_HOST_URL" \
                                        -Dsonar.projectKey="snowman-enterprise-monolith" \
                                        -Dsonar.projectName="Snowman Enterprise Monolith" \
                                        -Dsonar.projectVersion="1.0.$BUILD_NUMBER" \
                                        -Dsonar.token="$SONAR_TOKEN" \
                                        -Dsonar.java.binaries=target/classes \
                                        -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml
                                '

                            echo "SonarQube analysis completed."
                        '''
                    }
                }
            }
        }


        // ============================================================
        // OWASP DEPENDENCY AUDIT
        // ============================================================

        stage('OWASP dependency audit') {

            steps {

                script {

                    if (params.NVD_API_CREDENTIALS_ID?.trim()) {

                        withCredentials([
                            string(
                                credentialsId:
                                    params.NVD_API_CREDENTIALS_ID,
                                variable: 'NVD_API_KEY'
                            )
                        ]) {

                            sh '''#!/bin/bash
                                set -euo pipefail

                                echo "========================================"
                                echo "OWASP DEPENDENCY CHECK"
                                echo "========================================"

                                maven_repo="$WORKSPACE/.m2"

                                mkdir -p "$maven_repo"

                                docker run \
                                    --rm \
                                    --user "$(id -u):$(id -g)" \
                                    --env HOME=/tmp/jenkins-user \
                                    --env NVD_API_KEY="$NVD_API_KEY" \
                                    --env SECURITY_GATE_MODE="$SECURITY_GATE_MODE" \
                                    --volume "$WORKSPACE:/workspace" \
                                    --volume "$maven_repo:/maven-repository" \
                                    --workdir /workspace \
                                    "$MAVEN_IMAGE" \
                                    sh -c '
                                        set -e

                                        mkdir -p /tmp/jenkins-user

                                        if [ "$SECURITY_GATE_MODE" = "strict" ]; then
                                            CVSS=9.0
                                        else
                                            CVSS=11.0
                                        fi

                                        echo "CVSS failure threshold: $CVSS"

                                        mvn \
                                            --batch-mode \
                                            --no-transfer-progress \
                                            -Duser.home=/tmp/jenkins-user \
                                            -Dliquibase.should.run=false \
                                            -Dmaven.repo.local=/maven-repository \
                                            org.owasp:dependency-check-maven:12.1.8:check \
                                            -DskipTests \
                                            -Dformat=ALL \
                                            -DfailBuildOnCVSS="$CVSS" \
                                            -DnvdApiKey="$NVD_API_KEY"
                                    '
                            '''
                        }

                    } else {

                        echo "NVD API credential not configured."
                        echo "Running OWASP dependency check without NVD API key."

                        sh '''#!/bin/bash
                            set -euo pipefail

                            echo "========================================"
                            echo "OWASP DEPENDENCY CHECK"
                            echo "========================================"

                            maven_repo="$WORKSPACE/.m2"

                            mkdir -p "$maven_repo"

                            docker run \
                                --rm \
                                --user "$(id -u):$(id -g)" \
                                --env HOME=/tmp/jenkins-user \
                                --env SECURITY_GATE_MODE="$SECURITY_GATE_MODE" \
                                --volume "$WORKSPACE:/workspace" \
                                --volume "$maven_repo:/maven-repository" \
                                --workdir /workspace \
                                "$MAVEN_IMAGE" \
                                sh -c '
                                    set -e

                                    mkdir -p /tmp/jenkins-user

                                    if [ "$SECURITY_GATE_MODE" = "strict" ]; then
                                        CVSS=9.0
                                    else
                                        CVSS=11.0
                                    fi

                                    mvn \
                                        --batch-mode \
                                        --no-transfer-progress \
                                        -Duser.home=/tmp/jenkins-user \
                                        -Dliquibase.should.run=false \
                                        -Dmaven.repo.local=/maven-repository \
                                        org.owasp:dependency-check-maven:12.1.8:check \
                                        -DskipTests \
                                        -Dformat=ALL \
                                        -DfailBuildOnCVSS="$CVSS"
                                '
                        '''
                    }
                }
            }

            post {

                always {

                    archiveArtifacts(
                        allowEmptyArchive: true,
                        artifacts: 'target/dependency-check-report.*'
                    )
                }
            }
        }


        // ============================================================
        // SONARQUBE QUALITY GATE
        // ============================================================

        stage('Quality gate') {

            steps {

                withCredentials([
                    string(
                        credentialsId: env.SONAR_CREDENTIALS_ID,
                        variable: 'SONAR_TOKEN'
                    )
                ]) {

                    sh '''#!/bin/bash
                        set -euo pipefail

                        echo "========================================"
                        echo "SONARQUBE QUALITY GATE"
                        echo "========================================"

                        task_file="target/sonar/report-task.txt"

                        if [ ! -s "$task_file" ]; then

                            echo "ERROR: SonarQube report-task.txt not found"

                            exit 1

                        fi

                        ce_task_url="$(
                            awk '
                                /^ceTaskUrl=/ {
                                    sub(/^[^=]*=/, "")
                                    print
                                }
                            ' "$task_file"
                        )"

                        if [ -z "$ce_task_url" ]; then

                            echo "ERROR: ceTaskUrl not found"

                            exit 1

                        fi

                        echo "SonarQube compute task:"
                        echo "$ce_task_url"

                        analysis_id=""

                        for attempt in $(seq 1 60); do

                            echo "Checking SonarQube task - attempt $attempt/60"

                            task_response="$(
                                curl \
                                    --fail \
                                    --silent \
                                    --show-error \
                                    --user "$SONAR_TOKEN:" \
                                    "$ce_task_url"
                            )"

                            task_status="$(
                                printf '%s' "$task_response" |
                                grep -o '"status":"[^"]*"' |
                                head -1 |
                                cut -d'"' -f4
                            )"

                            echo "Task status: $task_status"

                            case "$task_status" in

                                SUCCESS)

                                    analysis_id="$(
                                        printf '%s' "$task_response" |
                                        grep -o '"analysisId":"[^"]*"' |
                                        head -1 |
                                        cut -d'"' -f4
                                    )"

                                    break
                                    ;;

                                FAILED|CANCELED)

                                    echo "SonarQube compute task failed."

                                    exit 1
                                    ;;

                                PENDING|IN_PROGRESS)

                                    sleep 5
                                    ;;

                                *)

                                    echo "Unexpected SonarQube status."

                                    exit 1
                                    ;;

                            esac

                        done

                        if [ -z "$analysis_id" ]; then

                            echo "Timed out waiting for SonarQube."

                            exit 1

                        fi

                        sonar_base_url="${ce_task_url%%/api/ce/task*}"

                        gate_response="$(
                            curl \
                                --fail \
                                --silent \
                                --show-error \
                                --user "$SONAR_TOKEN:" \
                                --get \
                                --data-urlencode \
                                "analysisId=$analysis_id" \
                                "$sonar_base_url/api/qualitygates/project_status"
                        )"

                        gate_status="$(
                            printf '%s' "$gate_response" |
                            grep -o '"status":"[^"]*"' |
                            head -1 |
                            cut -d'"' -f4
                        )"

                        echo "Quality Gate: $gate_status"

                        if [ "$gate_status" != "OK" ]; then

                            echo "ERROR: SonarQube Quality Gate failed."

                            exit 1

                        fi

                        echo "SonarQube Quality Gate PASSED."
                    '''
                }
            }
        }


        // ============================================================
        // VALIDATE DELIVERY CONFIGURATION
        // ============================================================

        stage('Validate delivery configuration') {

            steps {

                sh '''#!/bin/bash
                    set -euo pipefail

                    echo "========================================"
                    echo "VALIDATING DOCKER COMPOSE"
                    echo "========================================"

                    docker compose config --quiet


                    echo "========================================"
                    echo "GENERATING KUBERNETES MANIFESTS"
                    echo "========================================"

                    kubectl kustomize k8s/overlays/dev \
                        > snowman-dev.yaml

                    kubectl kustomize k8s/overlays/production \
                        > snowman-production.yaml

                    kubectl kustomize k8s/jobs \
                        > snowman-migration.yaml


                    echo "========================================"
                    echo "VALIDATING KUBERNETES MANIFESTS"
                    echo "========================================"

                    for manifest in \
                        snowman-dev.yaml \
                        snowman-production.yaml \
                        snowman-migration.yaml
                    do

                        echo "Validating: $manifest"

                        docker run \
                            --rm \
                            -i \
                            "$KUBECONFORM_IMAGE" \
                            -strict \
                            -summary \
                            -kubernetes-version 1.36.0 \
                            < "$manifest"

                    done
                '''
            }
        }


        // ============================================================
        // BUILD CONTAINER IMAGES
        // ============================================================

        stage('Build container images') {

            steps {

                sh '''#!/bin/bash
                    set -euo pipefail

                    echo "========================================"
                    echo "BUILD APPLICATION IMAGE"
                    echo "========================================"

                    docker build \
                        --tag "$APPLICATION_IMAGE:$IMAGE_TAG" \
                        --label "org.opencontainers.image.revision=$GIT_COMMIT" \
                        --label "org.opencontainers.image.version=$IMAGE_TAG" \
                        --file Dockerfile .


                    echo "========================================"
                    echo "BUILD MIGRATION IMAGE"
                    echo "========================================"

                    docker build \
                        --tag "$MIGRATION_IMAGE:$IMAGE_TAG" \
                        --label "org.opencontainers.image.revision=$GIT_COMMIT" \
                        --label "org.opencontainers.image.version=$IMAGE_TAG" \
                        --file Dockerfile.migration .
                '''
            }
        }


        // ============================================================
        // CONTAINER VULNERABILITY SCAN
        // ============================================================

        stage('Container vulnerability scan') {

            steps {

                sh '''#!/bin/bash
                    set -euo pipefail

                    echo "========================================"
                    echo "TRIVY CONTAINER SCAN"
                    echo "========================================"

                    mkdir -p "$WORKSPACE/.trivy-cache"

                    docker run \
                        --rm \
                        --user "$(id -u):$(id -g)" \
                        --group-add "$(stat -c %g /var/run/docker.sock)" \
                        --env HOME=/tmp \
                        --volume /var/run/docker.sock:/var/run/docker.sock \
                        --volume "$WORKSPACE/.trivy-cache:/tmp/trivy-cache" \
                        "$TRIVY_IMAGE" \
                        image \
                        --cache-dir /tmp/trivy-cache/trivy \
                        --ignore-unfixed \
                        --severity HIGH,CRITICAL \
                        --format json \
                        --output /tmp/trivy-cache/trivy-image-report.json \
                        "$APPLICATION_IMAGE:$IMAGE_TAG"


                    cp \
                        "$WORKSPACE/.trivy-cache/trivy-image-report.json" \
                        "$WORKSPACE/trivy-image-report.json"


                    if [ "$SECURITY_GATE_MODE" = "strict" ]; then

                        echo "Strict security mode enabled."

                        docker run \
                            --rm \
                            --user "$(id -u):$(id -g)" \
                            --group-add "$(stat -c %g /var/run/docker.sock)" \
                            --env HOME=/tmp \
                            --volume /var/run/docker.sock:/var/run/docker.sock \
                            --volume "$WORKSPACE/.trivy-cache:/tmp/trivy-cache" \
                            "$TRIVY_IMAGE" \
                            image \
                            --cache-dir /tmp/trivy-cache/trivy \
                            --ignore-unfixed \
                            --severity CRITICAL \
                            --exit-code 1 \
                            "$APPLICATION_IMAGE:$IMAGE_TAG"

                    else

                        echo "Security scans are report-only."

                    fi
                '''
            }

            post {

                always {

                    archiveArtifacts(
                        allowEmptyArchive: true,
                        artifacts: 'trivy-image-report.json'
                    )
                }
            }
        }


        // ============================================================
        // PUBLISH IMAGES TO ACR
        // ============================================================

        stage('Publish images to ACR') {

            when {

                expression {

                    params.DEPLOY_TARGET in [
                        'acr',
                        'aks'
                    ]
                }
            }

            steps {

                script {

                    if (!params.ACR_LOGIN_SERVER?.trim()) {

                        error(
                            'ACR_LOGIN_SERVER is required'
                        )
                    }

                    if (!params.ACR_CREDENTIALS_ID?.trim()) {

                        error(
                            'ACR_CREDENTIALS_ID is required'
                        )
                    }
                }


                withCredentials([
                    usernamePassword(
                        credentialsId: params.ACR_CREDENTIALS_ID,
                        usernameVariable: 'ACR_USERNAME',
                        passwordVariable: 'ACR_PASSWORD'
                    )
                ]) {

                    sh '''#!/bin/bash
                        set -euo pipefail

                        echo "========================================"
                        echo "LOGIN TO ACR"
                        echo "========================================"

                        set +x

                        printf '%s' "$ACR_PASSWORD" |
                            docker login "$ACR_LOGIN_SERVER" \
                                --username "$ACR_USERNAME" \
                                --password-stdin

                        set -x


                        echo "========================================"
                        echo "TAG APPLICATION IMAGE"
                        echo "========================================"

                        docker tag \
                            "$APPLICATION_IMAGE:$IMAGE_TAG" \
                            "$ACR_LOGIN_SERVER/$APPLICATION_IMAGE:$IMAGE_TAG"


                        echo "========================================"
                        echo "TAG MIGRATION IMAGE"
                        echo "========================================"

                        docker tag \
                            "$MIGRATION_IMAGE:$IMAGE_TAG" \
                            "$ACR_LOGIN_SERVER/$MIGRATION_IMAGE:$IMAGE_TAG"


                        echo "========================================"
                        echo "PUSH APPLICATION IMAGE"
                        echo "========================================"

                        docker push \
                            "$ACR_LOGIN_SERVER/$APPLICATION_IMAGE:$IMAGE_TAG"


                        echo "========================================"
                        echo "PUSH MIGRATION IMAGE"
                        echo "========================================"

                        docker push \
                            "$ACR_LOGIN_SERVER/$MIGRATION_IMAGE:$IMAGE_TAG"


                        docker logout "$ACR_LOGIN_SERVER"
                    '''
                }
            }
        }


        // ============================================================
        // DEPLOY TO AKS
        // ============================================================

        stage('Deploy to AKS') {

            when {

                expression {

                    params.DEPLOY_TARGET == 'aks'
                }
            }

            steps {

                script {

                    def required = [
                        ACR_LOGIN_SERVER:
                            params.ACR_LOGIN_SERVER,

                        AKS_RESOURCE_GROUP:
                            params.AKS_RESOURCE_GROUP,

                        AKS_CLUSTER_NAME:
                            params.AKS_CLUSTER_NAME,

                        AZURE_TENANT_ID:
                            params.AZURE_TENANT_ID,

                        AZURE_SUBSCRIPTION_ID:
                            params.AZURE_SUBSCRIPTION_ID
                    ]


                    def missing =
                        required
                            .findAll {
                                !it.value?.trim()
                            }
                            .keySet()


                    if (missing) {

                        error(
                            "Missing AKS parameters: ${missing.join(', ')}"
                        )
                    }


                    env.DEPLOYMENT_STARTED = 'true'
                }


                withCredentials([
                    usernamePassword(
                        credentialsId:
                            params.AZURE_CREDENTIALS_ID,

                        usernameVariable:
                            'AZURE_CLIENT_ID',

                        passwordVariable:
                            'AZURE_CLIENT_SECRET'
                    )
                ]) {

                    sh '''#!/bin/bash
                        set -euo pipefail

                        echo "========================================"
                        echo "AZURE LOGIN"
                        echo "========================================"

                        set +x

                        az login \
                            --service-principal \
                            --username "$AZURE_CLIENT_ID" \
                            --password "$AZURE_CLIENT_SECRET" \
                            --tenant "$AZURE_TENANT_ID" \
                            >/dev/null

                        set -x


                        az account set \
                            --subscription "$AZURE_SUBSCRIPTION_ID"


                        echo "========================================"
                        echo "GET AKS CREDENTIALS"
                        echo "========================================"

                        az aks get-credentials \
                            --resource-group "$AKS_RESOURCE_GROUP" \
                            --name "$AKS_CLUSTER_NAME" \
                            --overwrite-existing


                        echo "========================================"
                        echo "DEPLOY APPLICATION"
                        echo "========================================"

                        sed \
                            "s#ghcr.io/adikarthik/monolith-enterprise-application:latest#$ACR_LOGIN_SERVER/$APPLICATION_IMAGE:$IMAGE_TAG#g" \
                            snowman-production.yaml |
                            kubectl apply -f -


                        echo "========================================"
                        echo "DELETE OLD MIGRATION JOB"
                        echo "========================================"

                        kubectl delete job \
                            snowman-database-migration \
                            --namespace snowman \
                            --ignore-not-found=true


                        echo "========================================"
                        echo "DEPLOY DATABASE MIGRATION"
                        echo "========================================"

                        sed \
                            "s#ghcr.io/adikarthik/monolith-enterprise-application-migration:latest#$ACR_LOGIN_SERVER/$MIGRATION_IMAGE:$IMAGE_TAG#g" \
                            snowman-migration.yaml |
                            kubectl apply -f -


                        echo "========================================"
                        echo "WAIT FOR DATABASE MIGRATION"
                        echo "========================================"

                        if ! kubectl wait \
                            --namespace snowman \
                            --for=condition=complete \
                            job/snowman-database-migration \
                            --timeout=5m
                        then

                            echo "Migration failed."

                            kubectl logs \
                                --namespace snowman \
                                job/snowman-database-migration \
                                --all-containers=true || true

                            exit 1

                        fi


                        echo "========================================"
                        echo "WAIT FOR APPLICATION ROLLOUT"
                        echo "========================================"

                        kubectl rollout status \
                            --namespace snowman \
                            deployment/snowman \
                            --timeout=5m


                        echo "========================================"
                        echo "KUBERNETES RESOURCES"
                        echo "========================================"

                        kubectl get \
                            pods,service,ingress \
                            --namespace snowman \
                            -o wide


                        echo "========================================"
                        echo "APPLICATION SMOKE TEST"
                        echo "========================================"

                        kubectl delete pod \
                            "snowman-smoke-$BUILD_NUMBER" \
                            --namespace snowman \
                            --ignore-not-found=true


                        kubectl run \
                            "snowman-smoke-$BUILD_NUMBER" \
                            --namespace snowman \
                            --image=curlimages/curl:8.10.1 \
                            --restart=Never \
                            --rm \
                            --attach \
                            --command -- \
                            curl \
                                --fail \
                                --silent \
                                --show-error \
                                --retry 12 \
                                --retry-delay 5 \
                                http://snowman:8090/health


                        echo "========================================"
                        echo "AKS DEPLOYMENT SUCCESSFUL"
                        echo "========================================"

                        az logout
                    '''
                }
            }
        }
    }


    // ================================================================
    // POST ACTIONS
    // ================================================================

    post {

        success {

            echo """
            ========================================
            CI/CD COMPLETED SUCCESSFULLY
            ========================================

            Application:
                ${env.APPLICATION_IMAGE}:${env.IMAGE_TAG}

            Git commit:
                ${env.GIT_COMMIT}

            Build:
                ${env.BUILD_NUMBER}

            Deployment target:
                ${params.DEPLOY_TARGET}

            ========================================
            """
        }


        failure {

            script {

                if (
                    env.DEPLOYMENT_STARTED == 'true' &&
                    params.DEPLOY_TARGET == 'aks'
                ) {

                    sh '''#!/bin/bash

                        echo "========================================"
                        echo "DEPLOYMENT FAILED"
                        echo "ATTEMPTING ROLLBACK"
                        echo "========================================"

                        kubectl rollout undo \
                            --namespace snowman \
                            deployment/snowman || true

                        az logout >/dev/null 2>&1 || true
                    '''
                }
            }
        }


        always {

            archiveArtifacts(
                allowEmptyArchive: true,

                artifacts:
                    'target/Snowman.jar,' +
                    'target/site/jacoco/jacoco.xml,' +
                    'target/dependency-check-report.*,' +
                    'trivy-image-report.json,' +
                    'snowman-*.yaml',

                fingerprint: true
            )
        }


        cleanup {

            deleteDir()
        }
    }
}
