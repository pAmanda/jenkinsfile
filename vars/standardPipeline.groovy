def call(body) {

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])
    
    node {
        // Clean workspace before doing anything
        deleteDir()
        def VARS = checkout scm
        def BRANCH_NAME = VARS.GIT_BRANCH
        def COMMIT_MESSAGE = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()

        try {
            stage('Checkout') {
                if(COMMIT_MESSAGE.contains("maven-release-plugin")) {
                    System.exit(0)
                }
                checkout scm
                echo "COMMIT_MESSAGE =  " + COMMIT_MESSAGE
                echo "parameters = " + VERSION + " e " + NEXT_VERSION
                echo "branch = " + BRANCH_NAME
            }
            stage('Build') {
                echo "Initializing Build phase"
                sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
            }
            stage('Test') {
                if(!BRANCH_NAME.contains("feature")) {
                    echo "Initializing test phase"
                    sh "mvn test"
                }
            }
            stage ('Analyse') {
                if(!BRANCH_NAME.contains("feature")) {
                    echo "Initializing Analyse phase"
                    //withSonarQubeEnv('Sonar') {
                        //sh "mvn sonar:sonar"
                    //}
                }
            }
            stage('Quality Gate') {
                 if(!BRANCH_NAME.contains("feature")) {
                    echo "Initializing Quality Gate phase"
                    //timeout(time: 1, unit: 'HOURS') {
                      //  def qg = waitForQualityGate()
                       // if (qg.status != 'OK') {
                         //   error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        //}
                    //}
                }
            }
            stage('Archive') {
                if(BRANCH_NAME.contains("master") || BRANCH_NAME.contains("hotfix")) {
                    echo 'Initializing Archive phase'
                    sh 'mvn deploy -Dmaven.test.skip=true'
                }
            }
            stage ('Release') {
                if(VERSION != NEXT_VERSION) {
                    switch(BRANCH_NAME){
                        case "origin/master":
                            echo 'Initializing Release phase'
                            sh 'git checkout master'
                            sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                            break
                        case "origin/hotfix":
                            echo 'Initializing Release phase'
                            sh 'git checkout hotfix'
                            sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                    }
                }
            }
                
            stage('Docker') {
                if(VERSION != NEXT_VERSION) {
                    if(BRANCH_NAME.contains("master") || BRANCH_NAME.contains("hotfix")) {
                         echo 'Initializing Docker phase'
                        //sh "mvn package docker:build docker:push"
                    }
                }
            }
        } catch (err) {
            currentBuild.result = 'FAILED'
            throw err
        }
    }
}
