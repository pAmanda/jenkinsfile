def call(body) {

        properties([
            durabilityHint('PERFORMANCE_OPTIMIZED')
        ])

        def config = [:]
        body.resolveStrategy = Closure.DELEGATE_FIRST
        body.delegate = config
        body()

        node {
            // Clean workspace before doing anything
            deleteDir()

            try {
                stage ('Clone') {
                    sh "echo 'Oieee...'"
                    sh 'printenv'
                    checkout scm
                }
                stage ('Build') {
                   if (env.BRANCH_NAME == 'master') {
                      echo 'I only execute on the master branch'
                   } else {
                    sh "echo 'building ${config.projectName} ...'"
                    sh "echo 'variabless = ${config.xpto} + ${config.version}'"
                    sh 'mvn clean install -Dmaven.test.skip=true -Dmaven.javadoc.skip=true'
                   }
                }
                stage ('Tests') {
                    parallel 'static': {
                        sh "echo 'shell scripts to run static tests...'"
                    },
                    'unit': {
                        sh "echo 'shell scripts to run unit tests...'"
                    },
                    'integration': {
                        sh "echo 'shell scripts to run integration tests...'"
                    }
                }
                stage ('Deploy') {
                    sh "echo 'deploying to server ${config.serverDomain}...'"
                }
            } catch (err) {
                currentBuild.result = 'FAILED'
                throw err
            }
        }
    }
