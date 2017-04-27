pipeline {
  agent { 
    dockerfile {
      dir 'blended.docker/blended.docker.build'
    }
  }
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
