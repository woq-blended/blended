#!/usr/bin/env groovy
pipeline {
  agent any
  stages {
    stage('Prepare') {
      steps {
        sh '''
          docker images
        '''
      }
    }
  }
}
