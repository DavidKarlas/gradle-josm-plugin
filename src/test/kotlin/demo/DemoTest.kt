package org.openstreetmap.josm.gradle.plugin.demo

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.openstreetmap.josm.gradle.plugin.testutils.GradleProjectUtil
import java.io.File

class DemoTest {

  @Suppress("unused")
  enum class GradleVersion(val expectingSuccess: Boolean) {
    GRADLE_5_6_4(false),
    GRADLE_6_0(true),
    GRADLE_6_0_1(true),
    GRADLE_6_1_1(true),
    GRADLE_6_2_2(true),
    GRADLE_6_3(true),
    GRADLE_6_4(true);

    val version = name.substring(name.indexOf('_') + 1).replace('_', '.')
  }

  @ParameterizedTest
  @EnumSource(GradleVersion::class)
  fun testDemo(gradleVersion: GradleVersion, testInfo: TestInfo) {
    println("Building demo project with Gradle ${gradleVersion.version}.")
    println("Expecting to ${if (gradleVersion.expectingSuccess) "succeed" else "fail"}!")

    val tmpDir = GradleProjectUtil.createTempSubDir(testInfo, true)
    println("build dir: ${tmpDir.absolutePath}")

    File(DemoTest::class.java.getResource("/demo").toURI()).copyRecursively(tmpDir, overwrite = false)

    GradleRunner.create().withGradleVersion(gradleVersion.version)
      .withProjectDir(tmpDir)
      .withArguments(
        "--stacktrace",
        "build",
        "compileJava_minJosm",
        "compileJava_testedJosm",
        "compileJava_latestJosm",
        "generatePot",
        "localDist",
        "shortenPoFiles",
        "listJosmVersions"
      )
      .forwardOutput()
      .withPluginClasspath()
      .apply {
        if (gradleVersion.expectingSuccess) build() else buildAndFail()
      }
  }
}
