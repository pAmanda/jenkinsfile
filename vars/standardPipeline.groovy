def call(body) {

    def cabal = CABAL.trim()

    def parameters = CABAL.split(';')

    def map = [:]

    for(int i = 0; i < parameters.size(); i++) {
        println(parameters[i])
    }

    def commit_message = null
    node {
        deleteDir()
        sh 'git --version'
        checkout scm
        commit_message = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()
    }

    pipeline {
        agent any
        stages {
            stage('Build') {
                steps {
                    echo "Parameter: " + cabal
                    echo "Split: " + parameters.size()
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

def Boolean branch_is_master_hotfix() {
    return branch_is_master() || branch_is_hotfix()
}

def Boolean different_versions() {
    return VERSION != NEXT_VERSION;
}