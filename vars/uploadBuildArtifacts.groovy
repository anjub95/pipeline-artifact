
import groovy.json.JsonOutput
import groovy.transform.Field

@Field
static argDesc = [
        name: 'uploadBuildArtifacts',
        description: 'Upload artifacts to Artifactory.',
        args: [
                groupId: [
                        description: 'The ID of the group of artifacts to upload.',
                        validate: { it.getAt(it.size() - 1) == '/' ? it : it + '/' },
                        default: null,
                ],
                artifactId: [
                        description: 'The ID of the artifacts to upload.',
                        default: null,
                ],
                versionToUpload: [
                        description: 'The version of the artifacts to upload.',
                        default: null,
                ],
                artifactoryRepository: [
                        description: 'The Artifactory repository of the artifacts to upload.',
                ],
                artifactoryServerURL: [
                        description: 'URL for the Artifactory server.',
                ],
                artifactoryCred: [
                        description: 'Credentials to use for accessing Artifactory.  A default is provided.',
                        default: null,
                ],
                artifacts: [
                        description: 'A list of artifacts to upload.  One of "artifacts" or "buildArtifactType" must be provided.',
                        default: null,
                ],
                buildArtifactType: [
                        description: 'The type of artifacts to upload, e.g., the filename extension.  One of "artifacts" or "buildArtifactType" must be provided.',
                        default: null,
                ],
                buildType:  [
                        description: 'Type of build being performed.  Can be either "MAVEN" or "GRADLE".  Defaults to "MAVEN".',
                        default: 'MAVEN',
                        validate: { it.toUpperCase() },
                ],
                group: [
                        description: 'Specify the group of artifacts to upload.',
                        default: null,
                ],
                name: [
                        description: 'Specify the name of artifacts to upload.',
                        default: null,
                ],
        ],
]

def call(body){
    library 'pipeline-common'
    def config = demoCommon.parseArgs(argDesc, body)

    if (config.artifacts == null) {
        if (!config.buildArtifactType) {
            currentBuild.result = 'ABORTED'
            error 'One of "artifacts" or "buildArtifactType" must be provided.'
        }
        if (config.buildType == 'GRADLE'){
            config.artifacts = ["*.${config.buildArtifactType}"]
        } else {
          config.artifacts = ["*.${config.buildArtifactType}", "./demo-artifacts/*.pom"]
        }
    }
    def buildInfoToPub = upload(config.artifactoryServerURL, config.artifactoryCred, config.versionToUpload, config.artifacts, config.artifactoryRepository, config.groupId, config.artifactId,config.buildType,config)
    return buildInfoToPub
}

def upload(artifactoryServerURL, artifactoryCred, versionToUpload, artifacts, artifactoryRepository, groupId, artifactId,buildType,config) {
    def server = Artifactory.newServer url: artifactoryServerURL, credentialsId: artifactoryCred
    def buildInfoToPub = Artifactory.newBuildInfo()
    buildInfoToPub.name = artifactId
    buildInfoToPub.number = versionToUpload
    def uploadSpec
    def uploadInfo

    for (String artifact : artifacts) {
        if (!artifact.contains('*')) {
            if (!fileExists(artifact)) {
                sh "ls -R"
                error "File to upload to Artifactory not found on Jenkins workspace.  Looking for file ${artifact}"
            }
        }
        if (buildType == 'GRADLE') {
            buildInfoToPub.name = config.name
            uploadSpec = JsonOutput.toJson([
                    files: [
                            [
                                    pattern  : artifact,
                                    target   : "${artifactoryRepository}/${config.group.replaceAll('\\.', '\\/')}/${config.name}/${versionToUpload}/",
                                    recursive: "true",
                                    "excludePatterns": ["tools/"]
                            ],
                    ],
            ])
        } else {
            uploadSpec = JsonOutput.toJson([
                    files: [
                            [
                                    pattern  : artifact,
                                    target   : "${artifactoryRepository}/${groupId.replaceAll('\\.', '\\/')}${artifactId}/${versionToUpload}/",
                                    recursive: "true",
                                    "excludePatterns": ["tools/"]
                            ],
                    ],
            ])
        }
        server.upload spec: uploadSpec, buildInfo: buildInfoToPub
    }
    server.publishBuildInfo(buildInfoToPub)
    return buildInfoToPub
}
