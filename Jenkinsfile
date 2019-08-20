#!/usr/bin/env groovy
pipeline {
  agent {
    docker { image 'atooni:build-alpine:1.0.1'}
  }
  stages {
    stage('Prepare') {
      steps {
        sh 'java -version'
        sh 'node --version'
      }
    }
  }
}
