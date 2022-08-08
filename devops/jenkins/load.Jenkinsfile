pipeline {
  agent any

  environment {
      PATH = '/home/suezadm/.linuxbrew/bin:/usr/local/rvm/gems/ruby-2.1.3/bin:/usr/local/rvm/gems/ruby-2.1.3@global/bin:/usr/local/rvm/rubies/ruby-2.1.3/bin:/opt/rh/python27/root/usr/bin:/usr/local/maven/bin:/usr/local/linkbynet/script-client/bin:/usr/local/linkbynet/script/bin:/usr/local/linkbynet/php-5.5/bin:/usr/local/linkbynet/jdk8/bin:/usr/local/linkbynet/jdk7/bin:/usr/local/sbin:/usr/local/bin:/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/sbin:/usr/local/rvm/bin:/home/suezadm/bin'
  }

  stages {
    stage('Run Perf test') {
      steps {
        script {
          currentBuild.displayName = "#${BuiLD_NUMBER} ${params.JSCENARIO} ${params.CEL_CONCURENCY} VU"
          //currentBuild.description = "The best description."
        }
        //-o execution.0.hold-for=1
        bzt "devops/load_plan/taurus_jmeter_config.yml -o scenarios.simple.script=devops/load_plan/tsme_jmeter_${params.JSCENARIO}.jmx -o scenarios.simple.variables.nbVu=${params.CEL_CONCURENCY} -o scenarios.simple.variables.rampUp=${params.CEL_RAMPUP} -o scenarios.simple.variables.usersProfilPath=${params.USERSPROFILPATH} -o scenarios.simple.variables.ezpublish=${params.EZPUBLISH} -report  "
      }
    }
    stage('Save artifacts') {
      steps {
        archiveArtifacts artifacts: 'devops/load_plan/results_*', onlyIfSuccessful: true
      }
    }
    stage('Remove temporary files') {
      steps {
        sh "rm -rf devops/load_plan/"
      }
    }
  }
}