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
          sh 'bash ./blended.build/02_buildBlended.sh ${WORKSPACE}'
        }
      }
    }

    stage('Docker images') {
      steps {
        ansiColor('xterm') {
          sh 'bash ./blended.build/03_createDockerImages.sh ${WORKSPACE}'
        }
      }
    }

    stage('Integration Tests') {
      steps {
        ansiColor('xterm') {
          sh 'bash ./blended.build/04_integrationTest.sh ${WORKSPACE}'
        }
      }
    }

  }

  post {

    always {
      junit "**/surefire-reports/*.xml"
    }
  }
}
