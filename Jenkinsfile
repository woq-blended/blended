#!/usr/bin/env groovy
pipeline {
  agent {
    docker { image 'node:7-alpine' }
  }
  stages {
    stage('Prepare') {
      steps {
        sh 'node --version'
      }
    }
  }
}
