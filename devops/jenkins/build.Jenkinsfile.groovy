#!groovy


def notifySlack(String msg, org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper build) {
    buildStatus = build.result ?: 'SUCCESS'
    def color
    if (buildStatus == 'STARTED') {
        color = '#D4DADF'
    } else if (buildStatus == 'SUCCESS') {
        color = 'good'
    } else if (buildStatus == 'UNSTABLE') {
        color = '#FFFE89'
    } else {
        color = 'danger'
    }
    slackSend teamDomain: 'suezsmile', color: color, channel: '#monitoring', message: msg,  tokenCredentialId: 'slack-token'
}

node('tsme-docker') {

    properties(
        [
            parameters(
                [
                    gitParameter(branch: '', branchFilter: '.*', defaultValue: 'origin/develop', description: 'git branch or tag', name: 'GIT_REF', quickFilterEnabled: false, selectedValue: 'NONE', sortMode: 'NONE', tagFilter: '*', type: 'PT_BRANCH_TAG'),
                    string(defaultValue: 'X.X', description: 'semver major and minor', name: 'VERSION', trim: true),
                    choice(choices: ['--dev', '--no-dev'], description: '--dev only for inte', name: 'DEV_PACKAGES'),
                    choice(choices: ['inte', 'preprod', 'prod'], description: 'Target env for package deploy', name: 'TARGET_ENV')
                ]
            ),
            disableConcurrentBuilds(),
            buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '4', numToKeepStr: '4')),
            [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/Suezenv/tsme-part.git'],
            gitLabConnection(''),
            copyArtifactPermission('TSME-PART-DEPLOY,TSME-LBN-DEPLOY'),
            [$class: 'JiraProjectProperty'],
            [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
            [$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project']
        ]
    )

    def now = new Date()
    env.TIME_SUFFIX = now.format("yyyy-MM-dd-HH-mm-ss")
    env.PACKAGE_NAME = "TSME-PART-${TARGET_ENV}-v${VERSION}.${BUILD_NUMBER}"
    def MAKE_GIT_REF = GIT_REF.replaceAll("^origin/", "");

    env.APP_ENV = 'prod'
    if (DEV_PACKAGES == '--dev') {
        env.APP_ENV = 'dev'
    }

    stage('Preparation') {
        checkout poll: false, scm: [$class: 'GitSCM', branches: [[name: '${GIT_REF}']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4216593b-d5e9-4c4d-9cf9-0c1d8a5ada74', url: 'https://github.com/Suezenv/tsme-part.git']]]
        echo 'preparation done'
    }

    ansiColor('xterm') {
        stage('build') {
            try {
                withCredentials([string(credentialsId: 'PROJETSUEZ_TOKEN', variable: 'githubToken')]) {
                    sh 'echo COMPOSER_AUTH={\\"github-oauth\\":{\\"github.com\\":\\"${githubToken}\\"}} > .env.local'
                }
                sh "echo COMPOSER_HOME=${COMPOSER_HOME} >> .env.local"
                //Avoid collision with other builds
                sh "echo COMPOSE_PROJECT_NAME=tsme_build >> .env.local"
                sh "make build-package RELEASE_TAG=${VERSION} GIT_REF=${MAKE_GIT_REF} PACKAGE_NAME=${PACKAGE_NAME} NO_DEV=${DEV_PACKAGES} APP_ENV=${APP_ENV} INFRAPARAMS=${TARGET_ENV}"
                sh "ls -alh build/dist"
            }
            catch (exc) {
                currentBuild.result = 'FAILURE'
                throw exc
            } finally {
            }
        }

        stage('Archive') {
            def pattern = "build/dist/*.${env.BUILD_NUMBER}.tar.gz"
            archiveArtifacts pattern
        }

        stage('clean') {
            cleanWs()
        }
    }
}
