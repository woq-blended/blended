pipeline {
  agent any
  stages {
    stage('Prepare Build') {
      steps {
        ansiColor('xterm') {
          sh 'cd blended.docker/blended.docker.build; docker build -t atooni/blended-build .'
        }
      }
    }
    stage('Containerized Build') {
      agent {
        docker 'atooni/blended-build'
      }
      steps {
        ansiColor('xterm') {
          sh '/opt/zinc/bin/zinc -start -nailed -scala-home=$SCALA_HOME'
          sh 'source $HOME/.nvm/nvm.sh ; nvm use 4.2; mvn clean install'
        }
        junit allowEmptyResults: true, testResults: '**/surefire-reports/*.xml'
      }
    }
  }
}
