/*
* Copyright 2016 Hewlett-Packard Development Company, L.P.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* Software distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and limitations under the License.
*/

import groovy.json.*

/**
* wrap with timestamper and ansi-color plugin
*
* @param body function to wrap
*
*/
def wraps(body) {
  wrap([$class: 'AnsiColorBuildWrapper', 'colorMapName': 'XTerm',
  'defaultFg': 1, 'defaultBg': 2]) {
    wrap([$class: 'TimestamperBuildWrapper']) {
      body()
    }
  }
}

/**
* run the pipeline (main logic)
*
* @param pipelineRepo: the repo of the pipeline path
* @param failOnTest pipeline to fail if tests failed
*/
def runPipeline(pipelineRepo, failOnTest = true) {
  def stages, utils, changeUrl, version
  fileLoader.withGit(pipelineRepo,
    'master', null, '') {
      stages = fileLoader.load('stages');
      utils = fileLoader.load('utils');
    }
    node {
      wraps {
        try{
          // init stages script - get project name
          def name = stages.init(pipelineRepo)
          dir(name){
            // clean up workspace
            stages.cleanup_workspace()
            // check out source code
            def info = stages.co_source()
            changeUrl = info["changeUrl"]
            version = info["version"]
            // run build
            stages.build()
            // run linter
            stages.lint()
            // run unit tests
            stages.test()
            // if unit tests failed (build set to 'USTABLE') and failOnTest
            // then skip version building
            if (currentBuild.result != 'UNSTABLE' || !failOnTest)
            {
              // get latest tagged version
              def tag_version = utils.get_tag_version()
              echo "latest tag version: "+tag_version
              echo 'versions: ['+version +'] ['+tag_version+']'
              // check that is its not PR, is master branch
              // and there is no version like that
              if (!utils.check_pr(env) && (version != tag_version) &&
              (env.BRANCH_NAME == 'master'))
              {
                // release version and get release notes
                def release_notes = stages.package_and_release(version)
                // notify  that version was release on slack
                slackSend color: 'good', message: "New version "+
                "Preprleased: ${env.JOB_NAME} ${env.BUILD_NUMBER}\n"+
                "Version: ${version}\nRelease Notes:\n"+
                "${release_notes}\n${env.BUILD_URL}${changeUrl}"
                } else if (utils.check_pr(env)) {
                  echo 'it\'s a pull request, not tagging'
                  } else if (env.BRANCH_NAME != 'master') {
                    echo 'not creating release for non master branches'
                    } else {
                      echo "version is the same, not tagging"
                    }
                    // send that build is done
                    slackSend color: 'good', message: "Build done: "+
                    "${env.JOB_NAME} ${env.BUILD_NUMBER}\n"+
                    "${env.BUILD_URL}${changeUrl}"
                    } else {
                      // send that unit tests failed
                      slackSend color: 'warning', message: "Unit tests failed: "+
                      "${env.JOB_NAME} ${env.BUILD_NUMBER}\n"+
                      "${env.BUILD_URL}${changeUrl}"
                    }
                    step([$class: 'GitHubCommitStatusSetter', statusResultSource:
                    [$class: 'ConditionalStatusResultSource', results: []]])
                  }
                  } catch (e) {
                    // job is failed
                    echo "Exception: ${e}"
                    slackSend color: 'danger', message: "Job failed: ${env.JOB_NAME}"+
                    " ${env.BUILD_NUMBER}\n${env.BUILD_URL}${changeUrl}"
                    currentBuild.result='FAILED'
                    step([$class: 'GitHubCommitStatusSetter', statusResultSource:
                    [$class: 'ConditionalStatusResultSource', results: []]])
                    error "${e}"
                  }
                  echo "status: [${currentBuild.result}]"
                }
              }
            }

            return this;
