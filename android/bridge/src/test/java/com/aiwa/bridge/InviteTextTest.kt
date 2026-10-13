package com.aiwa.bridge

import org.junit.Assert.assertTrue
import org.junit.Test

class InviteTextTest {
    @Test fun anInvitationNamesTheAppOrTheStore() {
        assertTrue(inviteText("meteo", "https://x/#app=meteo").contains("« meteo »"))
        assertTrue(inviteText("meteo", "https://x/#app=meteo").contains("#app=meteo"))
        assertTrue(inviteText(null, null).contains(STORE_WEB_URL))
        assertTrue(inviteText(null, null).contains(APP_RELEASE_PAGE))
    }
}
