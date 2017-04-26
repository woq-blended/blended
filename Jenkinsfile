pipeline {
  agent any
  stages {
    stage('Prepare Build') {
      steps {
        ansiColor('xterm') {
          sh 'cd blended.docker/blended.docker.build; docker build -t blended-build .'
        }
      }
    }
    stage('Build and Test') {
      steps {
        node('blended-build:latest') 
        ansiColor('xterm') {
          sh 'mvn -version'
        }
      }
    }
  }
}
