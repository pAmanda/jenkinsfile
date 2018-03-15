
    properties([
        durabilityHint('PERFORMANCE_OPTIMIZED')
    ])

    // parameters {
    //     string(name: 'version', defaultValue: '0.0.1-SNAPSHOT', description: 'Número da versão que será fechada.')
    //     string(name: 'next_version', defaultValue: '0.0.2-SNAPSHOT', description: 'Próxima versão de desenvolvimento.')
    // }

    node {
        // Clean workspace before doing anything
        deleteDir()

        try {
            stage('Checkout') {
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
                    withSonarQubeEnv('Sonar') {
                        sh "mvn sonar:sonar"
                    }
                }
            }
            stage('Quality Gate') {
                if(env.BRANCH_NAME != "**/feature/*") {
                    timeout(time: 1, unit: 'HOURS') {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        }
                    }
                }
            }
            stage('Archive') {
                if(env.BRANCH_NAME == "**/master" || en.BRANCH_NAME == "**/hotfix") {
                    echo 'Initializing Archive phase'
                    echo 'mvn deploy -Dmaven.test.skip=true'
                }
            }
            stage ('Release') {
                if((env.BRANCH_NAME == "**/master" || en.BRANCH_NAME == "**/hotfix") && ${next_version} != ${version}) {
                    echo 'Initializing Release phase'
                    sh 'mvn -B -Dtag=${project.artifactId}-${project.version} release:prepare -DreleaseVersion=${version} -DdevelopmentVersion=${next_version}'
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
