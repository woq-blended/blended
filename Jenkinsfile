pipeline {
  agent any
  stages {
    stage('Build externals') {
      steps {
        ansiColor('xterm') {
          sh 'sh ./blended.build/01_1_buildScalaJSReactComponents.sh ${WORKSPACE}'
        }
        ansiColor('xterm') {
          sh 'mvn clean install -P build -Dmaven.repo.local=${WORKSPACE} '
        }
      }
    }
  }
}
