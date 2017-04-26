pipeline {
  agent any
  stages {
    stage('Prepare Build') {
      steps {
        docker.build 'blended-build' 'blended.docker/blended.docker.build'
      }
    }
  }
}
