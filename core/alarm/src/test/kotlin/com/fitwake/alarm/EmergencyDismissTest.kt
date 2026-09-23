package com.fitwake.alarm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmergencyDismissTest {
    private val phrase = "나는 지금 완전히 깨어 있고 운동 대신 알람을 끄기로 선택합니다"

    @Test
    fun exactMatch() = assertTrue(EmergencyDismiss.matches(phrase, phrase))

    @Test
    fun toleratesWhitespace() = assertTrue(EmergencyDismiss.matches("  나는 지금  완전히 깨어 있고 운동 대신 알람을 끄기로 선택합니다 ", phrase))

    @Test
    fun rejectsTypos() = assertFalse(EmergencyDismiss.matches("나는 지금 완전히 깨어 있고 운동 대신 알람을 끄기로 선택", phrase))
}
