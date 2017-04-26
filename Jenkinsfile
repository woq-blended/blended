pipeline {
  agent { docker 'maven:3.5.0' }
  stages {
    stage('Containerized Build') {
      steps {
        ansiColor('xterm') {
          sh 'mvn -version'
        }
      }
    }
  }
}
