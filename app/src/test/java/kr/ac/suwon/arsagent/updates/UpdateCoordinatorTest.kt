package kr.ac.suwon.arsagent.updates

import org.junit.Assert.*
import org.junit.Test

class UpdateCoordinatorTest {
    private class Gateway(override val enabled: Boolean = true) : UpdateGateway {
        override var signedIn = false
        val checks = mutableListOf<(Result<String?>) -> Unit>()
        val logins = mutableListOf<(Result<Unit>) -> Unit>()
        var installs = 0
        var installerResult: Result<Unit> = Result.success(Unit)
        override fun check(done: (Result<String?>) -> Unit) { checks += done }
        override fun signIn(done: (Result<Unit>) -> Unit) { logins += done }
        override fun openInstaller(): Result<Unit> { installs++; return installerResult }
    }

    @Test fun unconfiguredBuildNeverOffersLoginOrCallsFirebase() {
        val gateway = Gateway(false)
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true); owner.signIn(); owner.install()
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
        assertTrue(gateway.logins.isEmpty()); assertTrue(gateway.checks.isEmpty())
    }

    @Test fun signInNeedsExplicitConsentAndDeclineDoesNotNag() {
        val gateway = Gateway()
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true)
        assertEquals(UpdateStage.Consent, owner.ui.value.stage)
        assertTrue(gateway.logins.isEmpty())
        owner.dismiss(); owner.setEligible(false); owner.setEligible(true)
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
        assertTrue(gateway.logins.isEmpty())
    }

    @Test fun signedInTesterIsCheckedOnceAndInstallationIsExplicit() {
        val gateway = Gateway().apply { signedIn = true }
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true); owner.setEligible(true)
        assertEquals(1, gateway.checks.size)
        gateway.checks.single()(Result.success("0.1.0 (12)"))
        assertEquals("0.1.0 (12)", owner.ui.value.version)
        assertEquals(UpdateStage.Available, owner.ui.value.stage)
        assertEquals(0, gateway.installs)
        owner.install(); owner.install()
        assertEquals(1, gateway.installs)
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
    }

    @Test fun lateUpdateCheckAfterEnteringCallCannotShowPrompt() {
        val gateway = Gateway().apply { signedIn = true }
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true)
        owner.setEligible(false)
        gateway.checks.single()(Result.success("new"))
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
        owner.install()
        assertEquals(0, gateway.installs)
    }

    @Test fun olderCheckCannotReplaceNewerCheckAfterReturningHome() {
        val gateway = Gateway().apply { signedIn = true }
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true); owner.setEligible(false); owner.setEligible(true)
        gateway.checks[1](Result.success(null))
        gateway.checks[0](Result.success("stale"))
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
    }

    @Test fun signInCompletingInBackgroundWaitsForHomeBeforeChecking() {
        val gateway = Gateway()
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true); owner.signIn(); owner.signIn()
        assertEquals(1, gateway.logins.size)
        owner.setEligible(false)
        gateway.signedIn = true
        gateway.logins.single()(Result.success(Unit))
        assertTrue(gateway.checks.isEmpty())
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
        owner.setEligible(true)
        assertEquals(1, gateway.checks.size)
    }

    @Test fun offlineErrorCanBeDismissedAndDoesNotLoop() {
        val gateway = Gateway().apply { signedIn = true }
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true)
        gateway.checks.single()(Result.failure(IllegalStateException("private network detail")))
        assertEquals(UpdateStage.Failed, owner.ui.value.stage)
        owner.dismiss(); owner.setEligible(false); owner.setEligible(true)
        assertEquals(1, gateway.checks.size)
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
    }

    @Test fun successfulChecksAreThrottledAcrossQuickHomeVisits() {
        var now = 0L
        val gateway = Gateway().apply { signedIn = true }
        val owner = UpdateCoordinator(gateway) { now }
        owner.setEligible(true); gateway.checks.single()(Result.success(null))
        owner.setEligible(false); owner.setEligible(true)
        assertEquals(1, gateway.checks.size)
        now = 600_001
        owner.setEligible(false); owner.setEligible(true)
        assertEquals(2, gateway.checks.size)
    }

    @Test fun closedOwnerIgnoresLateCallbacks() {
        val gateway = Gateway().apply { signedIn = true }
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true); owner.close()
        gateway.checks.single()(Result.success("late"))
        owner.setEligible(true)
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
        assertEquals(1, gateway.checks.size)
    }

    @Test fun installationFailureDoesNotBlockAppAfterDismissal() {
        val gateway = Gateway().apply { signedIn = true; installerResult = Result.failure(IllegalStateException()) }
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true); gateway.checks.single()(Result.success("new"))
        owner.install()
        assertEquals(UpdateStage.Failed, owner.ui.value.stage)
        owner.dismiss()
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
    }

    @Test fun installerHandoffCannotLeaveAnUncancelableDownloadBlockingTheApp() {
        val gateway = Gateway().apply { signedIn = true }
        val owner = UpdateCoordinator(gateway)
        owner.setEligible(true); gateway.checks.single()(Result.success("new"))
        owner.install(); owner.setEligible(false); owner.setEligible(true)
        assertEquals(UpdateStage.Hidden, owner.ui.value.stage)
        assertEquals(1, gateway.installs)
        owner.install()
        assertEquals(1, gateway.installs)
    }
}
