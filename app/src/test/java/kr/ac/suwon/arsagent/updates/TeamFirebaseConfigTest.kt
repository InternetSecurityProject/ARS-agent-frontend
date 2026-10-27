package kr.ac.suwon.arsagent.updates

import org.junit.Assert.*
import org.junit.Test

class TeamFirebaseConfigTest {
    private val valid = """{"application_id":"1:123456:android:abcdef","api_key":"client-key","project_id":"team-project","sender_id":"123456","package_name":"kr.ac.suwon.arsagent.demo"}"""
    @Test fun validPublicConfigEnablesOnlyItsMatchingDebugPackage() {
        assertNotNull(TeamFirebaseConfig.parse(valid, "kr.ac.suwon.arsagent.demo"))
        assertNull(TeamFirebaseConfig.parse(valid, "kr.ac.suwon.arsagent"))
    }
    @Test fun missingMalformedOrAdminCredentialsCannotEnableUpdates() {
        listOf("", "{", "{}", """{"type":"service_account","private_key":"never-package"}""")
            .forEach { assertNull(TeamFirebaseConfig.parse(it, "kr.ac.suwon.arsagent.demo")) }
    }
    @Test fun mismatchedProjectNumberAndBlankFieldsAreRejected() {
        assertNull(TeamFirebaseConfig.parse(valid.replace("\"sender_id\":\"123456\"", "\"sender_id\":\"999\""), "kr.ac.suwon.arsagent.demo"))
        assertNull(TeamFirebaseConfig.parse(valid.replace("client-key", ""), "kr.ac.suwon.arsagent.demo"))
    }
}
