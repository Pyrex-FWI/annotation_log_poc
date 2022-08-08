node('suez-azu-tsme-web-1d') {
    properties(
        [
            parameters(
                [
                    string(defaultValue: 'develop', description: 'git commit, branch or tag', name: 'TSME_GIT_REF', trim: true),
                    choice(choices: ['dev', 'inte', 'preprod', 'prod'], description: 'Target env for E2E test', name: 'TARGET_ENV')
                ]
            ),
            buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '', numToKeepStr: '10')),
            [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/Suezenv/tsme-part'],
            [$class: 'JiraProjectProperty'],
            [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
            throttleJobProperty(categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 1, maxConcurrentTotal: 2, paramsToUseForLimit: '', throttleEnabled: true, throttleOption: 'project')
        ]
    )

    def checkoutRef = env.ghprbActualCommit ?: TSME_GIT_REF

    def target = "-"+env.TARGET_ENV
    def need_dev_env = false
    if (!env.TARGET_ENV || env.TARGET_ENV.equals('dev')) {
        target = ""
        need_dev_env = true
    }

    ansiColor('xterm') {
        stage('Checkout') {
            checkout poll: false, scm: [$class: 'GitSCM', branches: [[name: checkoutRef]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4216593b-d5e9-4c4d-9cf9-0c1d8a5ada74', url: 'https://github.com/Suezenv/tsme-part.git']]]
            echo 'Checkout done'
        }

        stage('MountEnv') {
            if (need_dev_env){
                try {
                    sh "echo COMPOSE_FILE=./docker-compose.yml:./docker-compose-jenkins.yml:./docker-compose-apisimulator.yml > .env.local"
                    sh "echo COMPOSER_HOME=${COMPOSER_HOME} >> .env.local"
                    sh "echo YARN_CACHE_FOLDER=${YARN_CACHE_FOLDER} >> .env.local"
                    sh "echo COMPOSE_PROJECT_NAME=tsme_${BUILD_NUMBER} >> .env.local"
                    sh "echo NETWORK_NUMBER=${BUILD_NUMBER} >> .env.local"
                    sh "echo CYPRESS_CACHE_FOLDER=${CYPRESS_CACHE_FOLDER} >> .env.local"
                    sh "echo NODE_ENV=test >> .env.local"
                    sh "make down-ci  1> /dev/null"
                    sh "make up-ci"
                    sh "make fix-perms 1> /dev/null"
                    sh 'echo APP_ENV=test >> .env.local'

                    withCredentials([string(credentialsId: 'PROJETSUEZ_TOKEN', variable: 'githubToken')]) {
                        sh 'echo COMPOSER_AUTH={\\"github-oauth\\":{\\"github.com\\":\\"${githubToken}\\"}} >> .env.local'
                    }
                    sh """
                        cp app/config/parameters.yml.dist app/config/parameters.yml
                    """
                }
                catch (exc) {
                    sh "make down-ci"
                    sh "make remove-shared-network"
                    cleanWs()
                    throw exc
                }
            }

        }

        stage('Install vendors') {
            if (need_dev_env){
                try {

                    sh """
                        make install-ci
                    """
                }
                catch (exc) {
                    sh "make down-ci"
                    sh "make remove-shared-network"
                    cleanWs()
                    throw exc
                }
            }
        }

        stage('Tests') {
            try {
                sh """
                    make test-e2e${target}
                """
            }
            catch (exc) {
                sh "make down-ci"
                sh "make remove-shared-network"
                //archiveArtifacts artifacts: 'cypress/videos/**/*.mp4, cypress/screenshots/**/*', onlyIfSuccessful: false
                cleanWs()
                throw exc
            }
            echo 'tests end'
        }

//         parallel "mysql-import-ez-data": {
//                 stage('Load Database and fixtures') {
//                     try {
//                         sh """
//                             make mysql-import-ez-data
//                         """
//                     }
//                     catch (exc) {
//                         sh "make down-ci"
//                         throw exc
//                     }
//                 }
//             },
//             "tsme_front_reload_database": {
//                 stage('Load Database and fixtures') {
//                     try {
//                         sh """
//                             make reload-database
//                         """
//                     }
//                     catch (exc) {
//                         sh "make down-ci"
//                         throw exc
//                     }
//                 }
//         }

        stage('UnMountEnv') {
//             archiveArtifacts artifacts: 'cypress/videos/**/*.mp4, cypress/screenshots/**/*', onlyIfSuccessful: false
            sh "make down-ci"
            sh "make remove-shared-network"
            cleanWs()
        }
    }
}
