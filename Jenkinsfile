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
      node('blended-build:latest') {
        sh 'mvn -version'
      }
    }
  }
}
