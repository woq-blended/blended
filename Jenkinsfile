pipeline {
  agent any
  stages {
    stage('Stage 1') {
      steps {
        echo 'Pfad : $(pwd)'
        echo $(git describe --tags)
      }
    }
  }
}
