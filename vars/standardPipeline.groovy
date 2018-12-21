def call(body) {

    // Pega a variável CABAL passada como parâmetro e extrai as variáveis internas importantes.
    def cabal = CABAL
    def environment = ''
    def next_version = ''
    def version = ''
    def tag_name = ''
    def branch_name = ''

    println("CABAL: " + this.cabal)

    if(cabal?.trim()) {
        this.environment = 'default'

    } else {
        def parameters = cabal.split(';')
        def map = [:]
        for(int i = 0; i < parameters.size(); i++) {
            println("Parâmetro " + i + ": " + parameters[i])
            def param = parameters[i].split(':')
            this.map.put(param[0].trim(), param[1].trim())
        }

        this.environment = map.get('ENVIRONMENT')
        this.next_version = map.get('NEXT_VERSION')
        this.version = map.get('VERSION')
        this.tag_name = map.get('TAG_NAME')
        this.branch_name = map.get('BRANCH_NAME')
    }

    println("environment: " + environment + " next_version: " + next_version + " version: " + version + " tag_name: " + tag_name + " branch_name: " + branch_name)

    def commit_message = null
    node {
        deleteDir()
        checkout scm
        this.commit_message = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()
    }

    if (this.commit_message.startsWith("[maven-release-plugin]")) {
        currentBuild.result = 'SUCCESS'
        echo "Commit message starts with maven-release-plugin. Exiting..."

    } else if(this.environment == 'staging' || this.environment == 'default') {
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
                            this.branch_name = this.branch_name?.trim() ? get_branch_name(GIT_BRANCH) : get_branch_name(this.branch_name)
                        }
                        echo "BRANCH_NAME = " + this.branch_name
                        echo "PARAMETERS = VERSION: " + this.version + " e NEXT_VERSION: " + this.next_version
                        sh 'git checkout ' + this.branch_name
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
                            branch_is_master_hotfix() && this.version?.trim() && this.next_version?.trim()
                        }
                    }
                    steps {
                        echo "===================================================="
                        echo "Release Stage"
                        echo "===================================================="
                        sh 'mvn -B release:prepare release:perform -DreleaseVersion=' + this.version +  '-DdevelopmentVersion=' + this.next_version
                    }
                }
                stage('Docker') {
                    when {
                        expression {
                            branch_is_master_hotfix() && this.version?.trim() && this.next_version?.trim()
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
        if(this.tag_name == null || this.tag_name == '') {
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
                            echo 'tag_name = ' + this.tag_name
                            sh 'git checkout ' + this.tag_name
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
