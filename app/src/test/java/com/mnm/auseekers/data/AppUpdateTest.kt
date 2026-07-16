package com.mnm.auseekers.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateTest {
    private val parser = GitHubReleaseParser()
    private val evaluator = AppUpdateEvaluator()

    @Test
    fun semanticVersionsCompareNumerically() {
        assertTrue(
            requireNotNull(AppVersion.parse("v0.15.0")) >
                requireNotNull(AppVersion.parse("0.14.9")),
        )
        assertTrue(
            requireNotNull(AppVersion.parse("1.0.0")) >
                requireNotNull(AppVersion.parse("0.99.99")),
        )
        assertEquals("2.3.4", requireNotNull(AppVersion.parse("v2.3.4")).toString())
        assertNull(AppVersion.parse("v2.3"))
        assertNull(AppVersion.parse("02.3.4"))
    }

    @Test
    fun parsesExpectedVersionedApkFromOfficialRelease() {
        val release = parser.parse(releaseJson())

        assertEquals(AppVersion(0, 15, 0), release.version)
        assertEquals("v0.15.0", release.tagName)
        assertTrue(release.apkDownloadUrl.endsWith("MNM-AU-Seekers-0.15.0.apk"))
    }

    @Test
    fun newerReleaseIsAvailableAndSameVersionIsCurrent() {
        val release = parser.parse(releaseJson())

        assertTrue(evaluator.evaluate("0.14.0", release) is AppUpdateState.Available)
        assertTrue(evaluator.evaluate("0.15.0", release) is AppUpdateState.Current)
        assertTrue(evaluator.evaluate("0.16.0", release) is AppUpdateState.Current)
        assertTrue(evaluator.evaluate("0.15.0", null) is AppUpdateState.NoPublishedRelease)
    }

    @Test
    fun rejectsLookalikeHostAndUnexpectedAssetName() {
        val lookalike = releaseJson().replace("https://github.com/", "https://github.com.example/")
        val wrongAsset = releaseJson().replace(
            "MNM-AU-Seekers-0.15.0.apk",
            "unrelated.apk",
        )

        assertTrue(runCatching { parser.parse(lookalike) }.isFailure)
        assertTrue(runCatching { parser.parse(wrongAsset) }.isFailure)
        assertFalse(
            GitHubReleaseUrlPolicy.isTrustedApkDownload(
                "https://example.com/MNM-AU-Seekers-0.15.0.apk",
                "v0.15.0",
            ),
        )
        assertFalse(
            GitHubReleaseUrlPolicy.isTrustedApkDownload(
                "https://github.com/MNMbites/mnm-au-seekers/releases/download/" +
                    "v0.15.0/MNM-AU-Seekers-0.15.0.apk?source=other",
                "v0.15.0",
            ),
        )
    }

    private fun releaseJson() = """
        {
          "tag_name": "v0.15.0",
          "html_url": "https://github.com/MNMbites/mnm-au-seekers/releases/tag/v0.15.0",
          "assets": [
            {
              "name": "MNM-AU-Seekers-0.15.0.apk",
              "browser_download_url": "https://github.com/MNMbites/mnm-au-seekers/releases/download/v0.15.0/MNM-AU-Seekers-0.15.0.apk"
            }
          ]
        }
    """.trimIndent()
}
