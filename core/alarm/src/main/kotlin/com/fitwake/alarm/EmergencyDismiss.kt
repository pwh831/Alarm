package com.fitwake.alarm

/**
 * 긴급 해제 (PRD AC-07): 긴 문장을 정확히 따라 입력해야 한다.
 * 잠결에 대충 누르지 못하게 하되, 띄어쓰기 차이 정도는 봐준다.
 */
object EmergencyDismiss {
    fun matches(input: String, phrase: String): Boolean = normalize(input) == normalize(phrase)

    private fun normalize(s: String) = s.trim().replace(Regex("\\s+"), " ")
}
