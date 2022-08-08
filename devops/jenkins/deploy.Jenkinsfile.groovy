#!groovy
/* devops/jenkins/deploy.Jenkinsfile.groovy */

node('suez-azu-dev-3d') {
    properties(
        [
            parameters(
                [
                    string(defaultValue: '', description: 'Récupérer la construction existante # du job TSME-PART-BUILD', name: 'TSME_BUILD_NUMBER', trim: true),
                    gitParameter(branch: '', branchFilter: '.*', defaultValue: 'origin/develop', description: 'git branch or tag', name: 'GIT_REF', quickFilterEnabled: false, selectedValue: 'NONE', sortMode: 'NONE', tagFilter: '*', type: 'PT_BRANCH_TAG'),
                    string(defaultValue: 'X.X', description: 'semver major and minor', name: 'VERSION', trim: true),
                    choice(choices: ['--dev', '--no-dev'], description: '--dev only for inte', name: 'DEV_PACKAGES'),
                    choice(choices: ['inte', 'preprod', 'prod', 'inte_chpyr', 'inte_olbov', 'inte_pidev', 'inte_winso', 'inte_chath'], description: 'Target env for package deploy', name: 'TARGET_ENV'),
                    choice(choices: ['upgrade_tsme', 'upgrade_tsme_preview'], description: 'Désigne l\'emplacement sur le serveur de destination. Ce paramètres n\'est exploitable que pour les environnements d\'inté / preprod / prod.', name: 'PROJECT_NAME')

                ]
            ),
            disableConcurrentBuilds(),
            buildDiscarder(logRotator(artifactDaysToKeepStr: '', artifactNumToKeepStr: '', daysToKeepStr: '4', numToKeepStr: '4')),
            [$class: 'GithubProjectProperty', displayName: '', projectUrlStr: 'https://github.com/Suezenv/tsme-part.git'],
            gitLabConnection(''),
            [$class: 'JiraProjectProperty'],
	        copyArtifactPermission('TSME-PART-DEPLOY,TSME-LBN-DEPLOY, TSME-PART-ANSIBLE-DEPLOY'),
            [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
            [$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project']
        ]
    )

    def envToLbnCatalog = [
      "inte": "int_centos7_ansible",
      "preprod": "preprod_centos7_ansible",
      "prod": "prod_centos7_ansible",
    ]

  	slack_deploy_details(env.TARGET_ENV, env.GIT_REF, env.VERSION)

    stage('Preparation') {
      	slack_thread("Prepare", jte.keywords.globals['slack_deploy_details_threadId'])
        checkout poll: false, scm: [$class: 'GitSCM', branches: [[name: '${GIT_REF}']], doGenerateSubmoduleConfigurations: false, extensions: [], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '4216593b-d5e9-4c4d-9cf9-0c1d8a5ada74', url: 'https://github.com/Suezenv/tsme-part.git']]]
    }

    stage('Build package') {
      	slack_thread("Package build", jte.keywords.globals['slack_deploy_details_threadId'])
        if (env.TSME_BUILD_NUMBER.length() > 0)
            env.PACKAGE_BUILD_NUM = env.TSME_BUILD_NUMBER
        else {
          	def CLEAN_TARGET_ENV = env.TARGET_ENV
            if (env.TARGET_ENV.contains('inte_')) {
              FOR_USER = env.TARGET_ENV.replace('inte_', '')
              CLEAN_TARGET_ENV = 'inte'
            }
            def built = build job: 'TSME-PART-BUILD', parameters: [gitParameter(name: 'GIT_REF', value: env.GIT_REF), string(name: 'VERSION', value: env.VERSION), string(name: 'DEV_PACKAGES', value: env.DEV_PACKAGES), string(name: 'TARGET_ENV', value: CLEAN_TARGET_ENV)]
            env.PACKAGE_BUILD_NUM = String.valueOf(built.number)
        }
        copyArtifacts fingerprintArtifacts: true, projectName: 'TSME-PART-BUILD', selector: specific(env.PACKAGE_BUILD_NUM), target: './'
        def pattern = "build/dist/*.${env.PACKAGE_BUILD_NUM}.tar.gz"
        archiveArtifacts pattern        
      	sh "ls -al ./build/dist"
    }


    def pattern = "build/dist/*.${env.PACKAGE_BUILD_NUM}.tar.gz"
    def files = findFiles(glob: pattern)
    if(!files.length.equals(1)) {
        error 'To many artifact files, possible error on precise file to deploy'
        cleanWs()
    }


    stage('Validation') {
        if(env.TARGET_ENV.equals('prod')) {
            slack_thread("Pending validation...", jte.keywords.globals['slack_deploy_details_threadId'])
            wait_validation()
        } else {
                echo """Pas de validation nécessaire pour ${env.TARGET_ENV}"""
        }
    }

    stage('Deploy') {
      	slack_thread("Deploy START", jte.keywords.globals['slack_deploy_details_threadId'])
        try {
            if (env.TARGET_ENV.contains('inte_')) {
              //Deploiement en inté pour les dev
              FOR_USER = env.TARGET_ENV.replace('inte_', '')
                withPythonEnv('/usr/bin/python3') {
                    //Ensure ansible are present
                    sh """
                        pip -V
                        pip install --upgrade pip
                        pip install ansible
                        echo $PWD
                        ansible --version
                        export
                    """

                    def ansible_file = files[0].path.replace('.tar.gz', '')
                    sh """
                        ansible-playbook -i devops/ansible/inventory/inte \
                         devops/ansible/users-workspace-deploy.yml \
                         -e deploy_version='${ansible_file}' -e target_env=inte -e for_user='${FOR_USER}'  -v
                    """
                }
            } else {
              	echo """Deploiement  ${env.PROJECT_NAME}"""
				def built = build job: 'TSME-PART-ANSIBLE-DEPLOY', parameters: [string(name: 'TARGET_ENV', value: env.TARGET_ENV), string(name: 'PROJECT_NAME', value: env.PROJECT_NAME), string(name: 'DEPLOY_FILE', value: files[0].name.replace('.tar.gz', ''))]              
            }
        	slack_deploy_details(env.TARGET_ENV, env.GIT_REF, env.VERSION, 'OK', jte.keywords.globals['slack_deploy_details_threadId'], jte.keywords.globals['slack_deploy_details_ts'])
          	slack_thread("Deploy END", jte.keywords.globals['slack_deploy_details_threadId'])

        } catch (exc) {
             sh """rm -f ${files[0].path}"""
	         slack_deploy_details(env.TARGET_ENV, env.GIT_REF, env.VERSION, 'KO', jte.keywords.globals['slack_deploy_details_threadId'], jte.keywords.globals['slack_deploy_details_ts'])
             throw exc
        }
    }

    stage('clean') {
      slack_thread("Clean workspace", jte.keywords.globals['slack_deploy_details_threadId'])
      cleanWs()
    }
}
