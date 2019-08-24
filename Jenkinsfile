#!/usr/bin/env groovy
pipeline {
  agent any
  stages {
    stage('Prepare') {
      steps {
        ansiColor('xterm') {
          sh '''#!/bin/bash -l
            sbt update
          '''
        }
      }
    }
    stage('Compile') {
      steps {
        ansiColor('xterm') {
          sh '''#!/bin/bash -l
             sbt clean publishLocal
          '''
        }
      }
    }
    stage('Unit-Test') {
      steps {
        ansiColor('xterm') {
          sh '''#!/bin/bash -l
            sbt clean update coverageOn test
          '''
        }
      }
    }
  }
}
