def call(any) {
        
        properties([
            durabilityHint('PERFORMANCE_OPTIMIZED')
        ])
      
        node {
            // Clean workspace before doing anything
            deleteDir()

            try {
                  stage('Checkout') {
                     git 'https://github.com/andersonlfeitosa/poc-jenkins-buildpipeline.git'
                  }
                  stage('Clean') {
                     sh "mvn clean"
                  }
                  stage('Build') {
                     sh "mvn install -DskipTests"
                  }
                  stage('Unit Tests') {
                     sh "mvn test"
                  }
                  stage('Archive') {
                    sh "mvn deploy -DskipTest"
                  }
                  stage('Sonar') {
                    withSonarQubeEnv('Sonar') {
                      sh "mvn sonar:sonar"
                    }
                  }
                  stage('Quality Gate') {
                    timeout(time: 1, unit: 'HOURS') {
                      def qg = waitForQualityGate()
                      if (qg.status != 'OK') {
                        error "Pipeline aborted due to quality gate failure: ${qg.status}"
                      }
                    }
                  }
                  stage('Docker') {
                    //sh "mvn package docker:build docker:push"
                  }
            } catch (err) {
                currentBuild.result = 'FAILED'
                throw err
            }
        }
    }
