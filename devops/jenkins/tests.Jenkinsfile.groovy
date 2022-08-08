node('tsme-docker') {
    properties(
        [
            parameters(
                [
                    string(defaultValue: 'develop', description: 'git commit, branch or tag', name: 'TSME_GIT_REF', trim: true)
                ]
            ),
            [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/Suezenv/tsme-part'],
            [$class: 'JiraProjectProperty'],
            [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
        ]
    )

    def checkoutRef = env.ghprbActualCommit ?: TSME_GIT_REF

    ansiColor('xterm') {
        stage('Checkout') {
            checkout poll: false, scm: [$class: 'GitSCM', branches: [[name: checkoutRef]], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4216593b-d5e9-4c4d-9cf9-0c1d8a5ada74', url: 'https://github.com/Suezenv/tsme-part.git']]]
            echo 'Checkout done'
        }

        stage('MountEnv') {
            try {
                sh "echo COMPOSE_FILE=./docker-compose.yml:./docker-compose-jenkins.yml:./docker-compose-apisimulator.yml > .env.local"
                sh "echo COMPOSER_HOME=${COMPOSER_HOME} >> .env.local"
                sh "echo COMPOSE_PROJECT_NAME=tsme_test_${BUILD_NUMBER} >> .env.local"
                sh "echo NETWORK_NUMBER=${BUILD_NUMBER} >> .env.local"
                sh "echo YARN_CACHE_FOLDER=${YARN_CACHE_FOLDER} >> .env.local"
                sh "make down-ci  1> /dev/null"
                sh "make up-ci"
                sh 'echo APP_ENV=test >> .env.local'
                sh "make fix-perms 1> /dev/null"
                sh """
                    cp app/config/parameters.yml.dist app/config/parameters.yml
                """
//                     make clear-cache
            }
            catch (exc) {
                sh "make down-ci"
                sh "make remove-shared-network"
                throw exc
            }
        }

        stage('Install vendors') {
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

        stage('Launch Grumphp') {
            try {
//                sh """
//                    make ci-syntax
//                """
            }
            catch (exc) {
                sh "make down-ci"
                cleanWs()
                throw exc
            }
        }

        stage('Tests') {
                try {
                    sh """
                        make test-backup
                        make rm-bigger-log
                    """
                }
                catch (exc) {
                    sh "make down-ci"
                    sh "make remove-shared-network"
                    cleanWs()
                    throw exc
                }
                echo 'tests end'
        }

        stage('UnMountEnv') {
            sh "make down-ci"
            sh "make remove-shared-network"
            cleanWs()
        }
    }
}
