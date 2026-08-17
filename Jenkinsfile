pipeline {

    agent {
        label 'PVM1'
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()

        timeout(
            time: 60,
            unit: 'MINUTES'
        )

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
            defaultValue: 'agent/jenkins-aks-cicd',
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
            description: 'Optional Jenkins secret-text credential containing NVD API key'
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
            defaultValue: 'snowmanacr29173.azurecr.io',
            description: 'Azure Container Registry login server'
        )

        string(
            name: 'ACR_CREDENTIALS_ID',
            defaultValue: 'acr-snowman',
            description: 'Jenkins ACR username/password credential'
        )

        string(
            name: 'AKS_RESOURCE_GROUP',
            defaultValue: 'snowman-rg',
            description: 'AKS resource group'
        )

        string(
            name: 'AKS_CLUSTER_NAME',
            defaultValue: 'snowman-aks',
            description: 'AKS cluster name'
        )

        string(
            name: 'AZURE_TENANT_ID',
            defaultValue: '05a9af5a-38a8-4644-8268-229b2d91e517',
            description: 'Azure tenant ID'
        )

        string(
            name: 'AZURE_SUBSCRIPTION_ID',
            defaultValue: 'fdf5d975-00eb-4f5a-8602-9de647fa9493',
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

        // Legacy application build runtime
        MAVEN_IMAGE =
            'maven:3.9.13-eclipse-temurin-8-noble'

        // Sonar scanner runtime
        SONAR_MAVEN_IMAGE =
            'maven:3.9.13-eclipse-temurin-17'

        SONARQUBE_ENV =
            'naukri-sonarqube'

        SONAR_CREDENTIALS_ID =
            'sonarqube-snowman-token'

        PVM1_SONAR_URL =
            'http://127.0.0.1:9000'

        SONAR_PROJECT_KEY =
            'snowman-enterprise-monolith'

        APPLICATION_IMAGE =
            'monolith-enterprise-application'

        MIGRATION_IMAGE =
            'monolith-enterprise-application-migration'

        KUBECONFORM_IMAGE =
            'ghcr.io/yannh/kubeconform:v0.7.0'

        TRIVY_IMAGE =
            'aquasec/trivy:0.65.0'

        DEPLOYMENT_STARTED =
            'false'

        SECURITY_GATE_MODE =
            "${params.SECURITY_GATE_MODE ?: 'report-only'}"
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
                    set -eu

                    echo "========================================"
                    echo "AGENT PREREQUISITES"
                    echo "========================================"

                    if [ ! -f scripts/prerequisites.sh ]
                    then
                        echo "ERROR: scripts/prerequisites.sh not found"
                        exit 1
                    fi

                    chmod +x scripts/prerequisites.sh

                    ./scripts/prerequisites.sh

                    echo "========================================"
                    echo "PREREQUISITES PASSED"
                    echo "========================================"
                '''
            }
        }

        // ============================================================
        // BUILD + UNIT TEST
        // ============================================================

        stage('Build and unit test') {

            steps {

                sh '''
                    set -eu

                    echo "========================================"
                    echo "MAVEN BUILD - JAVA 8"
                    echo "========================================"

                    echo "User: $(whoami)"
                    echo "WORKSPACE: $WORKSPACE"
                    echo "MAVEN IMAGE: $MAVEN_IMAGE"

                    maven_repo="$WORKSPACE/.m2"

                    rm -rf "$maven_repo"
                    mkdir -p "$maven_repo"
                    chmod 755 "$maven_repo"

                    docker run \
                        --rm \
                        --user "$(id -u):$(id -g)" \
                        --env HOME=/tmp/jenkins-user \
                        --env MAVEN_CONFIG=/maven-repository \
                        --volume "$WORKSPACE:/workspace" \
                        --volume "$maven_repo:/maven-repository" \
                        --workdir /workspace \
                        "$MAVEN_IMAGE" \
                        sh -c '
                            set -e

                            mkdir -p /tmp/jenkins-user

                            echo "=============================="
                            echo "JAVA VERSION"
                            echo "=============================="

                            java -version

                            echo "=============================="
                            echo "MAVEN VERSION"
                            echo "=============================="

                            mvn -version

                            echo "=============================="
                            echo "RUNNING MAVEN BUILD"
                            echo "=============================="

                            mvn \
                                --batch-mode \
                                --no-transfer-progress \
                                -Duser.home=/tmp/jenkins-user \
                                -Dliquibase.should.run=false \
                                -Dmaven.repo.local=/maven-repository \
                                clean verify
                        '

                    echo "========================================"
                    echo "CHECK BUILD ARTIFACTS"
                    echo "========================================"

                    test -s target/Snowman.jar

                    ls -lh target/Snowman.jar

                    if [ -f target/site/jacoco/jacoco.xml ]
                    then
                        ls -lh target/site/jacoco/jacoco.xml
                    else
                        echo "WARNING: JaCoCo XML report not found"
                    fi
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
        // SONARQUBE - JAVA 17
        // ============================================================

        stage('SonarQube analysis') {

            steps {

                withSonarQubeEnv("${SONARQUBE_ENV}") {

                    withCredentials([
                        string(
                            credentialsId:
                                env.SONAR_CREDENTIALS_ID,

                            variable:
                                'SONAR_TOKEN'
                        )
                    ]) {

                        sh '''
                            set -eu

                            echo "========================================"
                            echo "SONARQUBE ANALYSIS - JAVA 17"
                            echo "========================================"

                            echo "Sonar URL: $PVM1_SONAR_URL"
                            echo "Sonar Maven image: $SONAR_MAVEN_IMAGE"

                            echo "Checking local SonarQube..."

                            curl \
                                --fail \
                                --silent \
                                --show-error \
                                "$PVM1_SONAR_URL/api/system/status"

                            echo ""

                            maven_repo="$WORKSPACE/.m2"

                            mkdir -p "$maven_repo"

                            docker run \
                                --rm \
                                --network host \
                                --user "$(id -u):$(id -g)" \
                                --env HOME=/tmp/jenkins-user \
                                --env MAVEN_CONFIG=/maven-repository \
                                --env SONAR_TOKEN="$SONAR_TOKEN" \
                                --env SONAR_HOST_URL="$PVM1_SONAR_URL" \
                                --env BUILD_NUMBER="$BUILD_NUMBER" \
                                --volume "$WORKSPACE:/workspace" \
                                --volume "$maven_repo:/maven-repository" \
                                --workdir /workspace \
                                "$SONAR_MAVEN_IMAGE" \
                                sh -c '
                                    set -e

                                    mkdir -p /tmp/jenkins-user

                                    echo "=============================="
                                    echo "SONAR JAVA VERSION"
                                    echo "=============================="

                                    java -version

                                    echo "=============================="
                                    echo "SONAR MAVEN VERSION"
                                    echo "=============================="

                                    mvn -version

                                    echo "=============================="
                                    echo "RUNNING SONAR ANALYSIS"
                                    echo "=============================="

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

                            echo "========================================"
                            echo "SONARQUBE ANALYSIS COMPLETE"
                            echo "========================================"
                        '''
                    }
                }
            }
        }

        // ============================================================
        // OWASP DEPENDENCY CHECK
        // ============================================================

        stage('OWASP dependency audit') {

            steps {

                script {

                    if (params.NVD_API_CREDENTIALS_ID?.trim()) {

                        withCredentials([
                            string(
                                credentialsId:
                                    params.NVD_API_CREDENTIALS_ID,

                                variable:
                                    'NVD_API_KEY'
                            )
                        ]) {

                            sh '''
                                set -eu

                                echo "========================================"
                                echo "OWASP DEPENDENCY CHECK"
                                echo "========================================"

                                maven_repo="$WORKSPACE/.m2"

                                mkdir -p "$maven_repo"

                                docker run \
                                    --rm \
                                    --user "$(id -u):$(id -g)" \
                                    --env HOME=/tmp/jenkins-user \
                                    --env MAVEN_CONFIG=/maven-repository \
                                    --env NVD_API_KEY="$NVD_API_KEY" \
                                    --env SECURITY_GATE_MODE="$SECURITY_GATE_MODE" \
                                    --volume "$WORKSPACE:/workspace" \
                                    --volume "$maven_repo:/maven-repository" \
                                    --workdir /workspace \
                                    "$SONAR_MAVEN_IMAGE" \
                                    sh -c '
                                        set -e

                                        mkdir -p /tmp/jenkins-user

                                        if [ "$SECURITY_GATE_MODE" = "strict" ]
                                        then
                                            CVSS=9.0
                                        else
                                            CVSS=11.0
                                        fi

                                        echo "CVSS threshold: $CVSS"

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

                        sh '''
                            set -eu

                            echo "========================================"
                            echo "OWASP DEPENDENCY CHECK"
                            echo "NO NVD API KEY CONFIGURED"
                            echo "========================================"

                            maven_repo="$WORKSPACE/.m2"

                            mkdir -p "$maven_repo"

                            docker run \
                                --rm \
                                --user "$(id -u):$(id -g)" \
                                --env HOME=/tmp/jenkins-user \
                                --env MAVEN_CONFIG=/maven-repository \
                                --env SECURITY_GATE_MODE="$SECURITY_GATE_MODE" \
                                --volume "$WORKSPACE:/workspace" \
                                --volume "$maven_repo:/maven-repository" \
                                --workdir /workspace \
                                "$SONAR_MAVEN_IMAGE" \
                                sh -c '
                                    set -e

                                    mkdir -p /tmp/jenkins-user

                                    if [ "$SECURITY_GATE_MODE" = "strict" ]
                                    then
                                        CVSS=9.0
                                    else
                                        CVSS=11.0
                                    fi

                                    echo "CVSS threshold: $CVSS"

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
                        artifacts:
                            'target/dependency-check-report.*'
                    )
                }
            }
        }

        // ============================================================
        // SONAR QUALITY GATE
        // ============================================================

        stage('Quality gate') {

            steps {

                withCredentials([
                    string(
                        credentialsId:
                            env.SONAR_CREDENTIALS_ID,

                        variable:
                            'SONAR_TOKEN'
                    )
                ]) {

                    sh '''
                        set -eu

                        echo "========================================"
                        echo "SONAR QUALITY GATE"
                        echo "========================================"

                        task_file="target/sonar/report-task.txt"

                        if [ ! -s "$task_file" ]
                        then
                            echo "ERROR: $task_file missing"
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

                        if [ -z "$ce_task_url" ]
                        then
                            echo "ERROR: ceTaskUrl not found"
                            exit 1
                        fi

                        analysis_id=""

                        for attempt in $(seq 1 60)
                        do

                            echo "Checking Sonar CE task: $attempt/60"

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

                            echo "CE Task Status: $task_status"

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

                                    echo "ERROR: Sonar background task failed"
                                    exit 1
                                    ;;

                                PENDING|IN_PROGRESS)

                                    sleep 5
                                    ;;

                                *)

                                    echo "ERROR: Unexpected status: $task_status"
                                    exit 1
                                    ;;

                            esac

                        done

                        if [ -z "$analysis_id" ]
                        then
                            echo "ERROR: Sonar analysis timed out"
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
                                --data-urlencode "analysisId=$analysis_id" \
                                "$sonar_base_url/api/qualitygates/project_status"
                        )"

                        gate_status="$(
                            printf '%s' "$gate_response" |
                            grep -o '"status":"[^"]*"' |
                            head -1 |
                            cut -d'"' -f4
                        )"

                        echo "Quality Gate Status: $gate_status"

                        if [ "$gate_status" != "OK" ]
                        then
                            echo "ERROR: Sonar Quality Gate failed"
                            exit 1
                        fi

                        echo "========================================"
                        echo "QUALITY GATE PASSED"
                        echo "========================================"
                    '''
                }
            }
        }

        // ============================================================
        // VALIDATE CONFIGURATION
        // ============================================================

        stage('Validate delivery configuration') {

            steps {

                sh '''
                    set -eu

                    echo "========================================"
                    echo "DOCKER COMPOSE VALIDATION"
                    echo "========================================"

                    docker compose config --quiet

                    echo "========================================"
                    echo "GENERATE KUBERNETES YAML"
                    echo "========================================"

                    kubectl kustomize \
                        k8s/overlays/dev \
                        > snowman-dev.yaml

                    kubectl kustomize \
                        k8s/overlays/production \
                        > snowman-production.yaml

                    kubectl kustomize \
                        k8s/jobs \
                        > snowman-migration.yaml

                    echo "========================================"
                    echo "KUBECONFORM VALIDATION"
                    echo "========================================"

                    for manifest in \
                        snowman-dev.yaml \
                        snowman-production.yaml \
                        snowman-migration.yaml
                    do

                        echo "Validating $manifest"

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
        // BUILD DOCKER IMAGES
        // ============================================================

        stage('Build container images') {

            steps {

                sh '''
                    set -eu

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
        // TRIVY
        // ============================================================

        stage('Container vulnerability scan') {

            steps {

                sh '''
                    set -eu

                    echo "========================================"
                    echo "TRIVY IMAGE SCAN"
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

                    if [ "$SECURITY_GATE_MODE" = "strict" ]
                    then

                        echo "Strict security gate enabled"

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

                        echo "Report-only security mode"

                    fi
                '''
            }

            post {

                always {

                    archiveArtifacts(
                        allowEmptyArchive: true,
                        artifacts:
                            'trivy-image-report.json'
                    )
                }
            }
        }

        // ============================================================
        // PUSH TO ACR
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

                withCredentials([

                    usernamePassword(
                        credentialsId:
                            params.ACR_CREDENTIALS_ID,

                        usernameVariable:
                            'ACR_USERNAME',

                        passwordVariable:
                            'ACR_PASSWORD'
                    )

                ]) {

                    sh '''
                        set -eu

                        echo "========================================"
                        echo "LOGIN TO ACR"
                        echo "========================================"

                        set +x

                        printf '%s' "$ACR_PASSWORD" |
                            docker login \
                                "$ACR_LOGIN_SERVER" \
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

                        docker logout \
                            "$ACR_LOGIN_SERVER"
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

                    sh '''
                        set -eu

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
                        echo "AZURE ACCOUNT"
                        echo "========================================"

                        az account show \
                            --query "{name:name,id:id,tenantId:tenantId}" \
                            --output table

                        echo "========================================"
                        echo "GET AKS CREDENTIALS"
                        echo "========================================"

                        az aks get-credentials \
                            --resource-group "$AKS_RESOURCE_GROUP" \
                            --name "$AKS_CLUSTER_NAME" \
                            --overwrite-existing

                        echo "========================================"
                        echo "CHECK AKS CONNECTION"
                        echo "========================================"

                        kubectl get nodes

                        echo "========================================"
                        echo "DEPLOY APPLICATION"
                        echo "========================================"

                        sed -i \
                            "s#ghcr.io/adikarthik/monolith-enterprise-application:latest#$ACR_LOGIN_SERVER/$APPLICATION_IMAGE:$IMAGE_TAG#g" \
                            k8s/base/deployment.yaml

                        kubectl apply -k k8s/overlays/dev

                        echo "========================================"
                        echo "DELETE OLD MIGRATION JOB"
                        echo "========================================"

                        kubectl delete job \
                            snowman-database-migration \
                            --namespace snowman \
                            --ignore-not-found=true

                        echo "========================================"
                        echo "CREATE MIGRATION JOB"
                        echo "========================================"

                        sed -i \
                            "s#ghcr.io/adikarthik/monolith-enterprise-application-migration:latest#$ACR_LOGIN_SERVER/$MIGRATION_IMAGE:$IMAGE_TAG#g" \
                            k8s/jobs/database-migration.yaml

                        kubectl apply -k k8s/jobs

                        echo "========================================"
                        echo "WAIT FOR MIGRATION"
                        echo "========================================"

                        if ! kubectl wait \
                            --namespace snowman \
                            --for=condition=complete \
                            job/snowman-database-migration \
                            --timeout=5m
                        then

                            echo "ERROR: Database migration failed"

                            kubectl describe job \
                                snowman-database-migration \
                                --namespace snowman || true

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
                            pods,services,ingress \
                            --namespace snowman \
                            -o wide

                        echo "========================================"
                        echo "SMOKE TEST"
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
                                http://snowman/health

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
    // POST
    // ================================================================

    post {

        success {

            echo """
========================================
CI/CD PIPELINE SUCCESSFUL
========================================

Build:
${env.BUILD_NUMBER}

Commit:
${env.GIT_COMMIT}

Image tag:
${env.IMAGE_TAG}

Application image:
${env.APPLICATION_IMAGE}:${env.IMAGE_TAG}

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

                    sh '''
                        echo "========================================"
                        echo "DEPLOYMENT FAILED"
                        echo "ATTEMPTING ROLLBACK"
                        echo "========================================"

                        kubectl rollout undo \
                            --namespace snowman \
                            deployment/snowman || true

                        kubectl rollout status \
                            --namespace snowman \
                            deployment/snowman \
                            --timeout=3m || true

                        az logout \
                            >/dev/null 2>&1 || true
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
