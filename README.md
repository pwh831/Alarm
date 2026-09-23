# FitWake

푸시업이나 스쿼트를 해야 꺼지는 알람 앱. 기획 문서는 [docs/PRD.md](docs/PRD.md)에 있다.

## 현재 단계: M0 기술 검증

카메라로 푸시업·스쿼트 횟수를 세는 기능을 먼저 검증한다. 알람 기능(M1)은 아직 없다.

```
core/pose/   플랫폼 독립 반복 카운터 (순수 Kotlin, JVM 테스트)
app/         Android 앱: CameraX + ML Kit 포즈 인식 + 카운트 화면 (Jetpack Compose)
```

### core/pose
- `RepCounter`: 관절 각도 기반 상태 머신 (WAITING → TOP → BOTTOM → TOP = 1회)
- `SquatCounter`: 무릎 각도 + 엉덩이 하강 검증
- `PushupCounter`: 팔꿈치 각도 + 몸 수평/일직선 검증, 쉬움 난이도에서만 무릎 푸시업 허용
- 부정행위 방지: 관절 신뢰도 검사, 0.4초 미만 반복 무시, 1초 이상 몸이 안 보이면 진행 중인 반복 초기화

```bash
./gradlew :core:pose:test
```

### app
Android Studio에서 열어 실행한다 (minSdk 29, Android 10 이상).
1. 운동, 난이도, 목표 횟수를 고른다
2. 폰을 세워두고 전신이 보이게 선다 (푸시업은 측면 촬영)
3. 목표 횟수를 채우면 완료 화면이 나온다

미션 중에는 화면 밝기를 최대로 올려서 어두운 방에서 조명 역할을 하게 한다.
