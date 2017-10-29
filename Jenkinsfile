pipeline {
  agent any
  stages {

    stage('Preliminary SBT Build') {
      steps {
        ansiColor('xterm') {
          sh 'sbt clean test publishLocal osgiBundle unidoc'
        }
      }
    }

  }

  post {

    always {
      junit "**/test-reports/*.xml"
    }

  }
}
