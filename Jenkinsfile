/**
 * WebRobot CLI (Picocli) — build Maven + Docker Hub push + deploy opzionale su K8s.
 *
 * Stages:
 *   Checkout → Setup → Tests → Package uber-jar → Build Docker (Kaniko) → Deploy to K8s
 *
 * Versione CLI (${revision}): default CI 0.3.<BUILD_NUMBER>.
 * Immagine Docker: docker.io/webrobot/webrobot-cli:<tag> (pubblico, Docker Hub).
 * Secret K8s per Docker Hub: dockerhub-config-secret (.dockerconfigjson → config.json).
 */
pipeline {
    agent {
        kubernetes {
            yaml '''
apiVersion: v1
kind: Pod
metadata:
  namespace: cicd
spec:
  containers:
  - name: maven
    image: maven:3.9.11-amazoncorretto-17
    command:
    - sleep
    args:
    - 99d
    resources:
      requests:
        memory: "2Gi"
        cpu: "1000m"
        ephemeral-storage: "2Gi"
      limits:
        memory: "4Gi"
        cpu: "2000m"
        ephemeral-storage: "4Gi"
    volumeMounts:
    - name: maven-repo
      mountPath: /root/.m2/repository
  - name: kaniko
    image: gcr.io/kaniko-project/executor:v1.9.0-debug
    command:
    - sleep
    args:
    - 99d
    resources:
      requests:
        memory: "4Gi"
        cpu: "1000m"
        ephemeral-storage: "6Gi"
      limits:
        memory: "8Gi"
        cpu: "2000m"
        ephemeral-storage: "10Gi"
    volumeMounts:
    - name: dockerhub-cfg
      mountPath: /kaniko/.docker
  volumes:
  - name: maven-repo
    persistentVolumeClaim:
      claimName: maven-repo-pvc
  - name: dockerhub-cfg
    projected:
      sources:
      - secret:
          name: docker-hub-webrobot
          items:
            - key: .dockerconfigjson
              path: config.json
'''
            defaultContainer 'maven'
        }
    }

    environment {
        GITHUB_REPOSITORY        = 'WebRobot-Ltd/WebRobot-CLI'
        MAVEN_CREDENTIALS        = 'github-token'
        MAVEN_SETTINGS_CONFIG    = '603a9990-8a95-4328-84f2-693f1c72212f'
        UBER_JAR_GLOB            = 'target/webrobot-cli-*-uber.jar'

        // Docker Hub — immagine pubblica
        DOCKER_IMAGE             = 'webrobot2022/webrobot-cli'
        DOCKER_REGISTRY          = 'docker.io'

        // Kubernetes
        K8S_NAMESPACE            = 'webrobot'
    }

    parameters {
        booleanParam(
            name: 'RUN_TESTS',
            defaultValue: false,
            description: 'Eseguire mvn test prima del package'
        )
        booleanParam(
            name: 'DEPLOY_TO_MAVEN',
            defaultValue: false,
            description: 'Deploy del package Maven su Maven Central / Sonatype OSS'
        )
        booleanParam(
            name: 'COPY_STABLE_NAME',
            defaultValue: true,
            description: 'Copia anche webrobot-cli-uber.jar in target/ (URL fisso per script di installazione)'
        )
        booleanParam(
            name: 'BUILD_DOCKER',
            defaultValue: true,
            description: 'Build e push immagine Docker su Docker Hub'
        )
        booleanParam(
            name: 'DEPLOY_TO_K8S',
            defaultValue: false,
            description: 'Deploy Job CLI su Kubernetes dopo il build'
        )
        string(
            name: 'MAVEN_REVISION',
            defaultValue: '',
            trim: true,
            description: 'Versione Maven CLI (es. 0.4.2). Vuoto = auto 0.3.<BUILD_NUMBER>.'
        )
        string(
            name: 'WEBROBOT_SDK_MAVEN_VERSION',
            defaultValue: '0.3.10',
            trim: true,
            description: 'Versione webrobot.eu:org.webrobot.sdk su Maven Central.'
        )
        string(
            name: 'SONATYPE_CREDENTIALS_ID',
            defaultValue: 'sonatype-ossrh',
            trim: true,
            description: 'Jenkins credential ID per Sonatype OSS (server id ossrh)'
        )
    }

    options {
        timeout(time: 90, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    def scmVars = checkout scm
                    env.GIT_COMMIT       = scmVars.GIT_COMMIT
                    env.GIT_COMMIT_SHORT = scmVars.GIT_COMMIT ? scmVars.GIT_COMMIT.take(8) : 'unknown'
                    def manualRev        = params.MAVEN_REVISION?.trim()
                    env.MAVEN_REVISION   = manualRev ? manualRev : "0.3.${env.BUILD_NUMBER}"
                    env.WEBROBOT_SDK_MAVEN_VERSION = params.WEBROBOT_SDK_MAVEN_VERSION?.trim() ?: '0.3.10'
                    echo "🔄 Checkout ${env.GITHUB_REPOSITORY} @ ${env.GIT_COMMIT_SHORT}"
                    echo "📦 Maven -Drevision=${env.MAVEN_REVISION} -Dwebrobot.sdk.depversion=${env.WEBROBOT_SDK_MAVEN_VERSION}"
                }
            }
        }

        stage('Setup Environment') {
            steps {
                container('maven') {
                    sh 'java -version && mvn -version && pwd && ls -la'
                }
            }
        }

        stage('Unit Tests') {
            when {
                expression { params.RUN_TESTS }
            }
            steps {
                container('maven') {
                    script {
                        withMaven(globalMavenSettingsConfig: env.MAVEN_SETTINGS_CONFIG) {
                            sh "mvn -B test -Drevision=${env.MAVEN_REVISION} -Dwebrobot.sdk.depversion=${env.WEBROBOT_SDK_MAVEN_VERSION}"
                        }
                    }
                }
            }
        }

        stage('Package uber-jar') {
            steps {
                container('maven') {
                    script {
                        echo "🔨 Build CLI uber-jar..."
                        withMaven(globalMavenSettingsConfig: env.MAVEN_SETTINGS_CONFIG) {
                            sh "mvn -U -B clean package -DskipTests -Drevision=${env.MAVEN_REVISION} -Dwebrobot.sdk.depversion=${env.WEBROBOT_SDK_MAVEN_VERSION}"
                        }
                        sh "ls -la target/*.jar || true"
                        if (params.COPY_STABLE_NAME) {
                            sh '''
                                set -e
                                UBER=$(ls target/webrobot-cli-*-uber.jar | head -1)
                                test -n "$UBER"
                                cp -f "$UBER" target/webrobot-cli-uber.jar
                                ls -la target/webrobot-cli-uber.jar
                            '''
                        }
                        echo "✅ uber-jar pronto"
                    }
                }
            }
        }

        stage('Deploy to Maven Central') {
            when {
                expression { return params.DEPLOY_TO_MAVEN }
            }
            steps {
                container('maven') {
                    script {
                        echo "🚀 Deploy Maven Central — credenziale ${params.SONATYPE_CREDENTIALS_ID}"
                        def esc = { String s ->
                            if (s == null) return ''
                            return s.replace('&', '&amp;').replace('<', '&lt;').replace('>', '&gt;')
                                    .replace('"', '&quot;').replace('\'', '&apos;')
                        }
                        withCredentials([usernamePassword(credentialsId: params.SONATYPE_CREDENTIALS_ID, usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')]) {
                            def u = (env.OSSRH_USER ?: '').trim()
                            def p = (env.OSSRH_PASS ?: '').trim()
                            if (!u || !p) {
                                error("Credenziale '${params.SONATYPE_CREDENTIALS_ID}': username o password vuoti. Tipo Jenkins: «Username with password».")
                            }
                            writeFile file: 'jenkins-ossrh-overlay-settings.xml', text: """<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>ossrh</id>
      <username>${esc(u)}</username>
      <password>${esc(p)}</password>
    </server>
  </servers>
</settings>
"""
                            withMaven(globalMavenSettingsConfig: env.MAVEN_SETTINGS_CONFIG, mavenSettingsFile: "${env.WORKSPACE}/jenkins-ossrh-overlay-settings.xml") {
                                sh "mvn -B deploy -DskipTests -Drevision=${env.MAVEN_REVISION} -Dwebrobot.sdk.depversion=${env.WEBROBOT_SDK_MAVEN_VERSION}"
                            }
                        }
                    }
                }
            }
        }

        stage('Archive artifacts') {
            steps {
                container('maven') {
                    script {
                        archiveArtifacts artifacts: 'target/webrobot-cli-*-uber.jar', fingerprint: true, allowEmptyArchive: false
                        if (params.COPY_STABLE_NAME) {
                            archiveArtifacts artifacts: 'target/webrobot-cli-uber.jar', fingerprint: true, allowEmptyArchive: false
                        }
                    }
                }
            }
        }

        stage('Build & Push Docker') {
            when {
                expression { params.BUILD_DOCKER }
            }
            steps {
                container('kaniko') {
                    script {
                        echo "🐳 Build immagine Docker con Kaniko e push su Docker Hub..."
                        echo "📦 Immagine: ${env.DOCKER_IMAGE}"
                        echo "🏷️  Tag: latest, ${env.BUILD_NUMBER}, ${env.MAVEN_REVISION}"
                        sh """
                            set -euo pipefail
                            JAR_FILE=\$(ls target/webrobot-cli-*-uber.jar | head -1)
                            if [ -z "\${JAR_FILE:-}" ]; then
                                echo "❌ uber-jar non trovato in target/" >&2
                                exit 1
                            fi
                            echo "✅ JAR: \$JAR_FILE"
                            /kaniko/executor \\
                                --context="\$WORKSPACE" \\
                                --dockerfile="\$WORKSPACE/Dockerfile" \\
                                --build-arg MAVEN_REVISION=${env.MAVEN_REVISION} \\
                                --build-arg WEBROBOT_SDK_MAVEN_VERSION=${env.WEBROBOT_SDK_MAVEN_VERSION} \\
                                --destination=${env.DOCKER_IMAGE}:latest \\
                                --destination=${env.DOCKER_IMAGE}:${env.BUILD_NUMBER} \\
                                --destination=${env.DOCKER_IMAGE}:${env.MAVEN_REVISION} \\
                                --cache=true \\
                                --cache-ttl=24h
                        """
                        echo "✅ Push completato su Docker Hub:"
                        echo "   ${env.DOCKER_IMAGE}:latest"
                        echo "   ${env.DOCKER_IMAGE}:${env.BUILD_NUMBER}"
                        echo "   ${env.DOCKER_IMAGE}:${env.MAVEN_REVISION}"
                    }
                }
            }
        }

        stage('Deploy to Kubernetes') {
            when {
                allOf {
                    expression { params.BUILD_DOCKER }
                    expression { params.DEPLOY_TO_K8S }
                }
            }
            agent {
                kubernetes {
                    label 'kubectl'
                    yaml """
apiVersion: v1
kind: Pod
spec:
  containers:
  - name: kubectl
    image: alpine/k8s:1.28.2
    command:
    - sleep
    args:
    - 99d
"""
                }
            }
            steps {
                container('kubectl') {
                    script {
                        echo "🚀 Deploy Job CLI su Kubernetes (namespace: ${env.K8S_NAMESPACE})..."
                        sh """
                            kubectl create job webrobot-cli-smoke-${env.BUILD_NUMBER} \\
                                --image=${env.DOCKER_IMAGE}:${env.BUILD_NUMBER} \\
                                -n ${env.K8S_NAMESPACE} \\
                                -- webrobot --help
                        """
                        sh """
                            kubectl wait job/webrobot-cli-smoke-${env.BUILD_NUMBER} \\
                                -n ${env.K8S_NAMESPACE} \\
                                --for=condition=complete \\
                                --timeout=120s
                        """
                        sh """
                            kubectl logs -n ${env.K8S_NAMESPACE} \\
                                job/webrobot-cli-smoke-${env.BUILD_NUMBER}
                        """
                        echo "✅ Smoke test CLI su K8s completato"
                    }
                }
            }
        }
    }

    post {
        success {
            script {
                echo "✅ Pipeline completata con successo!"
                echo "📦 CLI revision: ${env.MAVEN_REVISION}"
                echo "🐳 Docker: ${params.BUILD_DOCKER ? "${env.DOCKER_IMAGE}:${env.MAVEN_REVISION}" : 'saltato'}"
                echo "🚀 Deploy K8s: ${params.DEPLOY_TO_K8S ? 'eseguito' : 'saltato'}"
                echo "📎 Maven Central: ${params.DEPLOY_TO_MAVEN ? 'deployato' : 'saltato'}"
                echo "🔗 Docker Hub: https://hub.docker.com/r/webrobot2022/webrobot-cli"
            }
        }
        failure {
            echo "❌ Pipeline fallita — controlla i log per i dettagli"
        }
        cleanup {
            echo "🏁 Build ${env.BUILD_NUMBER} completata — durata: ${currentBuild.durationString}"
        }
    }
}
