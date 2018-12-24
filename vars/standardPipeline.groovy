environment = ''
next_version = ''
version = ''
tag_name = ''
branch_name = ''
test = null


def call(body) {

    // Pega a variável CABAL passada como parâmetro e extrai as variáveis internas importantes.
    def cabal = CABAL
    test = null
    println("CABAL: " + cabal + "Bool: " + !test?.trim())

    if(!cabal?.trim()) {
        environment = 'default'
        next_version = ''
        version = ''
        tag_name = ''
        branch_name = ''

    } else {
        def parameters = cabal.split(';')
        def map = [:]
        for(int i = 0; i < parameters.size(); i++) {
            println("Parâmetro " + i + ": " + parameters[i])
            def param = parameters[i].split(':')
            map.put(param[0].trim(), param[1].trim())
        }
        environment = map.get('ENVIRONMENT')
        next_version = map.get('NEXT_VERSION')
        version = map.get('VERSION')
        tag_name = map.get('TAG_NAME')
        branch_name = map.get('BRANCH_NAME')
    }

    println("environment: " + environment + " next_version: " + next_version + " version: " + version + " tag_name: " + tag_name + " branch_name: " + branch_name)

    def commit_message = null
    node {
        deleteDir()
        checkout scm
        commit_message = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()
    }

    if (commit_message.startsWith("[maven-release-plugin]")) {
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
//                            branch_name = !branch_name?.trim() ? get_branch_name(GIT_BRANCH) : get_branch_name(branch_name)
                            echo "BRANCH_NAME É : " + branch_name + " e " + !branch_name?.trim() + " e " + branch_name?.trim()
                            if(branch_name == null || branch_name.trim() == '') {
                                echo "branch é null"
                                branch_name = get_branch_name(GIT_BRANCH)
                            } else {
                                branch_name = get_branch_name(branch_name)
                            }
                        }
                        echo "GIT_BRANCH = " + GIT_BRANCH
                        echo "BRANCH_NAME = " + branch_name
                        echo "PARAMETERS = VERSION: " + version + " e NEXT_VERSION: " + next_version
                        sh 'git checkout ' + branch_name
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
                                branch_is_feature()
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
                                branch_is_feature()
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
                                branch_is_feature()
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
                            branch_is_master_hotfix()
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
                            branch_is_master_hotfix() && version?.trim() && next_version?.trim()
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Release Stage"
                        echo "===================================================="
                        sh 'mvn -B release:prepare release:perform -DreleaseVersion=${version} -DdevelopmentVersion=${next_version}'
                    }
                }
                stage('Docker') {
                    when {
                        expression {
                            branch_is_master_hotfix() && version?.trim() && next_version?.trim()
                        }
                    }
                    steps {
                        echo "DOCKER"
                    }
                    //step
                }
            }
            post {
                always {
                    deleteDir()
                }
            }
        }
    } else {
        if(tag_name == null || tag_name == '') {
            echo "O parâmetro tag_name é obrigatório!"
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
                            echo 'tag_name = ' + tag_name
                            sh 'git checkout ' + tag_name
                        }
                    }
                    stage('Docker') {
                        // input {
                        //     message "Pode continuar?"
                        //     ok "Sim"
                        //     submitter "jenkins-admin"
                        // }
                        //step
                        steps {
                            echo "DOCKER"
                        }
                    }
                }
                post {
                    always {
                        deleteDir()
                    }
                }
            }
        }
    }
}

def String get_branch_name(branch_name) {
    return branch_name.replaceAll("origin/", "").trim()
}

def Boolean branch_is_feature() {
    return test_branch_name("feature/")
}

def Boolean branch_is_master() {
    return test_branch_name("master")
}

def Boolean branch_is_hotfix() {
    return test_branch_name("hotfix/")
}

def Boolean test_branch_name(branch) {
    return branch_name.startsWith(branch)
}

def Boolean branch_is_master_hotfix() {
    return branch_is_master() || branch_is_hotfix()
}
