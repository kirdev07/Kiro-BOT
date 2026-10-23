package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Test

class KiroLegacyEscapesTest {
    @Test fun matchesEscapedPipeInQuestion() {
        val rows = KiroAnswerArchive.importLegacy("один<pipe>два|верно")
        assertEquals("верно", KiroArchiveConversation(rows).respond("один|два"))
    }

    @Test fun decodesReplyText() {
        val rows = KiroAnswerArchive.importLegacy("привет|первая<br>вторая<pipe>третья")
        assertEquals("первая\nвторая|третья", KiroArchiveConversation(rows).respond("привет"))
    }

    @Test fun archiveRoundTripRetainsLegacyEscapes() {
        val rows = KiroAnswerArchive.importLegacy("привет|один<pipe>два<br>три")
        val restored = KiroAnswerArchive.parse(KiroAnswerArchive.export(rows))
        assertEquals(rows, restored)
    }
}
