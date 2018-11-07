def call(body) {

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])

    if(test) {
        pipeline { 
            agent any
            stages {
                stage('Checkout') {
                    step {
                        echo "Olá!"
                    }
                }
            }
        }
    } else {
        pipeline {
            agent any
            stages {
                stage('Deploy') {
                    step {
                        echo "Olá!"
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
