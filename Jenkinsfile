// FinSecure Pay CI/CD pipeline
// Checkout -> Build -> Test -> Package -> Security Checks -> Container Build -> Image Scan
//   -> Push -> Deploy -> Smoke Test
// Any failing stage stops the pipeline, so a failed test or a HIGH/CRITICAL
// vulnerability never reaches Push or Deploy.

pipeline {
    agent any

    options {
        timestamps()
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 40, unit: 'MINUTES')
    }

    // Check GitHub for new commits every 2 minutes (Jenkins runs locally, so
    // GitHub cannot reach it with a webhook unless a tunnel is used)
    triggers {
        pollSCM('H/2 * * * *')
    }

    environment {
        IMAGE       = 'gnsgswgc/finsecure-pay'
        VERSION     = "1.0.${BUILD_NUMBER}"
        CLUSTER     = 'finsecure-eks'
        AWS_REGION  = 'ap-southeast-1'
        NAMESPACE   = 'finsecure'
        KUBECONFIG  = "${WORKSPACE}/.kube/config"
        // Keep downloaded Maven libraries between builds
        MAVEN_REPO  = '/var/jenkins_home/.m2/repository'
        // Trivy runs as a container that shares Jenkins' volumes, so it can
        // read the workspace and the Docker socket
        TRIVY       = 'docker run --rm --volumes-from finsecure-jenkins -v trivy-cache:/root/.cache aquasec/trivy:0.69.3'
        // Security gate: block on HIGH/CRITICAL issues that have a fix available
        TRIVY_GATE  = '--scanners vuln --severity HIGH,CRITICAL --ignore-unfixed --exit-code 1 --format table'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
                sh 'git log -1 --pretty=format:"%h %an: %s"'
            }
        }

        stage('Build') {
            steps {
                sh './mvnw -B -q -Dmaven.repo.local=$MAVEN_REPO clean compile'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B -Dmaven.repo.local=$MAVEN_REPO test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Package') {
            steps {
                sh './mvnw -B -q -Dmaven.repo.local=$MAVEN_REPO package -DskipTests'
                archiveArtifacts artifacts: 'target/finsecure-pay.jar', fingerprint: true
            }
        }

        stage('Security Checks') {
            parallel {
                stage('Code Quality (SonarQube Cloud)') {
                    steps {
                        withCredentials([string(credentialsId: 'sonar-token', variable: 'SONAR_TOKEN')]) {
                            // qualitygate.wait makes the build fail if the Quality Gate fails
                            sh '''
                                ./mvnw -B -Dmaven.repo.local=$MAVEN_REPO sonar:sonar \
                                  -Dsonar.token=$SONAR_TOKEN \
                                  -Dsonar.projectVersion=$VERSION \
                                  -Dsonar.qualitygate.wait=true \
                                  -Dsonar.qualitygate.timeout=300
                            '''
                        }
                    }
                }
                stage('Dependency Scan (Trivy)') {
                    steps {
                        // Scans every library packaged in the jar against known CVEs
                        sh '''
                            mkdir -p reports
                            set +e
                            $TRIVY rootfs $TRIVY_GATE --ignorefile $WORKSPACE/.trivyignore \
                              --output $WORKSPACE/reports/trivy-dependencies.txt \
                              $WORKSPACE/target/finsecure-pay.jar
                            RC=$?
                            cat reports/trivy-dependencies.txt
                            [ $RC -ne 0 ] && echo "SECURITY GATE: HIGH/CRITICAL vulnerable dependencies found - release blocked"
                            exit $RC
                        '''
                    }
                }
            }
        }

        stage('Container Build') {
            steps {
                // EKS nodes are amd64; Docker Desktop cross-builds on Apple Silicon
                sh '''
                    docker build --pull --platform linux/amd64 \
                      --build-arg APP_VERSION=$VERSION \
                      -t $IMAGE:$VERSION .
                '''
            }
        }

        stage('Image Scan (Trivy)') {
            steps {
                // Scans OS packages and libraries in the final image before it is pushed
                sh '''
                    mkdir -p reports
                    set +e
                    $TRIVY image $TRIVY_GATE --ignorefile $WORKSPACE/.trivyignore \
                      --output $WORKSPACE/reports/trivy-image.txt \
                      $IMAGE:$VERSION
                    RC=$?
                    cat reports/trivy-image.txt
                    [ $RC -ne 0 ] && echo "SECURITY GATE: HIGH/CRITICAL vulnerabilities in image - release blocked"
                    exit $RC
                '''
            }
        }

        stage('Push') {
            steps {
                withCredentials([usernamePassword(credentialsId: 'dockerhub',
                        usernameVariable: 'DOCKER_USER', passwordVariable: 'DOCKER_TOKEN')]) {
                    sh '''
                        echo "$DOCKER_TOKEN" | docker login -u "$DOCKER_USER" --password-stdin
                        docker push $IMAGE:$VERSION
                    '''
                }
            }
            post {
                always {
                    sh 'docker logout || true'
                }
            }
        }

        stage('Deploy') {
            steps {
                withCredentials([
                        string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                        string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh '''
                        aws eks update-kubeconfig --name $CLUSTER --region $AWS_REGION --kubeconfig $KUBECONFIG
                        kubectl apply -f k8s/namespace.yaml
                        kubectl apply -f k8s/configmap.yaml -f k8s/service.yaml

                        # Deploy this build's image; the manifest in Git keeps a placeholder tag
                        sed "s|image: $IMAGE:.*|image: $IMAGE:$VERSION|" k8s/deployment.yaml | kubectl apply -f -
                        kubectl -n $NAMESPACE annotate deployment/finsecure-pay \
                          kubernetes.io/change-cause="Jenkins build #$BUILD_NUMBER: $IMAGE:$VERSION" --overwrite

                        # If the new version never becomes healthy, roll back automatically
                        if ! kubectl -n $NAMESPACE rollout status deployment/finsecure-pay --timeout=300s; then
                          echo "Rollout failed - rolling back to the previous version"
                          kubectl -n $NAMESPACE rollout undo deployment/finsecure-pay
                          kubectl -n $NAMESPACE rollout status deployment/finsecure-pay --timeout=300s
                          exit 1
                        fi
                        kubectl -n $NAMESPACE get pods -o wide
                    '''
                }
            }
        }

        stage('Smoke Test') {
            steps {
                withCredentials([
                        string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                        string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')]) {
                    sh '''
                        HOST=$(kubectl -n $NAMESPACE get svc finsecure-pay -o jsonpath='{.status.loadBalancer.ingress[0].hostname}')
                        echo "Checking http://$HOST/health reports version $VERSION"
                        for i in $(seq 1 30); do
                          BODY=$(curl -s --max-time 5 http://$HOST/health || true)
                          echo "$BODY" | grep -q "\\"version\\":\\"$VERSION\\"" && { echo "OK: $BODY"; exit 0; }
                          sleep 5
                        done
                        echo "Smoke test failed: last response was $BODY"
                        exit 1
                    '''
                }
            }
        }
    }

    post {
        success {
            echo "Deployed ${IMAGE}:${VERSION} to ${CLUSTER}"
        }
        failure {
            echo "Pipeline failed - nothing after the failed stage was run"
        }
        always {
            archiveArtifacts artifacts: 'reports/**', allowEmptyArchive: true
            sh 'docker image rm $IMAGE:$VERSION >/dev/null 2>&1 || true'
            sh 'rm -rf $WORKSPACE/.kube'
        }
    }
}
