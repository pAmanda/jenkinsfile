def call(body) {

    // properties([
    //     durabilityHint('PERFORMANCE_OPTIMIZED')
    // ])
    node {
        def commit_message = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()
        if (commit_message.startsWith("[maven-release-plugin]")) {    
            currentBuild.result = 'SUCCESS'
            echo "Commit message starts with maven-release-plugin. Exiting..."   
        }     
    }

    if(Test == Production) {
        throw new Exception('Tipos de ambiente para deploy não podem ter o mesmo valor.')
    }

    if(Test == 'true') {
        pipeline { 
            agent any
            stages {
                stage('Checkout') {
                    steps {
                        echo 'Olá!'
                        echo GIT_BRANCH
                        echo Test
                        echo ${Branch Name}
                        println TEST.getClass()
                    }
                }
            }
        }
    } else {
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
