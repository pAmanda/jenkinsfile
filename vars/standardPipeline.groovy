def call(body) {

    //Pega a variável CABAL passada como parâmetro e extrai as variáveis internas importantes.
    def cabal = CABAL
    def environment = 'default'
    def nextVersion = ''
    def version = ''
    def tagName = ''
    def branchName = ''


    if(!cabal?.trim()) {
        environment = 'default'

    } else {
        def parameters = cabal.split(';')
        def map = [:]
        for(int i = 0; i < parameters.size(); i++) {
            println("Parâmetro " + i + ": " + parameters[i])
            def param = parameters[i].split(':')
            map.put(param[0].trim(), param[1].trim())
        }
        environment = map.get('ENVIRONMENT')
        nextVersion = map.get('NEXT_VERSION')
        version = map.get('VERSION')
        tagName = map.get('TAG_NAME')
        branchName = map.get('BRANCH_NAME')
    }

    println("environment: " + environment + " nextVersion: " + nextVersion + " version: " + version + " tagName: " + tagName + " branchName: " + branchName)

    def commitMessage = null
    node {
        deleteDir()
        checkout scm
        commitMessage = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()
    }

    if (commitMessage.startsWith("[maven-release-plugin]") && environment != 'production') {
        currentBuild.result = 'SUCCESS'
        echo "Commit message starts with maven-release-plugin. Exiting..."

    } else if(environment == 'staging' || environment == 'default') {
        pipeline {
            agent any
            tools {
                maven 'maven'
                jdk 'JDK 1.8.0_66'
            }
            stages {
                stage('Checkout') {
                    steps {
                        echo "===================================================="
                        echo "Checkout Stage"
                        echo "===================================================="
                        script {
                            //Quando a branch vem nula, o null é tratado como String.
                            branchName = (branchName != 'null' && !branchName.isEmpty()) ? getBranchName(branchName) : getBranchName(GIT_BRANCH)

                        }
                        echo "branchName = " + branchName
                        echo "PARAMETERS = VERSION: " + version + " e nextVersion: " + nextVersion
                        sh 'git checkout ' + branchName
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
                    when {
                        not {
                            expression {
                                branchIsFeature(branchName)
                            }
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Test Stage"
                        echo "===================================================="
                        sh "mvn test"
                    }
                }
                stage('Analyse') {
                    when {
                        not {
                            expression {
                                branchIsFeature(branchName)
                            }
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Analyse Stage"
                        echo "===================================================="
//                        withSonarQubeEnv('Sonar') {
//                            sh "mvn sonar:sonar"
//
//                        }
                    }
                }
                stage('Quality Gate') {
                    when {
                        not {
                            expression {
                                branchIsFeature(branchName)
                            }
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Quality Gate Stage"
                        echo "===================================================="
//                        script {
//                            timeout(time: 1, unit: 'HOURS') {
//                                def qg = waitForQualityGate()
//                                if (qg.status != 'OK') {
//                                    error "Pipeline aborted due to quality gate failure: ${qg.status}"
//                                }
//                            }
//                        }
                    }
                }
                stage('Archive') {
                    when {
                        expression {
                            branchIsMasterHotfix(branchName)
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Archive Stage"
                        echo "===================================================="
                        sh 'mvn deploy -Dmaven.test.skip=true'
                    }
                }
                stage('Release') {
                    // input {
                    //     message "Pode continuar?"
                    //     ok "Sim"
                    //     submitter "jenkins-admin"
                    // }
                    when {
                        expression {
                            branchIsMasterHotfix(branchName) && version?.trim() && nextVersion?.trim()
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Release Stage"
                        echo "===================================================="
                        sh 'mvn -B release:prepare release:perform -DreleaseVersion=${version} -DdevelopmentVersion=${nextVersion}'
                    }
                }
                stage('Docker') {
                    when {
                        expression {
                            branchIsMasterHotfix(branchName) && version?.trim() && nextVersion?.trim()
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Docker Stage"
                        echo "===================================================="
                    }
                }
            }
        }
    } else {
        //Quando a tag vem nula, o null é tratado como String.
        if(tagName == 'null' || tagName.isEmpty()) {
            echo "O parâmetro tagName é obrigatório!"
            currentBuild.result = 'FAILURE'
        } else {
            pipeline {
                agent any
                stages {
                    stage('Checkout') {
                        steps {
                            echo "===================================================="
                            echo "Checkout Stage"
                            echo "===================================================="
                            echo 'tagName = ' + tagName
                            sh 'git checkout ' + tagName
                        }
                    }
                    stage('Docker') {
                        // input {
                        //     message "Pode continuar?"
                        //     ok "Sim"
                        //     submitter "jenkins-admin"
                        // }
                        steps {
                            echo "===================================================="
                            echo "Docker Stage"
                            echo "===================================================="
                        }
                    }
                }
            }
        }
    }
}

def String getBranchName(branchName) {
    return branchName.replaceAll("origin/", "").trim()
}

def Boolean branchIsFeature(branchName) {
    return testBranchName("feature/", branchName)
}

def Boolean branchIsMaster(branchName) {
    return testBranchName("master", branchName)
}

def Boolean branchIsHotfix(branchName) {
    return testBranchName("hotfix/", branchName)
}

def Boolean testBranchName(branchTest, branchName) {
    return branchName.startsWith(branchTest)
}

def Boolean branchIsMasterHotfix(branchName) {
    return branchIsMaster(branchName) || branchIsHotfix(branchName)
}
