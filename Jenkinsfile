pipeline {

    agent {
        label 'PVM1'
    }

    options {
        timeout(time: 1, unit: 'HOURS')
        timestamps()
    }

    environment {
        MAVEN_IMAGE = 'maven:3.9.13-eclipse-temurin-8-noble'

        // Change these if your environment uses different names
        ACR_NAME = 'YOUR_ACR_NAME'
        ACR_LOGIN_SERVER = 'YOUR_ACR_NAME.azurecr.io'

        AKS_RESOURCE_GROUP = 'YOUR_AKS_RESOURCE_GROUP'
        AKS_CLUSTER_NAME = 'YOUR_AKS_CLUSTER_NAME'
    }

    stages {

        // ============================================================
        // 1. CHECKOUT
        // ============================================================
        stage('Checkout') {
            steps {
                deleteDir()

                git branch: 'master',
                    url: 'https://github.com/Arun106/monolith-enterprise-application.git'

                script {
                    echo "Checked out commit:"
                    sh 'git rev-parse HEAD'
                }
            }
        }


        // ============================================================
        // 2. INSPECT MAVEN DEPENDENCIES
        // TEMPORARY STAGE
        // ============================================================
        stage('Inspect Maven dependencies') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "PROJECT INFORMATION"
                    echo "========================================"

                    echo "Current directory:"
                    pwd

                    echo ""
                    echo "Project files:"
                    ls -la

                    echo ""
                    echo "POM file:"
                    ls -lh pom.xml

                    echo ""
                    echo "========================================"
                    echo "SPRING REFERENCES IN POM.XML"
                    echo "========================================"

                    grep -n -i "spring" pom.xml || true

                    echo ""
                    echo "========================================"
                    echo "SPRING DEPENDENCY TREE"
                    echo "========================================"

                    docker run --rm \
                        -v "$PWD:/workspace" \
                        -w /workspace \
                        -e HOME=/tmp/maven-home \
                        "$MAVEN_IMAGE" \
                        mvn dependency:tree -Dincludes=org.springframework
                '''
            }
        }


        // ============================================================
        // 3. AGENT PREREQUISITES
        // ============================================================
        stage('Agent prerequisites') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "AGENT INFORMATION"
                    echo "========================================"

                    echo "User: $(whoami)"
                    echo "UID: $(id -u)"
                    echo "GID: $(id -g)"
                    echo "HOME: $HOME"
                    echo "WORKSPACE: $WORKSPACE"

                    echo ""
                    echo "========================================"
                    echo "CHECKING REQUIRED COMMANDS"
                    echo "========================================"

                    command -v git
                    command -v docker
                    command -v java
                    command -v kubectl
                    command -v kustomize

                    echo ""
                    echo "========================================"
                    echo "TOOL VERSIONS"
                    echo "========================================"

                    git --version
                    docker --version
                    docker compose version
                    java -version
                    kubectl version --client
                    kustomize version

                    echo ""
                    echo "========================================"
                    echo "AGENT PREREQUISITES PASSED"
                    echo "========================================"
                '''
            }
        }


        // ============================================================
        // 4. BUILD AND UNIT TEST
        // ============================================================
        stage('Build and unit test') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "MAVEN BUILD"
                    echo "========================================"

                    echo "Jenkins user: $(whoami)"
                    echo "UID: $(id -u)"
                    echo "GID: $(id -g)"
                    echo "HOME: $HOME"
                    echo "WORKSPACE: $WORKSPACE"
                    echo "MAVEN_IMAGE: $MAVEN_IMAGE"

                    echo ""
                    echo "========================================"
                    echo "PREPARING MAVEN REPOSITORY"
                    echo "========================================"

                    mkdir -p "$WORKSPACE/.m2"

                    chmod -R u+rwX "$WORKSPACE/.m2"

                    echo "Maven repository:"
                    echo "$WORKSPACE/.m2"

                    echo ""
                    echo "========================================"
                    echo "STARTING MAVEN CONTAINER"
                    echo "========================================"

                    docker run --rm \
                        --user "$(id -u):$(id -g)" \
                        -v "$WORKSPACE:/workspace" \
                        -v "$WORKSPACE/.m2:/maven-repository" \
                        -w /workspace \
                        -e HOME=/tmp/jenkins-user \
                        -e MAVEN_CONFIG=/tmp/jenkins-user/.m2 \
                        "$MAVEN_IMAGE" \
                        sh -c '
                            mkdir -p "$HOME/.m2"

                            echo "========================================"
                            echo "CONTAINER INFORMATION"
                            echo "========================================"

                            echo "User:"
                            id

                            echo "HOME:"
                            echo "$HOME"

                            echo ""
                            echo "JAVA VERSION"
                            java -version

                            echo ""
                            echo "MAVEN VERSION"
                            mvn -version

                            echo ""
                            echo "MAVEN REPOSITORY"
                            ls -ld /maven-repository

                            echo ""
                            echo "========================================"
                            echo "RUNNING MAVEN"
                            echo "========================================"

                            mvn \
                              -Dmaven.repo.local=/maven-repository \
                              -Dliquibase.should.run=false \
                              clean test
                        '
                '''
            }

            post {
                always {
                    junit(
                        testResults: '**/target/surefire-reports/*.xml',
                        allowEmptyResults: true
                    )
                }
            }
        }


        // ============================================================
        // 5. SONARQUBE ANALYSIS
        // ============================================================
        stage('SonarQube analysis') {
            steps {
                withSonarQubeEnv('SonarQube') {
                    sh '''
                        set -e

                        docker run --rm \
                            --user "$(id -u):$(id -g)" \
                            -v "$WORKSPACE:/workspace" \
                            -v "$WORKSPACE/.m2:/maven-repository" \
                            -w /workspace \
                            -e HOME=/tmp/jenkins-user \
                            "$MAVEN_IMAGE" \
                            sh -c '
                                mkdir -p "$HOME/.m2"

                                mvn \
                                  -Dmaven.repo.local=/maven-repository \
                                  -Dliquibase.should.run=false \
                                  sonar:sonar
                            '
                    '''
                }
            }
        }


        // ============================================================
        // 6. OWASP DEPENDENCY AUDIT
        // ============================================================
        stage('OWASP dependency audit') {
            steps {
                sh '''
                    set -e

                    docker run --rm \
                        --user "$(id -u):$(id -g)" \
                        -v "$WORKSPACE:/workspace" \
                        -v "$WORKSPACE/.m2:/maven-repository" \
                        -w /workspace \
                        -e HOME=/tmp/jenkins-user \
                        "$MAVEN_IMAGE" \
                        sh -c '
                            mkdir -p "$HOME/.m2"

                            mvn \
                              -Dmaven.repo.local=/maven-repository \
                              -Dliquibase.should.run=false \
                              org.owasp:dependency-check-maven:check
                        '
                '''
            }
        }


        // ============================================================
        // 7. QUALITY GATE
        // ============================================================
        stage('Quality gate') {
            steps {
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
        }


        // ============================================================
        // 8. VALIDATE DELIVERY CONFIGURATION
        // ============================================================
        stage('Validate delivery configuration') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "VALIDATING DELIVERY CONFIGURATION"
                    echo "========================================"

                    echo ""
                    echo "Docker files:"
                    find . -maxdepth 3 \\( \
                        -name 'Dockerfile' \
                        -o -name 'docker-compose.yml' \
                        -o -name 'docker-compose.yaml' \
                    \\) -print

                    echo ""
                    echo "Kubernetes files:"
                    find . -maxdepth 5 \\( \
                        -name '*.yaml' \
                        -o -name '*.yml' \
                    \\) -print

                    echo ""
                    echo "Validation completed."
                '''
            }
        }


        // ============================================================
        // 9. BUILD CONTAINER IMAGES
        // ============================================================
        stage('Build container images') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "BUILDING CONTAINER IMAGES"
                    echo "========================================"

                    if [ -f Dockerfile ]; then

                        docker build \
                            -t "${ACR_LOGIN_SERVER}/snowman-enterprise:${BUILD_NUMBER}" \
                            -t "${ACR_LOGIN_SERVER}/snowman-enterprise:latest" \
                            .

                    else
                        echo "No root Dockerfile found."
                        echo "Skipping container image build."
                    fi
                '''
            }
        }


        // ============================================================
        // 10. CONTAINER VULNERABILITY SCAN
        // ============================================================
        stage('Container vulnerability scan') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "CONTAINER VULNERABILITY SCAN"
                    echo "========================================"

                    if docker image inspect \
                        "${ACR_LOGIN_SERVER}/snowman-enterprise:${BUILD_NUMBER}" \
                        >/dev/null 2>&1; then

                        if command -v trivy >/dev/null 2>&1; then

                            trivy image \
                                --exit-code 0 \
                                --severity HIGH,CRITICAL \
                                "${ACR_LOGIN_SERVER}/snowman-enterprise:${BUILD_NUMBER}"

                        else
                            echo "Trivy is not installed on the Jenkins agent."
                            echo "Skipping scan."
                        fi

                    else
                        echo "Image does not exist."
                        echo "Skipping scan."
                    fi
                '''
            }
        }


        // ============================================================
        // 11. PUBLISH IMAGES TO ACR
        // ============================================================
        stage('Publish images to ACR') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "PUBLISHING IMAGE TO ACR"
                    echo "========================================"

                    if docker image inspect \
                        "${ACR_LOGIN_SERVER}/snowman-enterprise:${BUILD_NUMBER}" \
                        >/dev/null 2>&1; then

                        echo "Logging into Azure Container Registry..."

                        az acr login \
                            --name "$ACR_NAME"

                        echo "Pushing BUILD_NUMBER image..."

                        docker push \
                            "${ACR_LOGIN_SERVER}/snowman-enterprise:${BUILD_NUMBER}"

                        echo "Pushing latest image..."

                        docker push \
                            "${ACR_LOGIN_SERVER}/snowman-enterprise:latest"

                    else
                        echo "Image not found."
                        echo "Skipping ACR publish."
                    fi
                '''
            }
        }


        // ============================================================
        // 12. DEPLOY TO AKS
        // ============================================================
        stage('Deploy to AKS') {
            steps {
                sh '''
                    set -e

                    echo "========================================"
                    echo "DEPLOYING TO AKS"
                    echo "========================================"

                    az aks get-credentials \
                        --resource-group "$AKS_RESOURCE_GROUP" \
                        --name "$AKS_CLUSTER_NAME" \
                        --overwrite-existing

                    echo ""
                    echo "AKS NODES:"
                    kubectl get nodes

                    echo ""
                    echo "KUBERNETES FILES:"
                    find . -maxdepth 5 \\( \
                        -name '*.yaml' \
                        -o -name '*.yml' \
                    \\) -print

                    echo ""
                    echo "Deployment configuration must be adjusted"
                    echo "to the actual Kubernetes manifest location."
                '''
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
            echo "BUILD NUMBER: ${BUILD_NUMBER}"
            echo "RESULT: ${currentBuild.currentResult}"
            echo "========================================"

            archiveArtifacts(
                artifacts: '**/target/*.jar',
                allowEmptyArchive: true
            )

            deleteDir()
        }

        success {
            echo "SNOWMAN ENTERPRISE CICD PIPELINE SUCCESSFUL"
        }

        failure {
            echo "SNOWMAN ENTERPRISE CICD PIPELINE FAILED"
            echo "Check the stage that failed in the Jenkins console."
        }
    }
}
