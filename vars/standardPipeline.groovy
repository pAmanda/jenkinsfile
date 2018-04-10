def call(body) {

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])

    node {
        // Clean workspace before doing anything        
        deleteDir()
        
        // Exporting Docker env variables
        // Change this variables
        env.DOCKER_HOST="tcp://192.168.99.100:2376"
        env.DOCKER_CERT_PATH="/Users/" + env.USER + "/.docker/machine/machines/default"
        env.PATH = "${tool 'Maven3'}/bin:${env.PATH}"
        env.PATH = "${tool 'jdk1.8'}/bin:${env.PATH}"
        
        def VARS = checkout scm
        def COMMIT_MESSAGE = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()
        
        if (COMMIT_MESSAGE.startsWith("[maven-release-plugin]") 
            && (env.BRANCH_NAME == '' || env.BRANCH_NAME == null)) {
            
            currentBuild.result = 'SUCCESS'
            echo "Commit message starts with maven-release-plugin. Exiting..."
            
        } else {
            env.BRANCH_NAME = (env.BRANCH_NAME == '' || env.BRANCH_NAME == null) ?  get_branch_name(VARS.GIT_BRANCH) : get_branch_name(env.BRANCH_NAME) 
            try {
                stage('Checkout') {
                    echo "===================================================="
                    echo "Checkout Stage"
                    echo "===================================================="
                    //checkout scm
                    echo "branch name = " + env.BRANCH_NAME
                    echo "parameters = " + VERSION + " e " + NEXT_VERSION
                    sh 'git checkout '+ env.BRANCH_NAME
                }
                stage('Build') {
                    echo "===================================================="
                    echo "Build Stage"
                    echo "===================================================="
                    sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
                }
                stage('Test') {
                    if(!branch_is_feature()) {
                        echo "===================================================="
                        echo "Test Stage"
                        echo "===================================================="
                        sh "mvn test"
                    }
                }
                stage ('Analyse') {
                    if(!branch_is_feature()) {
                        echo "===================================================="
                        echo "Analyse Stage"
                        echo "===================================================="
                        withSonarQubeEnv('sonar') {
                            sh "mvn sonar:sonar"
                        }
                    }
                }
                stage('Quality Gate') {
                     if(!branch_is_feature()) {
                        echo "===================================================="
                        echo "Quality Gate Stage"
                        echo "===================================================="
                        timeout(time: 1, unit: 'HOURS') {
                            def qg = waitForQualityGate()
                            if (qg.status != 'OK') {
                                error "Pipeline aborted due to quality gate failure: ${qg.status}"
                            }
                        }
                    }
                }
                stage('Archive') {
                    if(branch_is_master() || branch_is_hotfix()) {
                        echo "===================================================="
                        echo "Archive Stage"
                        echo "===================================================="
                        sh 'mvn deploy -Dmaven.test.skip=true'
                    }
                }
                stage ('Release') {
                    if(VERSION != NEXT_VERSION) {
                        if(branch_is_master() || branch_is_hotfix()) {
                            echo "===================================================="
                            echo "Release Stage"
                            echo "===================================================="
                            sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                        }
                    }
                }
                stage('Docker') {
                    if(VERSION != NEXT_VERSION) {
                        if(branch_is_master() || branch_is_hotfix()) {
                            echo "===================================================="
                            echo "Docker Stage"
                            echo "===================================================="
                            sh "mvn package docker:build docker:push"
                        }
                    }
                }
            } catch (error) {
                currentBuild.result = 'FAILED'
                throw error
            }   
        }
    }
}

def String get_branch_name(branch_name) {
    return branch_name.replaceAll("origin/", "").trim();
}

def Boolean branch_is_feature() {
    return test_branch_name("feature/")
}

def Boolean branch_is_master() {
    return test_branch_name("master")
}

def Boolean branch_is_develop() {
    return test_branch_name("develop")
}

def Boolean branch_is_hotfix() {
    return test_branch_name("hotfix/")
}

def Boolean test_branch_name(branch) {
    return env.BRANCH_NAME.startsWith(branch)
}
