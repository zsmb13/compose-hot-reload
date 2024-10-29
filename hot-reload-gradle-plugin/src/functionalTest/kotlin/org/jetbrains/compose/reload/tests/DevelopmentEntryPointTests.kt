package org.jetbrains.compose.reload.tests

import org.jetbrains.compose.reload.utils.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.writeText

class DevelopmentEntryPointTests {

    @HotReloadTest
    @DefaultSettingsGradleKts
    @DefaultBuildGradleKts
    @TestOnlyLatestVersions
    fun `test - simple jvm project`(fixture: HotReloadTestFixture) = fixture.runTest {
        val mainKt = fixture.projectDir
            .resolve("src")
            .resolve(fixture.projectMode.fold("jvmMain", "main"))
            .resolve("kotlin/Main.kt")
            .createParentDirectories()

        val devKt = fixture.projectDir
            .resolve("src")
            .resolve(fixture.projectMode.fold("jvmDev", "dev"))
            .resolve("kotlin/Dev.kt")
            .createParentDirectories()

        mainKt.writeText(
            """
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.unit.sp
            
            @Composable
            fun Widget(text: String) {
                Text("Before: " + text, fontSize = 48.sp)
            }
        """.trimIndent()
        )

        devKt.writeText(
            """
            import androidx.compose.runtime.Composable
            import org.jetbrains.compose.reload.jvm.*

            @Composable
            @DevelopmentEntryPoint
            fun DevWidget() {
                Widget("Foo")
            }
        """.trimIndent()
        )

        fixture.launchDevApplicationAndWait(className = "DevKt", funName = "DevWidget")
        fixture.checkScreenshot("0-initial")

        devKt.replaceText("Foo", "Bar")
        fixture.awaitReload()
        fixture.checkScreenshot("1-change-in-devKt")

        mainKt.replaceText("Before", "After")
        fixture.awaitReload()
        fixture.checkScreenshot("2-change-in-mainKt")
    }
}