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
* return version from package.json file
*
* @param fname full path to package.json
*/
def get_version ( fname ) {
  def jsonText = readFile(fname)
  def json = new JsonSlurper().parseText(jsonText)
  return json.version
}

/**
* get latest tag name (version)
*
*/
def get_tag_version (){
  sh "git describe --tags `git rev-list --tags "+
  "--max-count=1` | sed s/^v//g | "+
  "sed s/-.*//g > ver.txt"
  return readFile('ver.txt').trim()
}

/**
* write diff between HEAD and latest tag in nice formatted way
*
*/
def get_tags_diff () {
  try {
    sh "git describe --tags"
    sh 'git log --no-merges $(git describe --tags '+
    '`git rev-list --tags --max-count=1`)..HEAD '+
    '--pretty=\'tformat:- %s\' > changes.txt'
    } catch (e) {
      sh 'git log --no-merges '+
      '--pretty=\'tformat:- %s\' > changes.txt'
    }
    return 'changes.txt'
  }

  /**
  * check it its a pull request
  *
  */
  def check_pr ( env_vars ) {
    return env_vars.CHANGE_TARGET ? true : false
  }

  /**
  * Find if its github.com or enterprise installation
  * return github api url and credentials to use
  *
  */
  def guess_github_settings () {
    sh 'git remote -v | grep origin | head -1 | '+
    'awk \'{print $2}\' | '+
    'sed \'s/git@//g;s/\\.git//g\' > guess'
    def str = readFile('guess').trim().split(':')
    def ret = [api: '', project: str[1], cred: '']
    if (str[0] == 'github.com')
    {
      ret.api = 'https://api.'+str[0]
      ret.cred = 'github-token'
      } else {
        ret.api =  'https://'+str[0]+'/api/v3'
        ret.cred = 'github-enterprise-token'
      }
      println 'Project: '+ret.api+' '+ret.cred+' '+
      ret.project
      return ret
    }

    /**
    * Create new version in github with the new tag created
    *
    * @param url github api url
    * @param path project path
    * @parama auth auth key id
    * @param version_name version name
    * @param version_tag tag name to create the version from
    * @param prerelease prerelease setting in github
    * @param changes changes log
    */
    def create_version_json (url, path, auth, version_name,
      version_tag, prerelease, changes) {
        echo 'creating new github version'
        def obj = [tag_name: version_tag, name: version_name,
        body: changes, draft: false, prerelease: prerelease]
        def json = JsonOutput.toJson(obj)
        println "Sending to github:\n${json}"
        withCredentials([[$class:
          'UsernamePasswordMultiBinding', credentialsId: auth,
          passwordVariable: 'PW', usernameVariable: 'UN']]) {
            res = httpRequest consoleLogResponseBody: true,
            customHeaders: [[name: 'Authorization', value: 'token '+env.PW]],
            httpMode: 'POST', requestBody: json, url: url+"/repos/"+path+"/releases"
          }
          echo "result:\n"+res.content
        }

        return this;
