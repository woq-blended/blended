pipeline {
  agent any
  stages {

    stage('Preliminary SBT Build') {
      steps {
        ansiColor('xterm') {
          sh 'sbt clean coverage test publishLocal osgiBundle unidoc'
          sh 'sbt coverageReport'
          sh 'sbt coverageAggregate'
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
