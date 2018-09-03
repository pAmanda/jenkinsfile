def call(body) {

    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])

    node {
        // Clean workspace before doing anything        
        deleteDir()
        
        // Exporting Docker env variables
        // Change this variables
        // env.DOCKER_HOST="tcp://192.168.99.100:2376"
        // env.DOCKER_CERT_PATH="/Users/" + env.USER + "/.docker/machine/machines/default"
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
                stage('Docker') {
                    if(VERSION != NEXT_VERSION) {
                        if(branch_is_master() || branch_is_hotfix()) {
                            echo "===================================================="
                            echo "Docker Stage"
                            echo "===================================================="
                            openshift.withCluster('openshift-pocose', '4eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJzaXBwZSIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VjcmV0Lm5hbWUiOiJqZW5raW5zLXRva2VuLWNxeHJ0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZXJ2aWNlLWFjY291bnQubmFtZSI6ImplbmtpbnMiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC51aWQiOiI2ZmJlNWY0Yy1hN2VkLTExZTgtYmFmYS1hYWQ5ZGI0NzZhMzciLCJzdWIiOiJzeXN0ZW06c2VydmljZWFjY291bnQ6c2lwcGU6amVua2lucyJ9.Z3dFK3IQ3yoTQsyZYfPT0RDvwHmG4pm9BNBBkKdVYOHAwkuULfMzCuGC5vZ9uPDFXFsdcdg2nBMlj66nnhKKaxFeEdoJOf5eyRM0VK1rF0VL9b3fwtd8-UgmvjISgWO8Eous1Gr3uAUkOnw9HA3ioD5CBsz9JIMLhIG7bR0zkPoXcd16gw_Q3RSL_5BMnuFXLIroQtmjfVDjKzZwHK6Q_dcVCXdQMHLpd-yx76ahOiQdRjWmxZ4CxGTr9y8ccCRcECw3TWLcN32bSY3Z5xRVeESXEbBaWeJaeBuJUOfTyMdbJuvagj0xJMrmjsRU1RCAF2nKXYUzzq7lo7u3qulAMg') {
                                sh "mvn package docker:build docker:push"
                            }
                            // sh "mvn package docker:build docker:push"
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
