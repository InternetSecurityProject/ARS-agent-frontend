package kr.ac.suwon.arsagent

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.appdistribution.FirebaseAppDistribution
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Narrow SDK characterization: no login/check/download or network is requested. */
class TeamUpdateStartupTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun configuredColdStartTracksTheFirstResumedActivity() {
        assumeTrue("Run separately with a synthetic public debug config", runCatching {
            compose.activity.assets.open("firebase/firebase-team.json").close()
        }.isSuccess)
        val proxy = FirebaseAppDistribution.getInstance()
        val sdk = proxy.javaClass.getDeclaredField("delegate").apply { isAccessible = true }.get(proxy)!!
        val field = sdk.javaClass.getDeclaredField("lifecycleNotifier").apply { isAccessible = true }
        val notifier = field.get(sdk)
        val method = notifier.javaClass.getDeclaredMethod("getForegroundActivity").apply { isAccessible = true }
        lateinit var foreground: Task<*>
        compose.runOnIdle { foreground = method.invoke(notifier) as Task<*> }
        assertSame(compose.activity, Tasks.await(foreground, 3, TimeUnit.SECONDS))
    }
}
