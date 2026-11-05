void buildEnvironment(final Closure closure) {
    withCredentials([usernamePassword(credentialsId: 'ci-user-artifactory-token', usernameVariable: 'repository_username', passwordVariable: 'repository_password')]) {
        configFileProvider([configFile(fileId: 'gradle-settings', targetLocation: "${GRADLE_USER_HOME}/init.d/repositories.gradle")]) {
            closure()
        }
    }
}

void buildGitEnvironment(final Closure closure) {
    buildEnvironment {
        withCredentials([usernamePassword(credentialsId: 'ci-user-bitbucket-token', usernameVariable: 'bitbucket_username', passwordVariable: 'bitbucket_password')]) {
            sshagent(credentials: ['ci-user-ssh']) {
                withCredentials([sshUserPrivateKey(credentialsId: 'ci-user-ssh', usernameVariable: 'ssh_username', keyFileVariable: 'KEYFILE_NOT_NEEDED')]) {
                    closure()
                }
            }
        }
    }
}

void npmCredentials(final Closure body) {
    configFileProvider([configFile(fileId: 'npmrc', targetLocation: "${WORKSPACE}/jenkins.npmrc")]) {
        body()
    }
}

return this