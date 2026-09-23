package com.fitwake.app.onboarding

import android.os.Build
import androidx.annotation.StringRes
import com.fitwake.app.R

/**
 * 제조사 배터리 관리가 백그라운드 앱을 강제로 멈춰 알람이 안 울리는 경우가 많다 (PRD 7.1).
 * 기기별로 메뉴 위치가 달라서 설정 화면을 직접 여는 대신 경로를 글로 안내한다.
 */
enum class Manufacturer(@StringRes val guide: Int, private vararg val names: String) {
    SAMSUNG(R.string.battery_guide_samsung, "samsung"),
    XIAOMI(R.string.battery_guide_xiaomi, "xiaomi", "redmi", "poco"),
    HUAWEI(R.string.battery_guide_huawei, "huawei", "honor"),
    OPPO(R.string.battery_guide_oppo, "oppo", "realme", "oneplus"),
    VIVO(R.string.battery_guide_vivo, "vivo"),
    OTHER(R.string.battery_guide_other),
    ;

    companion object {
        fun current(): Manufacturer {
            val name = "${Build.MANUFACTURER} ${Build.BRAND}".lowercase()
            return entries.firstOrNull { m -> m.names.any { it in name } } ?: OTHER
        }
    }
}

const val DONT_KILL_MY_APP_URL = "https://dontkillmyapp.com"
