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
      publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'target/scala-2.11/scoverage-report', reportFiles: 'index.html', reportName: 'Scoverage', reportTitles: ''])
      publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'target/scala-2.11/unidoc', reportFiles: 'index.html', reportName: 'ScalaDoc', reportTitles: ''])
      junit "**/test-reports/*.xml"
      step([$class: 'ScoveragePublisher', reportDir: 'target/scala-2.11/scoverage-report', reportFile: 'scoverage.xml'])
    }

  }
}
