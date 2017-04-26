pipeline {
  agent any
  ansiColor('xterm') {
    stages {
      stage('Prepare Build') {
        steps {
          sh 'cd blended.docker/blended.docker.build; docker build -t blended-build .'
        }
      }
    }
  }
}
