#!/usr/bin/env groovy
pipeline {
  agent any
  stages {
    stage('Prepare') {
      steps {
        ansiColor('xterm') {
          sbt update 
          sh '''
            sbt update
          '''
        }
      }
    }
    stage('Compile') {
      steps {
        ansiColor('xterm') {
          sh '''
             sbt clean publishLocal"
          '''
        }
      }
    }
    stage('Unit-Test') {
      steps {
        ansiColor('xterm') {
          sh '''
            sbt clean update coverageOn test"
          '''
        }
      }
    }
  }
}
