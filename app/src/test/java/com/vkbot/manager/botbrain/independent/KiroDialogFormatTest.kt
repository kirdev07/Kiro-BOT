package com.vkbot.manager.botbrain.independent

import org.junit.Assert.assertEquals
import org.junit.Test

class KiroDialogFormatTest {
    @Test fun roundTripWithEscapedCharacters() {
        val rules = listOf(KiroFollowUpRules.Rule("next", "hello", "Как\tдела?", "Хорошо!\nСпасибо"))
        assertEquals(rules, KiroDialogFormat.parse(KiroDialogFormat.export(rules)))
    }

    @Test fun emptyRulesRoundTrip() {
        assertEquals(emptyList<KiroFollowUpRules.Rule>(), KiroDialogFormat.parse(KiroDialogFormat.export(emptyList())))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsOldFormat() {
        KiroDialogFormat.parse("hello|world")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateRuleIds() {
        KiroDialogFormat.parse("# KIRO-DIALOGS/1\nx\ta\tb\tc\nx\ta\td\te")
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBrokenEscape() {
        KiroDialogFormat.parse("# KIRO-DIALOGS/1\nx\ta\tb\tbroken\\q")
    }
}
