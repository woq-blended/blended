pipeline {
  agent any
  stages {
    stage('Build externals') {
      steps {
        ansiColor('xterm') {
          sh 'sh ./blended.build/01_1_buildScalaJSReactComponents.sh ${WORKSPACE}'
        }
      }
    }
  }
}
