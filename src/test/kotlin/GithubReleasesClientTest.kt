package org.openstreetmap.josm.gradle.plugin.ghreleases

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.WireMockServer

import ru.lanwen.wiremock.ext.WiremockResolver
import ru.lanwen.wiremock.ext.WiremockResolver.Wiremock
import ru.lanwen.wiremock.ext.WiremockUriResolver
import ru.lanwen.wiremock.ext.WiremockUriResolver.WiremockUri


import com.beust.klaxon.JsonObject

class GithubReleasesClientTest {

  fun buildClient(apiUri: String) : GithubReleasesClient {
    val accessToken = System.getenv("GITHUB_ACCESS_TOKEN")
    val githubUser = System.getenv("GITHUB_USER")
    if (accessToken == null) {
      println("Warning: required environment variable GITHUB_ACCESS_TOKEN not set")
    }
    if (githubUser == null) {
      println("Warning: required environment variable GITHUB_USER not set")
    }
    val client = GithubReleasesClient()
    client.user = githubUser
    client.repository = "josm-scripting-plugin"
    client.accessToken = accessToken
    client.apiUrl = apiUri
    return client
  }


  @Test
    @Disabled
    fun `pagination with an empty Link header should work`() {
        val pagination = Pagination(null)
        assertFalse(pagination.hasNext)
        assertNull(pagination.nextUrl)
    }

    @Test
    @Disabled
    fun `pagination with a url of type rel="next" should work`() {
        val pagination = Pagination(
            "<https://api.github.com/search/code?q=addClass+user%3Amozilla&page=15>; rel=\"next\"")
        assertTrue(pagination.hasNext)
        assertEquals(pagination.nextUrl, "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=15")
    }

    @Test
    @ExtendWith(
      WiremockResolver::class,
      WiremockUriResolver::class
    )
    @Disabled
    fun `getLatestRelease should return a release for HTTP 200`(@Wiremock server: WireMockServer,
                                                                @WiremockUri uri: String) {
        val client = buildClient(uri)
        val path = "/repos/${client.user}/${client.repository}/releases/latest"

        // replies two release in the first page
        server.stubFor(get(urlPathEqualTo(path))
          .willReturn(aResponse()
            .withStatus(200)
            // link to the next page of releases
            .withBody("""{"id": 1}""")
          )
        )
        val release = client.getLatestRelease()
        assertNotNull(release)
        assertTrue(release is JsonObject)
        assertEquals(release?.int("id"), 1)
    }

    @Test
    @ExtendWith(
      WiremockResolver::class,
      WiremockUriResolver::class
    )
    @Disabled
    fun `getLatestRelease should return null for HTTP 404`(@Wiremock server: WireMockServer,
                                                                @WiremockUri uri: String) {
      val client = buildClient(uri)
      val path = "/repos/${client.user}/${client.repository}/releases/latest"

      // replies two release in the first page
      server.stubFor(get(urlPathEqualTo(path))
        .willReturn(aResponse()
          .withStatus(404)
          // link to the next page of releases
          .withBody("""{"message": "not found"}""")
        )
      )
      val release = client.getLatestRelease()
      assertNull(release)
    }

    @Test
    @ExtendWith(
      WiremockResolver::class,
      WiremockUriResolver::class
    )
    @Disabled
    fun `getLatestRelease should throw for an illegal http status code`(@Wiremock server: WireMockServer,
                                                           @WiremockUri uri: String) {
      val client = buildClient(uri)
      val path = "/repos/${client.user}/${client.repository}/releases/latest"

      // replies two release in the first page
      server.stubFor(get(urlPathEqualTo(path))
        .willReturn(aResponse()
          .withStatus(500)
          // link to the next page of releases
          .withBody("Server Error")
        )
      )
      assertThrows(GithubReleaseClientException::class.java, {
        try {
          client.getLatestRelease()
        } catch(e: Exception) {
          println(e)
          e.printStackTrace()
          throw e
        }
      })
    }

    @Test
    @ExtendWith(
      WiremockResolver::class,
      WiremockUriResolver::class
    )
    @Disabled
    fun `createRelease with only a tag name should work`(@Wiremock server: WireMockServer,
                                                        @WiremockUri uri: String) {
        val client = buildClient(uri)
        val path = "/repos/${client.user}/${client.repository}/releases"

        val tagName = "v0.0.1"

        // replies two release in the first page
        server.stubFor(post(urlPathEqualTo(path))
          .withRequestBody(matchingJsonPath("$[?(@.tag_name == '${tagName}')]"))
            .willReturn(aResponse()
              .withStatus(200)
              .withBody("""{"id": 1}""")
            )
        )
        val newRelease = client.createRelease(tagName)
        assertEquals(newRelease["id"], 1)
    }

    @Test
    @ExtendWith(WiremockResolver::class, WiremockUriResolver::class)
    fun `createRelease should acept optional parameters`(@Wiremock server: WireMockServer,
                                                         @WiremockUri uri: String) {
        val client = buildClient(uri)
        val path = "/repos/${client.user}/${client.repository}/releases"

        val tagName = "v0.0.1"
        val name = "aname"
        val targetCommitish = "acommitish"
        val body = "abody"
        val draft = true
        val prerelease = true

        // replies two release in the first page
        server.stubFor(post(urlPathEqualTo(path))
            .withRequestBody(matchingJsonPath("$[?(@.tag_name == '${tagName}')]"))
            .withRequestBody(matchingJsonPath("$[?(@.name == '${name}')]"))
            .withRequestBody(matchingJsonPath("$[?(@.target_commitish == '${targetCommitish}')]"))
            .withRequestBody(matchingJsonPath("$[?(@.body == '${body}')]"))
            .withRequestBody(matchingJsonPath("$[?(@.draft == true)]"))
            .withRequestBody(matchingJsonPath("$[?(@.prerelease == true)]"))
            .willReturn(aResponse()
              .withStatus(200)
              .withBody("""{"id": 1}""")
            )
        )
        val newRelease = client.createRelease(tagName, name=name,targetCommitish=targetCommitish, body=body,
          draft=draft, prerelease = prerelease)
        assertEquals(newRelease["id"], 1)
    }

    @Test
    @ExtendWith(
        WiremockResolver::class,
        WiremockUriResolver::class
    )
    @Disabled
    fun `if a few relases are present, getReleases should work`(@Wiremock server: WireMockServer, @WiremockUri uri: String) {
        val client = buildClient(uri)
        val path = "/repos/${client.user}/${client.repository}/releases"

        // replies two release in the first page
        server.stubFor(get(urlPathEqualTo(path))
            .inScenario("paging")
            .willReturn(aResponse()
                .withStatus(200)
                // link to the next page of releases
                .withHeader("Link", "<${client.apiUrl}${path}?page=2>; rel=\"next\"")
                .withBody("""[
                    {"id": 1},
                    {"id": 2}
                    ]""")
                )
            )
        // replies two more releases in the second page
        server.stubFor(get(urlPathEqualTo(path))
            .withQueryParam("page", equalTo("2"))
            .inScenario("paging")
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("""[
                    {"id": 3},
                    {"id": 4}
                    ]""")
                )
            )
        val releases = client.getReleases()
        assertEquals(releases.size, 4)
    }


    @Test
    @Disabled
    @ExtendWith(
        WiremockResolver::class,
        WiremockUriResolver::class
    ) 
    fun testGetReleases(@Wiremock server: WireMockServer, @WiremockUri uri: String) {
        val client = buildClient(uri)
        val url = "/repos/${client.user}/${client.repository}/releases"

        server.stubFor(get(urlEqualTo(url))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("""[
                    {"id": 1},
                    {"id": 2}
                    ]""")
                )
            )

        val releases = client.getReleases()
        println(releases)
    }

}
