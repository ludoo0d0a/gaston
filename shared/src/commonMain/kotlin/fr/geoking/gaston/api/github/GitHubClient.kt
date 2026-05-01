package fr.geoking.gaston.api.github

import fr.geoking.gaston.shared.network.NetworkException
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Minimal GitHub REST client for pull requests on a single repo (status, merge, close, issue comments).
 * Use a [classic PAT](https://github.com/settings/tokens) or fine-grained token with Contents + Pull requests (and Issues for comments).
 */
class GitHubClient(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.github.com"
) {
    private val json = Json { ignoreUnknownKeys = true }

    private fun requireToken(token: String): String {
        if (token.isBlank()) {
            throw NetworkException(null, "GitHub token is required. Add one in Settings → GitHub.")
        }
        return token
    }

    private fun HttpRequestBuilder.githubHeaders(token: String) {
        header("Authorization", "Bearer ${requireToken(token)}")
        header("Accept", "application/vnd.github+json")
        header("X-GitHub-Api-Version", "2022-11-28")
    }

    @Serializable
    data class GitHubPullRequestSummary(
        val number: Int = 0,
        val title: String = "",
        val state: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        @SerialName("mergeable_state") val mergeableState: String? = null
    )

    suspend fun listOpenPullRequests(token: String, owner: String, repo: String): List<GitHubPullRequestSummary> =
        listPullRequests(token, owner, repo, state = "open")

    suspend fun listPullRequests(
        token: String,
        owner: String,
        repo: String,
        state: String = "open"
    ): List<GitHubPullRequestSummary> {
        requireToken(token)
        val response = client.get("$baseUrl/repos/$owner/$repo/pulls") {
            githubHeaders(token)
            parameter("state", state)
            parameter("per_page", "30")
            parameter("sort", "updated")
            parameter("direction", "desc")
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "GitHub list PRs: $body")
        }
        return json.decodeFromString(ListSerializer(GitHubPullRequestSummary.serializer()), body)
    }

    @Serializable
    data class GitHubPullRequestDetail(
        val number: Int = 0,
        val title: String = "",
        val state: String = "",
        val body: String? = null,
        @SerialName("html_url") val htmlUrl: String = "",
        val draft: Boolean = false,
        val merged: Boolean = false,
        val mergeable: Boolean? = null,
        @SerialName("mergeable_state") val mergeableState: String? = null
    )

    suspend fun getPullRequest(token: String, owner: String, repo: String, number: Int): GitHubPullRequestDetail {
        requireToken(token)
        val response = client.get("$baseUrl/repos/$owner/$repo/pulls/$number") {
            githubHeaders(token)
        }
        val body = response.bodyAsText()
        if (response.status.value != 200) {
            throw NetworkException(response.status.value, "GitHub get PR: $body")
        }
        return json.decodeFromString(GitHubPullRequestDetail.serializer(), body)
    }

    @Serializable
    private data class MergeBody(val merge_method: String = "merge")

    suspend fun mergePullRequest(token: String, owner: String, repo: String, number: Int) {
        requireToken(token)
        val response = client.put("$baseUrl/repos/$owner/$repo/pulls/$number/merge") {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(MergeBody())
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "GitHub merge PR: ${response.bodyAsText()}")
        }
    }

    @Serializable
    private data class PatchPrBody(val state: String)

    suspend fun closePullRequest(token: String, owner: String, repo: String, number: Int) {
        requireToken(token)
        val response = client.patch("$baseUrl/repos/$owner/$repo/pulls/$number") {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(PatchPrBody(state = "closed"))
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "GitHub close PR: ${response.bodyAsText()}")
        }
    }

    @Serializable
    private data class IssueCommentBody(val body: String)

    /**
     * Posts an issue comment on the PR.
     */
    suspend fun addCommentOnPullRequest(token: String, owner: String, repo: String, number: Int, commentText: String) {
        requireToken(token)
        val response = client.post("$baseUrl/repos/$owner/$repo/issues/$number/comments") {
            githubHeaders(token)
            contentType(ContentType.Application.Json)
            setBody(IssueCommentBody(body = commentText.trim()))
        }
        if (response.status.value !in 200..299) {
            throw NetworkException(response.status.value, "GitHub PR comment: ${response.bodyAsText()}")
        }
    }
}

data class GitHubPrRef(val owner: String, val repo: String, val number: Int)

fun parseGitHubPullRequestUrl(url: String): GitHubPrRef? {
    val m = Regex("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)", RegexOption.IGNORE_CASE).find(url.trim()) ?: return null
    return GitHubPrRef(m.groupValues[1], m.groupValues[2], m.groupValues[3].toInt())
}
