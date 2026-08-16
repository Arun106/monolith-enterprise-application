pipeline {

    agent {
        label 'PVM1'
    }

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
         * Jenkins agent Java.
         * Maven uses Java 17 inside Docker.
         */
        JAVA_HOME = '/usr/lib/jvm/java-17-openjdk-amd64'

        /*
         * IMPORTANT:
         * Maven now uses Java 17.
         */
        MAVEN_IMAGE = 'maven:3.9.13-eclipse-temurin-17-noble'

        /*
         * SonarQube
         */
        SONAR_CREDENTIALS_ID = 'sonar-token'

        /*
         * Docker image names
         */
        BACKEND_IMAGE = 'snowman-backend'

        FRONTEND_IMAGE = 'snowman-frontend'
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

                sh '''
                    #!/bin/bash

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

                    echo "========================================"
                    echo "HOST JAVA"
                    echo "========================================"

                    java -version

                    echo "========================================"
                    echo "DOCKER"
                    echo "========================================"

                    docker --version

                    echo "========================================"
                    echo "KUBECTL"
                    echo "========================================"

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

                    kubectl version --client

                    echo "========================================"
                    echo "AGENT PREREQUISITES PASSED"
                    echo "========================================"
                '''
            }
        }


        // ============================================================
        // BUILD AND UNIT TEST
        // ============================================================

        stage('Build and unit test') {

            steps {

                sh '''
                    #!/bin/bash

                    set -euo pipefail

                    echo "========================================"
                    echo "MAVEN BUILD"
                    echo "========================================"

                    echo "Jenkins user: $(whoami)"
                    echo "UID: $(id -u)"
                    echo "GID: $(id -g)"
                    echo "HOME: $HOME"
                    echo "WORKSPACE: $WORKSPACE"

                    echo "========================================"
                    echo "MAVEN IMAGE"
                    echo "========================================"

                    echo "$MAVEN_IMAGE"

                    echo "========================================"
                    echo "JAVA VERSION INSIDE MAVEN"
                    echo "========================================"

                    docker run --rm \
                        "$MAVEN_IMAGE" \
                        java -version

                    echo "========================================"
                    echo "RUNNING MAVEN"
                    echo "========================================"

                    docker run --rm \
                        --user "$(id -u):$(id -g)" \
                        -e HOME=/tmp/jenkins-user \
                        -v "$WORKSPACE:/workspace" \
                        -v maven-repository:/maven-repository \
                        -w /workspace \
                        "$MAVEN_IMAGE" \
                        sh -c '
                            set -e

                            mkdir -p /tmp/jenkins-user

                            mvn \
                                --batch-mode \
                                --no-transfer-progress \
                                -Duser.home=/tmp/jenkins-user \
                                -Dmaven.repo.local=/maven-repository \
                                -Dliquibase.should.run=false \
                                clean test
                        '
                '''
            }

            post {

                always {

                    junit(
                        allowEmptyResults: true,
                        testResults: 'target/surefire-reports/*.xml'
                    )
                }
            }
        }


        // ============================================================
        // SONARQUBE ANALYSIS
        // ============================================================

        stage('SonarQube analysis') {

            steps {

                withCredentials([
                    string(
                        credentialsId: env.SONAR_CREDENTIALS_ID,
                        variable: 'SONAR_TOKEN'
                    )
                ]) {

                    sh '''
                        #!/bin/bash

                        set -euo pipefail

                        echo "========================================"
                        echo "SONARQUBE ANALYSIS"
                        echo "========================================"

                        docker run --rm \
                            --user "$(id -u):$(id -g)" \
                            -e HOME=/tmp/jenkins-user \
                            -e SONAR_TOKEN="$SONAR_TOKEN" \
                            -v "$WORKSPACE:/workspace" \
                            -v maven-repository:/maven-repository \
                            -w /workspace \
                            "$MAVEN_IMAGE" \
                            sh -c '
                                set -e

                                mkdir -p /tmp/jenkins-user

                                mvn \
                                    --batch-mode \
                                    --no-transfer-progress \
                                    -Duser.home=/tmp/jenkins-user \
                                    -Dmaven.repo.local=/maven-repository \
                                    -Dsonar.token="$SONAR_TOKEN" \
                                    sonar:sonar
                            '
                    '''
                }
            }
        }


        // ============================================================
        // OWASP DEPENDENCY AUDIT
        // ============================================================

        stage('OWASP dependency audit') {

            steps {

                sh '''
                    #!/bin/bash

                    set -euo pipefail

                    echo "========================================"
                    echo "OWASP DEPENDENCY CHECK"
                    echo "========================================"

                    docker run --rm \
                        --user "$(id -u):$(id -g)" \
                        -e HOME=/tmp/jenkins-user \
                        -v "$WORKSPACE:/workspace" \
                        -v maven-repository:/maven-repository \
                        -w /workspace \
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
        // QUALITY GATE
        // ============================================================

        stage('Quality gate') {

            steps {

                withCredentials([
                    string(
                        credentialsId: env.SONAR_CREDENTIALS_ID,
                        variable: 'SONAR_TOKEN'
                    )
                ]) {

                    sh '''
                        #!/bin/bash

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
                                    -u "$SONAR_TOKEN:" \
                                    "$ce_task_url"
                            )"

                            task_status="$(
                                printf '%s' "$task_response" |
                                sed -n 's/.*"status":"\\([^"]*\\)".*/\\1/p'
                            )"

                            echo "Task status: $task_status"

                            if [ "$task_status" = "SUCCESS" ]; then

                                analysis_id="$(
                                    printf '%s' "$task_response" |
                                    sed -n 's/.*"analysisId":"\\([^"]*\\)".*/\\1/p'
                                )"

                                break

                            elif [ "$task_status" = "FAILED" ]; then

                                echo "ERROR: SonarQube analysis failed"
                                exit 1

                            elif [ "$task_status" = "CANCELED" ]; then

                                echo "ERROR: SonarQube analysis canceled"
                                exit 1

                            fi

                            sleep 5

                        done

                        if [ -z "$analysis_id" ]; then
                            echo "ERROR: SonarQube analysis timed out"
                            exit 1
                        fi

                        echo "Analysis ID: $analysis_id"
                    '''
                }
            }
        }


        // ============================================================
        // VALIDATE DELIVERY CONFIGURATION
        // ============================================================

        stage('Validate delivery configuration') {

            when {
                expression {
                    params.DEPLOY_TARGET != 'none'
                }
            }

            steps {

                sh '''
                    #!/bin/bash

                    set -euo pipefail

                    echo "========================================"
                    echo "VALIDATING DELIVERY CONFIGURATION"
                    echo "========================================"

                    echo "Deployment target: $DEPLOY_TARGET"
                    echo "ACR: $ACR_LOGIN_SERVER"

                    if [ "$DEPLOY_TARGET" = "aks" ]; then

                        echo "AKS resource group: $AKS_RESOURCE_GROUP"
                        echo "AKS cluster: $AKS_CLUSTER_NAME"

                    fi
                '''
            }
        }


        // ============================================================
        // BUILD CONTAINER IMAGES
        // ============================================================

        stage('Build container images') {

            when {
                expression {
                    params.DEPLOY_TARGET != 'none'
                }
            }

            steps {

                sh '''
                    #!/bin/bash

                    set -euo pipefail

                    echo "========================================"
                    echo "BUILDING CONTAINER IMAGES"
                    echo "========================================"

                    docker build \
                        -t "$ACR_LOGIN_SERVER/$BACKEND_IMAGE:$IMAGE_TAG" \
                        .

                    echo "Container image built successfully"

                    docker images | head
                '''
            }
        }


        // ============================================================
        // CONTAINER VULNERABILITY SCAN
        // ============================================================

        stage('Container vulnerability scan') {

            when {
                expression {
                    params.DEPLOY_TARGET != 'none'
                }
            }

            steps {

                sh '''
                    #!/bin/bash

                    set -euo pipefail

                    echo "========================================"
                    echo "CONTAINER VULNERABILITY SCAN"
                    echo "========================================"

                    if command -v trivy >/dev/null 2>&1; then

                        trivy image \
                            --exit-code 0 \
                            --severity HIGH,CRITICAL \
                            "$ACR_LOGIN_SERVER/$BACKEND_IMAGE:$IMAGE_TAG"

                    else

                        echo "WARNING: Trivy is not installed."
                        echo "Skipping container vulnerability scan."

                    fi
                '''
            }
        }


        // ============================================================
        // PUBLISH IMAGES TO ACR
        // ============================================================

        stage('Publish images to ACR') {

            when {
                expression {
                    params.DEPLOY_TARGET in ['acr', 'aks']
                }
            }

            steps {

                withCredentials([
                    usernamePassword(
                        credentialsId: params.ACR_CREDENTIALS_ID,
                        usernameVariable: 'ACR_USERNAME',
                        passwordVariable: 'ACR_PASSWORD'
                    )
                ]) {

                    sh '''
                        #!/bin/bash

                        set -euo pipefail

                        echo "========================================"
                        echo "PUBLISHING IMAGE TO ACR"
                        echo "========================================"

                        echo "$ACR_PASSWORD" |
                            docker login "$ACR_LOGIN_SERVER" \
                                --username "$ACR_USERNAME" \
                                --password-stdin

                        docker push \
                            "$ACR_LOGIN_SERVER/$BACKEND_IMAGE:$IMAGE_TAG"

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

                withCredentials([
                    azureServicePrincipal(
                        credentialsId: params.AZURE_CREDENTIALS_ID,
                        subscriptionIdVariable: 'AZURE_SUBSCRIPTION_ID',
                        clientIdVariable: 'AZURE_CLIENT_ID',
                        clientSecretVariable: 'AZURE_CLIENT_SECRET',
                        tenantIdVariable: 'AZURE_TENANT_ID'
                    )
                ]) {

                    sh '''
                        #!/bin/bash

                        set -euo pipefail

                        echo "========================================"
                        echo "DEPLOYING TO AKS"
                        echo "========================================"

                        az login \
                            --service-principal \
                            --username "$AZURE_CLIENT_ID" \
                            --password "$AZURE_CLIENT_SECRET" \
                            --tenant "$AZURE_TENANT_ID"

                        az account set \
                            --subscription "$AZURE_SUBSCRIPTION_ID"

                        az aks get-credentials \
                            --resource-group "$AKS_RESOURCE_GROUP" \
                            --name "$AKS_CLUSTER_NAME" \
                            --overwrite-existing

                        kubectl get nodes

                        echo "Deploying image:"
                        echo "$ACR_LOGIN_SERVER/$BACKEND_IMAGE:$IMAGE_TAG"

                        kubectl set image \
                            deployment/snowman-backend \
                            snowman-backend="$ACR_LOGIN_SERVER/$BACKEND_IMAGE:$IMAGE_TAG"

                        kubectl rollout status \
                            deployment/snowman-backend \
                            --timeout=5m
                    '''
                }
            }
        }
    }


    // ================================================================
    // POST ACTIONS
    // ================================================================

    post {

        always {

            echo "========================================"
            echo "PIPELINE FINISHED"
            echo "========================================"

            echo "BUILD NUMBER: ${env.BUILD_NUMBER}"
            echo "RESULT: ${currentBuild.currentResult}"

            archiveArtifacts(
                allowEmptyArchive: true,
                artifacts: '**/target/*.jar'
            )

            deleteDir()
        }

        success {

            echo "SNOWMAN ENTERPRISE CICD PIPELINE SUCCEEDED"
        }

        failure {

            echo "SNOWMAN ENTERPRISE CICD PIPELINE FAILED"
            echo "Check the stage that failed in the Jenkins console."
        }
    }
}
