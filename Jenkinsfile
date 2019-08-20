#!/usr/bin/env groovy
pipeline {
  agent any
  stages {
    ansiColor('xterm') {
      stage('Prepare') {
        steps {
          sh '''
            docker run -u blended -v /var/lib/jenkins/workspace/blended_jenkins:/home/blended/project -v $HOME/.ivy2:/home/blended/.ivy2 atooni/build-alpine:1.0.1 /bin/bash -l -c "cd ~/project ; SBT_OPTS='-Xmx3072m -XX:MaxMetaspaceSize=1536m' sbt update"
          '''
        }
      }
    }
  }
}
