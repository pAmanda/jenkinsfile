def call(body) {

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])
    
    node {
        // Clean workspace before doing anything
        deleteDir()
        def VARS = checkout scm
        def BRANCH_NAME = VARS.GIT_BRANCH

        try {
            stage('Checkout') {
                checkout scm
                echo "parameters = " + VERSION + " e " + NEXT_VERSION
                echo "branch = " + BRANCH_NAME
            }
            stage('Build') {
                echo "Initializing Build phase"
                sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
            }
            stage('Test') {
                if(BRANCH_NAME != "**/feature/*") {
                    echo "Initializing test phase"
                    sh "mvn test"
                }
            }
            stage ('Analyse') {
                if(BRANCH_NAME != "**/feature/*") {
                    echo "Initializing Analyse phase"
                    //withSonarQubeEnv('Sonar') {
                        //sh "mvn sonar:sonar"
                    //}
                }
            }
            stage('Quality Gate') {
                 if(BRANCH_NAME != "**/feature/*") {
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
                if(BRANCH_NAME == "**/master" || BRANCH_NAME == "**/hotfix") {
                    echo 'Initializing Archive phase'
                    sh 'mvn deploy -Dmaven.test.skip=true'
                }
            }
            stage ('Release') {
                if(VERSION != NEXT_VERSION) {
                    echo 'Initializing Release phase'
                    switch(BRANCH_NAME){
                        case "**/master":
                            sh 'git checkout master'
                            sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                            break
                        case "**/hotfix":
                            sh 'git checkout hotfix'
                            sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                    }
                }
            }
                
            stage('Docker') {
                if(VERSION != NEXT_VERSION) {
                    if(BRANCH_NAME.contains("**/master") || BRANCH_NAME.contains("**/hotfix")) {
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
