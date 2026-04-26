/**
 * WebRobot CLI (Picocli) — stesso modello Kubernetes/Maven di WebRobot.Sdk:
 * withMaven + managed settings (Sonatype: server id ossrh per deploy; SDK da Maven Central).
 * Artefatto: target/org.webrobot.eu.spark.job-*-uber.jar (shade).
 *
 * Versione CLI (${revision}): default CI 0.3.<BUILD_NUMBER> (nuova GAV ad ogni build).
 * Dipendenza SDK: param WEBROBOT_SDK_MAVEN_VERSION (versione su Maven Central).
 * Deploy Central: credential Jenkins «Username with password» (param SONATYPE_CREDENTIALS_ID, default sonatype-ossrh)
 * + overlay settings ossrh; Maven unisce con il managed file MAVEN_SETTINGS_CONFIG.
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
  volumes:
  - name: maven-repo
    persistentVolumeClaim:
      claimName: maven-repo-pvc
'''
            defaultContainer 'maven'
        }
    }

    environment {
        GITHUB_REPOSITORY = 'WebRobot-Ltd/WebRobot-CLI'
        // Managed Maven settings (mirror/proxy); segreti Sonatype via credential SONATYPE_CREDENTIALS_ID nello stage deploy.
        MAVEN_CREDENTIALS = 'github-token'
        MAVEN_SETTINGS_CONFIG = '603a9990-8a95-4328-84f2-693f1c72212f'
        UBER_JAR_GLOB = 'target/org.webrobot.eu.spark.job-*-uber.jar'
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
            description: 'Deploy del package Maven su Maven Central / Sonatype OSS (distributionManagement nel pom)'
        )
        booleanParam(
            name: 'COPY_STABLE_NAME',
            defaultValue: true,
            description: 'Copia anche webrobot-cli-uber.jar in target/ (URL fisso per script di installazione / mirror)'
        )
        string(
            name: 'MAVEN_REVISION',
            defaultValue: '',
            trim: true,
            description: 'Versione Maven del modulo CLI (es. 0.4.2). Vuoto = auto 0.3.<BUILD_NUMBER>.'
        )
        string(
            name: 'WEBROBOT_SDK_MAVEN_VERSION',
            defaultValue: '0.3.10',
            trim: true,
            description: 'Versione webrobot.eu:org.webrobot.sdk su Maven Central (allinea all’ultimo deploy SDK).'
        )
        string(
            name: 'SONATYPE_CREDENTIALS_ID',
            defaultValue: 'sonatype-ossrh',
            trim: true,
            description: 'Jenkins credential ID (Username with password) per Sonatype OSS — server Maven id ossrh'
        )
    }

    options {
        timeout(time: 45, unit: 'MINUTES')
        disableConcurrentBuilds()
        buildDiscarder(logRotator(numToKeepStr: '15'))
    }

    stages {
        stage('Checkout') {
            steps {
                script {
                    def scmVars = checkout scm
                    env.GIT_COMMIT = scmVars.GIT_COMMIT
                    env.GIT_COMMIT_SHORT = scmVars.GIT_COMMIT ? scmVars.GIT_COMMIT.take(8) : 'unknown'
                    def manualRev = params.MAVEN_REVISION?.trim()
                    env.MAVEN_REVISION = manualRev ? manualRev : "0.3.${env.BUILD_NUMBER}"
                    env.WEBROBOT_SDK_MAVEN_VERSION = params.WEBROBOT_SDK_MAVEN_VERSION?.trim() ?: '0.3.10'
                    echo "Checkout ${env.GITHUB_REPOSITORY} @ ${env.GIT_COMMIT_SHORT}"
                    echo "Maven -Drevision=${env.MAVEN_REVISION} -Dwebrobot.sdk.depversion=${env.WEBROBOT_SDK_MAVEN_VERSION}"
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
                        withMaven(globalMavenSettingsConfig: env.MAVEN_SETTINGS_CONFIG) {
                            sh "mvn -U -B clean package -DskipTests -Drevision=${env.MAVEN_REVISION} -Dwebrobot.sdk.depversion=${env.WEBROBOT_SDK_MAVEN_VERSION}"
                        }
                        sh "ls -la target/*.jar || true"
                        if (params.COPY_STABLE_NAME) {
                            sh '''
                                set -e
                                UBER=$(ls target/org.webrobot.eu.spark.job-*-uber.jar | head -1)
                                test -n "$UBER"
                                cp -f "$UBER" target/webrobot-cli-uber.jar
                                ls -la target/webrobot-cli-uber.jar
                            '''
                        }
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
                        echo "Deploy Sonatype OSS: overlay ossrh da credential ${params.SONATYPE_CREDENTIALS_ID} + managed global ${env.MAVEN_SETTINGS_CONFIG}"
                        def esc = { String s ->
                            if (s == null) {
                                return ''
                            }
                            return s.replace('&', '&amp;')
                                .replace('<', '&lt;')
                                .replace('>', '&gt;')
                                .replace('"', '&quot;')
                                .replace('\'', '&apos;')
                        }
                        withCredentials([usernamePassword(credentialsId: params.SONATYPE_CREDENTIALS_ID, usernameVariable: 'OSSRH_USER', passwordVariable: 'OSSRH_PASS')]) {
                            writeFile file: 'jenkins-ossrh-overlay-settings.xml', text: """<?xml version="1.0" encoding="UTF-8"?>
<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
    xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.2.0 https://maven.apache.org/xsd/settings-1.2.0.xsd">
  <servers>
    <server>
      <id>ossrh</id>
      <username>${esc(env.OSSRH_USER)}</username>
      <password>${esc(env.OSSRH_PASS)}</password>
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
                        archiveArtifacts artifacts: 'target/org.webrobot.eu.spark.job-*-uber.jar', fingerprint: true, allowEmptyArchive: false
                        if (params.COPY_STABLE_NAME) {
                            archiveArtifacts artifacts: 'target/webrobot-cli-uber.jar', fingerprint: true, allowEmptyArchive: false
                        }
                    }
                }
            }
        }
    }

    post {
        success {
            echo 'OK — CLI uber-jar in Jenkins Artifacts (Permalink: job → lastSuccessfulBuild → artifact).'
            echo "CLI revision ${env.MAVEN_REVISION}; SDK dep ${env.WEBROBOT_SDK_MAVEN_VERSION}. Deploy Maven: ${params.DEPLOY_TO_MAVEN ? 'sì' : 'no'}."
            echo 'Installazione: scripts/install-webrobot-cli.sh con WEBROBOT_CLI_JAR_URL=<URL pubblico del jar>.'
        }
        failure {
            echo 'Build o deploy fallito: log Maven, managed settings (server ossrh / Sonatype), firma GPG e requisiti Central.'
        }
    }
}
