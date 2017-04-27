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
          sh 'source $HOME/.nvm/nvm.sh ; nvm use 4.2'
          sh '/opt/zinc/bin/zinc -start -nailed -scala-home=$SCALA_HOME'
          sh 'mvn clean install'
        }
        junit allowEmptyResults: true, testResults: '**/surefire-reports/*.xml'
      }
    }
  }
}
