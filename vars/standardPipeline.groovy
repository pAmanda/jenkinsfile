def call(body) {

    def scmVars = checkout scm
    def branchName = scmVars.GIT_BRANCH

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])
    
    node {
        // Clean workspace before doing anything
        deleteDir()

        try {
            stage('Checkout') {
                echo "parameters = ${VERSION} e ${NEXT_VERSION}"
                echo "branhc = " + branchName
                sh 'printenv'
                checkout scm
            }
            stage('Build') {
                echo "Initializing Build phase"
                sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
            }
            stage('Test') {
                if(env.BRANCH_NAME != "**/feature/*") {
                    echo "Initializing test phase"
                    sh "mvn test"
                }
            }
            stage ('Analyse') {
                if(env.BRANCH_NAME != "**/feature/*") {
                    echo "Initializing Analyse phase"
                    //withSonarQubeEnv('Sonar') {
                        //sh "mvn sonar:sonar"
                    //}
                }
            }
            stage('Quality Gate') {
                 echo "Initializing Quality Gate phase"
               // if(env.BRANCH_NAME != "**/feature/*") {
                    //timeout(time: 1, unit: 'HOURS') {
                      //  def qg = waitForQualityGate()
                       // if (qg.status != 'OK') {
                         //   error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        //}
                    //}
               // }
            }
            stage('Archive') {
                if(env.BRANCH_NAME == "**/master") {
                    echo 'Initializing Archive phase'
                    sh 'mvn deploy -Dmaven.test.skip=true'
                } else if(env.BRANCH_NAME == "**/hotfix") {
                    echo 'Initializing Archive phase'
                    sh 'mvn deploy -Dmaven.test.skip=true'
                }    
            }
            stage ('Release') {   
                switch(env.BRANCH_NAME){
                    case "**/master":
                        if(${VERSION} != ${NEXT_VERSION}){
                            echo 'Initializing Release phase'
                            sh 'git checkout master'
                            sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                        }
                        break
                    case "**/hotfix":
                        if(${VERSION} != ${NEXT_VERSION}){
                            echo 'Initializing Release phase'
                            sh 'git checkout hotfix'
                            sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
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
