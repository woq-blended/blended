pipeline {
  agent any
  stages {

    stage('Build externals') {
      steps {
        ansiColor('xterm') {
          sh 'bash ./blended.build/01_1_buildScalaJSReactComponents.sh ${WORKSPACE}'
        }
      }
    }

    stage('Blended build') {
      steps {
        ansiColor('xterm') {
          sh 'bash ./blended.build/01_1_buildScalaJSReactComponents.sh ${WORKSPACE}'
        }
      }
    }
  }
}
