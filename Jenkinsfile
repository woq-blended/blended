pipeline {
  agent any
  stages {

    stage('Preliminary SBT Build') {
      steps {
        ansiColor('xterm') {
          sh 'sh ./blended.build/01_1_buildScalaJSReactComponents.sh $WORKSPACE'
          sh 'sbt clean coverage test coverageReport coverageAggregate coverageOff publishLocal osgiBundle unidoc'
        }
      }
    }
  }

  post {

    always {
      publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'target/scala-2.11/unidoc', reportFiles: 'index.html', reportName: 'ScalaDoc', reportTitles: ''])
      junit "**/test-reports/*.xml"
      step([$class: 'ScoveragePublisher', reportDir: 'target/scala-2.11/scoverage-report', reportFile: 'scoverage.xml'])
    }

  }
}
