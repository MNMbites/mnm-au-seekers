package com.mnm.auseekers.data

import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<AppVersion> {
    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, AppVersion::major, AppVersion::minor, AppVersion::patch)

    override fun toString(): String = "$major.$minor.$patch"

    companion object {
        private val VERSION_PATTERN = Regex(
            "^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)$",
        )

        fun parse(value: String): AppVersion? {
            val match = VERSION_PATTERN.matchEntire(value.trim()) ?: return null
            val numbers = match.groupValues.drop(1).map { it.toIntOrNull() ?: return null }
            return AppVersion(numbers[0], numbers[1], numbers[2])
        }
    }
}

data class AppRelease(
    val tagName: String,
    val version: AppVersion,
    val releasePageUrl: String,
    val apkDownloadUrl: String,
)

sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data object NoPublishedRelease : AppUpdateState
    data class Current(val latestVersion: AppVersion) : AppUpdateState
    data class Available(val release: AppRelease) : AppUpdateState
    data class Error(val detail: String) : AppUpdateState
}

class AppUpdateEvaluator {
    fun evaluate(currentVersion: String, latestRelease: AppRelease?): AppUpdateState {
        val current = AppVersion.parse(currentVersion)
            ?: throw IllegalArgumentException("The installed app version is invalid.")
        if (latestRelease == null) return AppUpdateState.NoPublishedRelease
        return if (latestRelease.version > current) {
            AppUpdateState.Available(latestRelease)
        } else {
            AppUpdateState.Current(latestRelease.version)
        }
    }
}

class GitHubReleaseParser {
    fun parse(json: String): AppRelease = try {
        val root = JsonParser.parseString(json).asObject("release")
        val tagName = root.requiredString("tag_name")
        val version = AppVersion.parse(tagName)
            ?: throw IllegalArgumentException("Release tag must be vMAJOR.MINOR.PATCH")
        val releasePageUrl = root.requiredString("html_url")
        GitHubReleaseUrlPolicy.requireTrustedReleasePage(releasePageUrl, tagName)

        val expectedAssetName = "MNM-AU-Seekers-$version.apk"
        val asset = root.requiredArray("assets")
            .map { it.asObject("release asset") }
            .firstOrNull { it.requiredString("name") == expectedAssetName }
            ?: throw IllegalArgumentException("Latest release has no $expectedAssetName asset")
        val apkDownloadUrl = asset.requiredString("browser_download_url")
        GitHubReleaseUrlPolicy.requireTrustedApkDownload(apkDownloadUrl, tagName)

        AppRelease(
            tagName = tagName,
            version = version,
            releasePageUrl = releasePageUrl,
            apkDownloadUrl = apkDownloadUrl,
        )
    } catch (error: JsonParseException) {
        throw IOException("GitHub returned malformed release JSON.", error)
    } catch (error: IllegalArgumentException) {
        throw IOException("GitHub returned an invalid app release.", error)
    } catch (error: IllegalStateException) {
        throw IOException("GitHub returned an invalid app release.", error)
    }
}

class GitHubReleaseUpdateProvider(
    private val parser: GitHubReleaseParser = GitHubReleaseParser(),
) {
    suspend fun latest(): AppRelease? = withContext(Dispatchers.IO) {
        GitHubLatestReleaseTransport.fetch()?.let(parser::parse)
    }
}

object GitHubReleaseUrlPolicy {
    private const val RELEASE_PATH_PREFIX = "/MNMbites/mnm-au-seekers/releases/"

    fun requireTrustedReleasePage(url: String, tagName: String) {
        val uri = trustedGitHubUri(url)
        require(uri.rawPath == "${RELEASE_PATH_PREFIX}tag/$tagName") {
            "Unexpected GitHub release page"
        }
    }

    fun requireTrustedApkDownload(url: String, tagName: String) {
        val uri = trustedGitHubUri(url)
        val version = AppVersion.parse(tagName)
            ?: throw IllegalArgumentException("Release tag is invalid")
        val expectedPath =
            "${RELEASE_PATH_PREFIX}download/$tagName/MNM-AU-Seekers-$version.apk"
        require(uri.rawPath == expectedPath) {
            "Unexpected GitHub release download"
        }
    }

    fun isTrustedApkDownload(url: String, tagName: String): Boolean = runCatching {
        requireTrustedApkDownload(url, tagName)
    }.isSuccess

    private fun trustedGitHubUri(url: String): URI = runCatching { URI(url) }
        .getOrElse { throw IllegalArgumentException("Release URL is invalid", it) }
        .also { uri ->
            require("https".equals(uri.scheme, ignoreCase = true)) {
                "Release URL must use HTTPS"
            }
            require("github.com".equals(uri.host, ignoreCase = true) && uri.port == -1) {
                "Release URL must use github.com"
            }
            require(uri.userInfo == null && uri.query == null && uri.fragment == null) {
                "Release URL must not include credentials, a query, or a fragment"
            }
        }
}

private object GitHubLatestReleaseTransport {
    private val latestReleaseUri = URI(
        "https://api.github.com/repos/MNMbites/mnm-au-seekers/releases/latest",
    )

    fun fetch(): String? {
        val connection = latestReleaseUri.toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "MNM-AU-Seekers-Android")
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = false

            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> connection.inputStream
                    .bufferedReader(StandardCharsets.UTF_8)
                    .use { it.readText() }
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw IOException(
                    "GitHub release service returned HTTP ${connection.responseCode}.",
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}

private fun com.google.gson.JsonElement.asObject(label: String): JsonObject =
    takeIf { isJsonObject }?.asJsonObject
        ?: throw IllegalArgumentException("$label must be an object")

private fun JsonObject.requiredString(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString
        ?.takeIf { it.isNotBlank() }
        ?: throw IllegalArgumentException("Missing or invalid $name")

private fun JsonObject.requiredArray(name: String): com.google.gson.JsonArray =
    get(name)?.takeIf { it.isJsonArray }?.asJsonArray
        ?: throw IllegalArgumentException("Missing or invalid $name")
