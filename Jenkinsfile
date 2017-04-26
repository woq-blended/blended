pipeline {
  agent any
  stages {
    stage('Prepare Build') {
      checkout scm
      def buildenv = docker.build "blended-build"
  
      buildenv.inside {
        stage "prepare"
        sh "mvn -version"
      }
    }
  }
}
