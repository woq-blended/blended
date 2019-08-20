#!/usr/bin/env groovy
pipeline {
  agent {
    docker {
      image 'atooni/build-alpine:1.0.1'
      args '-u blended -v .:/home/blended/project'
    }
  }
  stages {
    stage('Prepare') {
      steps {
        sh 'java -version'
      }
    }
  }
}
