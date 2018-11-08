def call(body) {

    // properties([
    //     durabilityHint('PERFORMANCE_OPTIMIZED')
    // ])

    ENVIRONMENT = ENVIRONMENT.trim()
    def nothing = (ENVIRONMENT == "" || ENVIRONMENT == null) ? true : false
    def commit_message = null
    node {
        commit_message = sh (script: 'git log -1 --pretty=%B',returnStdout: true).trim()   
    }
    if (commit_message.startsWith("[maven-release-plugin]")) {    
        currentBuild.result = 'SUCCESS'
        echo "Commit message starts with maven-release-plugin. Exiting..."   

    } else if(ENVIRONMENT == 'Homologation' || nothing) {
        pipeline { 
            agent any
            tools {
                maven 'Maven3'
                jdk 'jdk1.8' 
            }
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
                        withSonarQubeEnv('Sonar') {                          
                            //sh "mvn sonar:sonar"

                        }   
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
			            // timeout(time: 1, unit: 'HOURS') {
                    	//    def qg = waitForQualityGate()
			            //     if (qg.status != 'OK') {
		                //       error "Pipeline aborted due to quality gate failure: ${qg.status}"
    			        //     } 
		                // }                      
                    }  
                }
                stage('Archive') {
                    when {
                        expression {
                            branch_is_master_hotfix() && different_versions()
                        } 
                    }
                    steps {
                        echo "===================================================="
                        echo "Release Stage"
                        echo "===================================================="
                        sh 'mvn -B release:prepare -DreleaseVersion=${VERSION} -DdevelopmentVersion=${NEXT_VERSION}'
                    }
                }
                stage('Docker') {
                    when {
                        expression {
                            branch_is_master_hotfix() && different_versions()
                        } 
                    }
                    steps {
                        echo "===================================================="
                        echo "Docker Stage"
                        echo "===================================================="
                        echo "Fazendo checkout na tag gerada"
                        sh 'git checkout $(git describe --tags $(git rev-list --tags --max-count=1))'
                        echo "===================================================="
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
    return BRANCH_NAME.startsWith(branch)
}

def Boolean branch_is_master_hotfix() {
    return branch_is_master() || branch_is_hotfix()
}

def Boolean different_versions() {
    return VERSION != NEXT_VERSION;
}
