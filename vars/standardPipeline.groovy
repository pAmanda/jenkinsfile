def call(body) {

    // properties([
    //     durabilityHint('PERFORMANCE_OPTIMIZED')
    // ])

    def commit_message = null
    node {
        commit_message = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()   
    }

    println ENVIRONMENT

    if (commit_message.startsWith("[maven-release-plugin]")) {    
        currentBuild.result = 'SUCCESS'
        echo "Commit message starts with maven-release-plugin. Exiting..."   
    } else {
        switch(ENVIRONMENT) {
            case 'Homologation':
                default_environment()
                break
            case 'Production':
                production_environment()
                break
            default:
                default_environment()
                break
        }
    }
}

def void default_environment() {
    pipeline { 
        agent any
        stages {
            stage('Checkout') {
                steps {
                    echo "===================================================="
                    echo "Checkout Stage"
                    echo "===================================================="
                    script {
                        BRANCH_NAME = (BRANCH_NAME == '' || BRANCH_NAME == null) ?  get_branch_name(GIT_BRANCH) : get_branch_name(BRANCH_NAME) 
                    }
                    echo "BRANCH_NAME = " + BRANCH_NAME
                    echo "PARAMETERS = VERSION: " + VERSION + " e NEXT_VERSION: " + NEXT_VERSION
                    sh 'git checkout ' + BRANCH_NAME
                }
            }
            stage('Build') {
                steps {
                    echo "===================================================="
                    echo "Build Stage"
                    echo "===================================================="
                    sh "mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true"
                }
            }
            stage('Test') { 
                steps {                        
                    echo "===================================================="
                    echo "Test Stage"
                    echo "===================================================="
                    sh "mvn test"
                }
            }
        }
    }
}

def void production_environment() {
    pipeline {
        agent any
        stages {
            stage('Deploy') {
                steps {
                    echo "Olá!"
                    echo Production
                    echo Test
                }
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
    return BRANCH_NAME.startsWith(branch)
}
