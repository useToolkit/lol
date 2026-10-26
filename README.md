# I photo — 갤럭시 전용 시네마틱 카메라

> 첨부된 6장의 호텔·야경 레퍼런스를 참고해 만든 안드로이드 카메라 앱. 브랜드명: **I photo**.

## v1.1.0
- 촬영 장면 테두리와 코너 가이드: 실제 촬영 영역이 명확하게 보이도록 분리된 미리보기
- 사진/영상 화면 비율 전환: 4:3(세로 3:4), 9:16, 1:1, 3:2
- CameraX PreviewView ViewPort에 맞춰 미리보기 크기와 사진/영상 크롭 영역 연동
- 보정 슬라이더 접기/펼치기, 이전 촬영 비율 기억
- 실기기별 지원 해상도 및 영상 크롭 동작은 차이가 있을 수 있음

## 설치하기

- **[APK 바로 다운로드](https://github.com/useToolkit/lol/raw/refs/heads/i-photo-apk/I-photo-v1.3.1.apk)** (CI 빌드 성공 후 활성화)
- [GitHub Actions 설치파일](https://github.com/useToolkit/lol/actions/workflows/android.yml?query=branch%3Ai-photo) → 최신 Build I photo APK → Artifacts → I-photo-APK
- 휴대폰 다운로드 후 APK 열기 → '이 출처의 앱 설치 허용' → 설치 → 카메라/마이크 권한 허용.

## 지원 기능

- **사진 및 영상에 동일한 실시간 필터 적용**: CameraX + Media3Effect (GPU SurfaceProcessor) + 17³ 3D LUT
- **Luxury Night**: 따뜻한 조명, 차가운 암부, 낮은 채도, 깊은 블랙 (레퍼런스 기반 기본 설정)
- **Soft Hotel / City Noir / Original**: 3가지 추가 프리셋
- 필터 강도(0~100%), 노출(-2~+2EV), 원본 비교(누르고 있기)
- 사진 JPEG / 영상 FHD MP4(H.264 코덱은 기기 선택) 갤러리 자동 저장
- 전후면 카메라, 1x/2x 줌, 격자, 플래시, 터치 초점 및 핀치 줌
- 오프라인 작동 / 사진·영상을 외부 서버로 전송하지 않음

## 빌드

Android Studio에서 열기 또는 GitHub Actions에서 `Build I photo APK` 실행.

```bash
gradle :app:assembleDebug
```

설치 패키지명 `com.usetoolkit.iphoto`. 최소 Android 10(API 29), 갤럭시 Android 17 지원 목표.

## 구현·테스트 주의사항

- **컬러 매칭의 한계**: 6개 결과 이미지에는 갤럭시/아이폰의 동일 장면 원본 및 컬러차트가 없습니다. 현재 LUT는 레퍼런스의 미적 특성을 재현한 첫 버전입니다. 완전한 픽셀 수준 복제나 아이폰 ISP/야간 멀티프레임 알고리즘을 보장하지 않습니다.
- 영상과 사진이 동일한 Media3Effect GPU 효과 체인을 사용해 기록됩니다. HDR 영상을 강제하지 않고 SDR 경로로 동작합니다.
- 실제 광학식 초광각 0.6배, RAW, 4K 60fps, 삼성 자체 야간 다중프레임 알고리즘은 이 버전에서 제공하지 않습니다.
- Galaxy 기종별 CameraX UseCase 그룹 지원 여부에 따라 기능과 최대 화질이 달라질 수 있으며, **실기기 촬영 테스트가 필요합니다**.
- 이 GitHub 저장소는 과거 다른 앱을 배포했던 공용 공간이므로 **`i-photo` 브랜치**만 수정했으며 기존 기본 브랜치와 Oily 브랜치는 변경하지 않았습니다.
- GitHub Actions에서 만드는 APK는 사용자 직접 설치용 `debug` 서명입니다. 동일 패키지의 장기 업데이트에는 서명키를 일관되게 관리해야 합니다.

## 오픈소스 라이선스

AndroidX CameraX, Media3, Activity, Core 등의 라이브러리 사용 (Apache-2.0). 앱 코드 자체는 사용자의 개인 프로젝트입니다.

## v1.3.1 — system bar layout correction
- Handle Android 15+ edge-to-edge system insets explicitly with WindowInsetsCompat.
- Top app branding, AF lock, flash, and settings start BELOW the status bar/camera hole cutout.
- Mode tabs and shutter actions stay ABOVE the gesture/3-button navigation bar.
- Adds small touch-safe spacing at top and bottom while retaining large preview.
- No LUT, focus, ratio, or recording behavior changes in this patch.

## v1.3 — AF lock
- Tap on the camera preview to focus on a chosen area.
- Touch and hold (520ms) to lock focus at the chosen point (CameraX FocusMeteringAction.disableAutoCancel).
- AF/AF LOCK button in the top toolbar toggles focus lock at the last tapped or center point.
- Tap again or switch cameras to unlock focus. Pinch-to-zoom works alongside gestures.
- Actual focus-lock behavior depends on the camera hardware's supported AF modes.

## v1.2.0: 몰입형 촬영 화면
- 삼성 기본 카메라 참고: 미리보기를 최대화하고 셔터/모드/줌을 카메라 화면 위에 오버레이
- 비율은 상단 오른쪽 ⚙ 설정에서 변경 (4:3, 9:16, 1:1, 3:2), 기본값 9:16
- 카메라 화면에서 비율 버튼 제거, 필터 강도/노출/격자도 설정 패널로 이동
- 기존 4가지 시네마틱 룩 유지, 실제 사진/영상 미리보기 비율은 CameraX ViewPort로 연동
- 기존 설치와 업데이트 서명이 달라 설치 충돌이 날 수 있음. 충돌 시 기존 앱 제거 후 재설치
