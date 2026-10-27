package kr.ac.suwon.arsagent.updates

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.appdistribution.FirebaseAppDistribution

private class FirebaseUpdateGateway(private val app: Application, private val sdk: FirebaseAppDistribution,
    private val appId: String) : UpdateGateway {
    override val enabled = true
    override val signedIn get() = sdk.isTesterSignedIn
    override fun signIn(done: (Result<Unit>) -> Unit) {
        sdk.signInTester().addOnSuccessListener { done(Result.success(Unit)) }
            .addOnFailureListener { done(Result.failure(it)) }
    }
    override fun check(done: (Result<String?>) -> Unit) {
        sdk.checkForNewRelease().addOnSuccessListener { release ->
            done(Result.success(release?.let { "${it.displayVersion} (${it.versionCode})" }))
        }.addOnFailureListener { done(Result.failure(it)) }
    }
    override fun openInstaller(): Result<Unit> = runCatching {
        val url = Uri.parse("https://appdistribution.firebase.google.com/testerapps/${Uri.encode(appId)}")
        app.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private object DisabledUpdates : UpdateGateway {
    override val enabled = false
    override val signedIn = false
    override fun check(done: (Result<String?>) -> Unit) = Unit
    override fun signIn(done: (Result<Unit>) -> Unit) = Unit
    override fun openInstaller(): Result<Unit> = Result.failure(IllegalStateException("Updates not configured"))
}

private var preparedGateway: UpdateGateway? = null

/** Must run before MainActivity's first resume so the SDK sees its lifecycle callbacks. */
fun prepareTeamUpdates(app: Application) {
    if (preparedGateway != null) return
    preparedGateway = runCatching {
        val config = app.assets.open("firebase/firebase-team.json").bufferedReader().use {
            TeamFirebaseConfig.parse(it.readText(), app.packageName)
        } ?: return@runCatching DisabledUpdates
        val options = FirebaseOptions.Builder().setApplicationId(config.application_id)
            .setApiKey(config.api_key).setProjectId(config.project_id).setGcmSenderId(config.sender_id).build()
        if (FirebaseApp.getApps(app).isEmpty()) FirebaseApp.initializeApp(app, options)
        // Never mix another Firebase project's cached singleton with this team's configuration.
        if (FirebaseApp.getInstance().options.applicationId != config.application_id) DisabledUpdates
        else FirebaseUpdateGateway(app, FirebaseAppDistribution.getInstance(), config.application_id)
    }.getOrDefault(DisabledUpdates)
}

class TeamUpdateViewModel(app: Application) : AndroidViewModel(app) {
    val owner = UpdateCoordinator(preparedGateway ?: DisabledUpdates)
    override fun onCleared() { owner.close() }
}

/** Only MainActivity supplies this slot; gallery/server-test/demo keep their existing behavior. */
@Composable
fun TeamUpdates(eligible: Boolean) {
    val model: TeamUpdateViewModel = viewModel()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, eligible) {
        val observer = LifecycleEventObserver { _, _ ->
            model.owner.setEligible(eligible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        }
        lifecycle.addObserver(observer)
        model.owner.setEligible(eligible && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
        onDispose { lifecycle.removeObserver(observer); model.owner.setEligible(false) }
    }
    val ui by model.owner.ui.collectAsStateWithLifecycle()
    if (eligible) UpdatePrompt(ui, model.owner::signIn, model.owner::install, model.owner::dismiss)
}
