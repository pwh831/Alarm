package com.fitwake.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.hasText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 앱을 켜고 주요 화면을 한 번씩 거쳐도 죽지 않는지 확인하는 스모크 테스트.
 * 카메라 미션 화면은 CameraX·ML Kit이 필요해 실기기에서 확인한다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29, 35], qualifiers = "ko")
class LaunchSmokeTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun click(text: String) {
        val node = compose.onNodeWithText(text)
        runCatching { node.performScrollTo() } // 스크롤 영역 밖의 버튼이면 스크롤 없이 누른다
        node.performClick()
        compose.waitForIdle()
    }

    @Test
    fun onboardingThenHomeEditStatsSettings() {
        compose.onNodeWithText("운동해야 꺼지는 알람").assertExists()
        repeat(3) { click("다음") }
        compose.onNodeWithText("알람이 확실히 울리도록 설정해 주세요").assertExists()
        click("나중에 할게요")
        compose.onNodeWithText("휴대폰 배터리 설정 확인").assertExists()
        click("다음")
        compose.onNodeWithText("한 번 해볼까요?").assertExists()
        click("건너뛰기")

        // 홈 → 새 알람 저장
        compose.onNodeWithText("+ 알람 추가").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("새 알람").assertExists()
        click("저장")
        // 저장한 알람이 홈 상단의 "다음 알람까지 남은 시간"에 반영된다
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("후에 울려요", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }

        // 기록
        compose.onNodeWithText("기록").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("연속 기상").assertExists()
        click("‹ 뒤로")

        // 설정
        compose.onNodeWithText("설정").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("새 알람 기본 미션").assertExists()
    }
}
