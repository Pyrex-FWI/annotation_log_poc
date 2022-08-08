node('tsme-dev') {

//TODO --> DELETE with build.xml and https://ci.toutsurmesservices.fr/view/TSME/job/TSME-ezplatform-build
//    currentBuild.displayName = "${TSME_MAJOR_VERSION}.-${RELEASE_TAG}-${BUILD_NUMBER}"

    env.ARCHIVE_FILE = "${JOB_NAME}-${BUILD_NUMBER}".replaceAll("[\\W]", "_")
    env.ARCHIVE_FILE = "${ARCHIVE_FILE}".replaceAll("[\\W]", "_")
    env.ARCHIVE_FILE_EXT = "${env.ARCHIVE_FILE}.tar.gz"

    currentBuild.description = "${ARCHIVE_FILE}"

    if ("--no-dev" == "${params.DEV_PACKAGES}") {
       env.APP_ENV = 'prod'
    }

    stage('Preparation') {
      // Get source code from a GitHub repository
      checkout([$class: 'GitSCM', branches: [[name: '${RELEASE_TAG}']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CleanBeforeCheckout']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: 'GITHUB_SUEZENV_PC', url: 'https://github.com/Suezenv/tsme-part']]])
    }
    stage('Assemble package to deploy') {
        withAnt(installation: 'ANT 1.9') {
            // Build a package to deploy
            sh 'ant full-build -v -DdevPackages=${DEV_PACKAGES} -Dtag=${RELEASE_TAG} -DpackageVersion=${ARCHIVE_FILE} -DbuildVersion=${ARCHIVE_FILE}'
        }
    }
    stage('Archive the build package') {
        archiveArtifacts 'build/dist/**/*.tar.gz'
    }
    stage('Add a git tag') {
        // For SSH private key authentication, try the sshagent step from the SSH Agent plugin.
        sshagent (credentials: ['GITHUB_SUEZENV_PC']) {
            sh("git tag -a v${ARCHIVE_FILE} -m 'release version ${ARCHIVE_FILE}'")
            sh('git push git@github.com:Suezenv/tsme-part.git v${ARCHIVE_FILE}')
        }
    }
    stage('Deploy to rebon') {
        sh 'ls -al build/dist/${ARCHIVE_FILE}.tar.gz'
        sh 'scp build/dist/${ARCHIVE_FILE}.tar.gz suezlivr@10.15.18.6:/home/livraisons/TSME/${ARCHIVE_FILE}.tar.gz'
    }
    stage('Clean Workspace') {
        cleanWs()
    }
//    stage('Send Slack notification') {
//
//        if("${params.NOTIFY_BY_SLACK}" == true) {
//            slackSend baseUrl: 'https://suezsmile.slack.com/services/hooks/jenkins-ci/', color: 'good', tokenCredentialId: 'slack-token', channel: "#TSME", message: "Le build TSME ${ARCHIVE_FILE} est prêt pour la livraison. \n Merci de créer un ticket Linkbynet en remplissant les champs du formulaire : \n\nProjet : SUEZ - TSME \n\nDemande spécifique : [SUEZ][ANS][TSME][BACK][WEB] Deploiement d'un package \n\nNiveau de priorité : 2- Sous 8h ouvrées \n\nEt avec le contenu suivant : \n\nBonjour,\n\nMerci de bien vouloir deployer ce package: \n\nINVENTORY: XX (inte, preprod ou prod) \n\nVERSION: ${ARCHIVE_FILE}  \n\n Cordialement,"
//        }
//    }
}