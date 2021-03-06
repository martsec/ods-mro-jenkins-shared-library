package org.ods.service

@Grab(group="com.konghq", module="unirest-java", version="2.4.03", classifier="standalone")

import com.cloudbees.groovy.cps.NonCPS

import java.net.URI

import kong.unirest.Unirest

import org.apache.http.client.utils.URIBuilder

class NexusService {

    URI baseURL

    String username
    String password

    NexusService(String baseURL, String username, String password) {
        if (!baseURL?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'baseURL' is undefined.")
        }

        if (!username?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'username' is undefined.")
        }

        if (!password?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'password' is undefined.")
        }

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. '${baseURL}' is not a valid URI.").initCause(e)
        }

        this.username = username
        this.password = password
    }

    @NonCPS
    def URI storeArtifact(String repository, String directory, String name, byte[] artifact, String contentType) {
        def response = Unirest.post("${this.baseURL}/service/rest/v1/components?repository={repository}")
            .routeParam("repository", repository)
            .basicAuth(this.username, this.password)
            .field("raw.directory", directory)
            .field("raw.asset1", new ByteArrayInputStream(artifact), contentType)
            .field("raw.asset1.filename", name)
            .asString()

        response.ifSuccess {
            if (response.getStatus() != 204) {
                throw new RuntimeException("Error: unable to store artifact. Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'.")
            }
        }

        response.ifFailure {
            def message = "Error: unable to store artifact. Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to store artifact. Nexus could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        return this.baseURL.resolve("/repository/${repository}/${directory}/${name}")
    }
    
    def URI storeArtifactFromFile(String repository, String directory, String name, File artifact, String contentType) {
        return storeArtifact(repository, directory, name, artifact.getBytes(), contentType)
    }
}
