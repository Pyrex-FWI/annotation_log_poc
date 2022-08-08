#!groovy

//TOTO Delete and https://ci.toutsurmesservices.fr/view/TSME/job/TSME-PART-DEV-WORKSPACE-DEPLOY/

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

node('suez-azu-dev-3d') {

    properties(
        [
            parameters(
                [
                    gitParameter(branch: '', branchFilter: '.*', defaultValue: 'origin/develop', description: 'git branch or tag', name: 'GIT_REF', quickFilterEnabled: false, selectedValue: 'NONE', sortMode: 'NONE', tagFilter: '*', type: 'PT_BRANCH_TAG'),
                    string(defaultValue: 'X.X', description: 'semver major and minor', name: 'VERSION', trim: true),
                    choice(choices: ['--dev', '--no-dev'], description: '', name: 'DEV_PACKAGES'),
                    choice(choices: ['inte'], description: 'Target env for package deploy', name: 'TARGET_ENV'),
                    choice(choices: ['chpyr', 'likou', 'olbov', 'pidev', 'winso', 'chath'], description: 'Target user workspace', name: 'FOR_USER')
                ]
            ),
            buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '4', numToKeepStr: '4')),
            [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/Suezenv/tsme-part.git'],
            [$class: 'JiraProjectProperty'],
            [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
            [$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 2, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project']
        ]
    )

    def checkoutRef = env.ghprbActualCommit ?: GIT_REF
    env.DOCKER_IMAGE = '3snetregistry.azurecr.io/tsme/php7.3:base-v4'
    env.ARCHIVE_FILE = "${JOB_NAME}-${BUILD_NUMBER}".replaceAll("[\\W]", "_")
    env.ARCHIVE_FILE_EXT = "${env.ARCHIVE_FILE}.tar.gz"

    env.CLEAN_GIT_REF = GIT_REF.replaceAll('origin/', '').replaceAll("[\\W]", "_")

    currentBuild.description = "${FOR_USER} - ${GIT_REF}"

    env.APP_ENV = 'prod'
    if (DEV_PACKAGES == '--dev') {
        env.APP_ENV = 'dev'
    }

    withPythonEnv('/usr/bin/python3') {

        stage('Checkout') {
            checkout([$class: 'GitSCM', branches: [[name: checkoutRef]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: false, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'GITHUB_SUEZENV_PC', url: 'https://github.com/Suezenv/tsme-part.git']]])
            echo 'Checkout done'
        }
        ansiColor('xterm') {
            dir('build/dist') {

                stage('Checkout') {
                    checkout([$class: 'GitSCM', branches: [[name: checkoutRef]], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: false, reference: '', shallow: true]], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'GITHUB_SUEZENV_PC', url: 'https://github.com/Suezenv/tsme-part.git']]])
                    echo 'Checkout done'
                }

                stage('build') {
                    try {
                        sh """
                            pip -V
                            pip install --upgrade pip
                            pip install ansible
                            echo $PWD
                            ansible --version
                            export
                        """

                        withCredentials([string(credentialsId: 'COMPOSER_GIT_AUTH_TOKEN', variable: 'githubToken')]) {

                            sh '''
                                docker run \
                                --rm \
                                -v $PWD:/usr/var/www \
                                -e APP_ENV=${APP_ENV} -e COMPOSER_AUTH=${githubToken} -e NPM_TOKEN=${NPM_TOKEN} -e COMPOSER_ALLOW_SUPERUSER=1 -e COMPOSER_PROCESS_TIMEOUT=3600 \
                                -e SESSION_HANDLER_ID=session.handler.native_file  \
                                -e YARN_CACHE_FOLDER=/usr/var/www/node_modules/.cache/yarn \
                                -e NO_UPDATE_NOTIFIER=false \
                                ${DOCKER_IMAGE} \
                                bash -c "php /usr/bin/composer install -n --optimize-autoloader --no-suggest --no-progress --no-scripts --prefer-dist && php /usr/bin/composer run-script post-install-cmd "
                            '''
                        }

                        sh '''
                                docker run \
                                --rm \
                                -v $PWD:/usr/var/www \
                                ${DOCKER_IMAGE} \
                                bash -c "sed -i 's/##version##/${CLEAN_GIT_REF}/ig' app/config/default_parameters.yml && grep 'tsme_version' app/config/default_parameters.yml"
                        '''
                        sh '''
                                docker run \
                                --rm \
                                -v $PWD:/usr/var/www \
                                ${DOCKER_IMAGE} \
                                bash -c "sed -i \"s/##date##/$(date '+%Y%m%d')/ig\" app/config/default_parameters.yml && grep 'tsme_build_date' app/config/default_parameters.yml"
                        '''
                    }
                    catch (exc) {
                        currentBuild.result = 'FAILURE'
                        throw exc
                    } finally {
                    }
                }
            }

            stage('archiveArtifacts') {
                sh '''
                    tar -czf ${ARCHIVE_FILE_EXT} -C build/dist . \
                    --exclude "node_modules" \
                    --exclude "api-simuator" \
                    --exclude "var/logs/*" \
                    --exclude "var/cache/*" \
                    --exclude "var/encore/*" \
                    --exclude "tests" \
                    --exclude-vcs
                '''

                archiveArtifacts artifacts: env.ARCHIVE_FILE_EXT
            }

            stage('Delivery') {
                try {
                    sh '''
                        ansible-playbook -i devops/ansible/inventory/inte \
                         devops/ansible/users-workspace-deploy.yml \
                         -e deploy_version="${ARCHIVE_FILE}" -e target_env=${TARGET_ENV} -e for_user=${FOR_USER}  -v
                    '''
                } catch (exc) {
                         sh 'rm -f ${ARCHIVE_FILE_EXT}'
                         throw exc
                }
            }

            stage('clean') {
                sh 'rm -f ${ARCHIVE_FILE_EXT}'
//                 cleanWs()
            }
        }
    }
}
