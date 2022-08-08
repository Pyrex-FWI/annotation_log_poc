#!groovy

def notifySlack(String msg) {
    def color = 'good'
    slackSend teamDomain: 'suezsmartsolution', color: color, channel: '#monitoring-tsme', message: msg,  tokenCredentialId: 'slack-3s-token'
}

node('suez-azu-dev-3d') {

    properties(
        [
            parameters(
                [
                    gitParameter(branch: '', branchFilter: '.*', defaultValue: 'origin/master', description: 'git branch or tag', name: 'GIT_REF', quickFilterEnabled: false, selectedValue: 'NONE', sortMode: 'NONE', tagFilter: '*', type: 'PT_BRANCH_TAG'),
                    gitParameter(branch: '', branchFilter: '.*', defaultValue: 'origin/develop', description: 'git branch or tag', name: 'GIT_BRANCH', quickFilterEnabled: false, selectedValue: 'NONE', sortMode: 'NONE', tagFilter: '*', type: 'PT_BRANCH_TAG'),
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

    def GIT_LIST = "${GIT_REF} <=== ${GIT_BRANCH} : \n\n";

    stage('Preparation') {
        checkout poll: false, scm: [$class: 'GitSCM', branches: [[name: 'origin/develop']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4216593b-d5e9-4c4d-9cf9-0c1d8a5ada74', url: 'https://github.com/Suezenv/tsme-part.git']]]
        echo 'preparation done'
    }

    ansiColor('xterm') {
        stage('build list') {
            try {
                withCredentials([string(credentialsId: 'PROJETSUEZ_TOKEN', variable: 'githubToken')]) {
                    sh 'echo COMPOSER_AUTH={\\"github-oauth\\":{\\"github.com\\":\\"${githubToken}\\"}} > .env.local'
                }
                sh "echo COMPOSER_HOME=${COMPOSER_HOME} >> .env.local"
                //Avoid collision with other builds
                sh "echo COMPOSE_PROJECT_NAME=tsme_build >> .env.local"
                GIT_LIST += sh (
                script: "git log --format='%B' --no-merges  ${GIT_REF}...${GIT_BRANCH} | sed -r '/./!d' | grep -iP '(\\[AB#\\d+\\])|(ab#\\d+)|(\\[NT\\])'",
                returnStdout: true
                ).trim()
                echo "List of commit messages: ${GIT_LIST}"

            }
            catch (exc) {
                throw exc
            } finally {
            }
        }
    }

   stage('Send Slack notification') {
        notifySlack(GIT_LIST)
   }
}
