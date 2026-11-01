# I photo — 갤럭시 전용 시네마틱 카메라

## v2.2.1 — 아이폰형 직접 드래그 줌 전면 수정

**핵심 변경:** 이전에는 숫자 버튼별 터치가 각각 처리돼 손가락을 움직이기 시작하면 드래그가 취소되기도 했습니다. 이제 확대·축소는 숫자를 누르는 기능이 아니라 한 손가락의 드래그가 주 조작입니다.

- 하단 줌 선택 영역 전체를 `ZoomControlView` 하나의 터치 영역으로 구현했습니다. 좌우로 약 7dp 이상 움직이기 시작하면 길게 누르기를 기다리지 않고 즉시 연속 줌을 시작합니다.
- 약 290ms 길게 눌러도 원호(반원형) 줌 휠이 나타납니다. **누른 손가락을 떼지 않고 좌우로 계속 움직이면** 실제 카메라 배율이 이에 맞춰 실시간으로 변합니다.
- 손가락을 줌 휠 위에 다시 놓고 좌우로 드래그해도 정밀한 배율 조절이 됩니다. 중앙 눈금은 고정되어 있고 숫자 눈금이 줌값에 맞춰 이동합니다.
- 다이얼을 `FrameLayout` 오버레이로 올려 기존과 달리 조작 중 하단 UI 위치가 바뀌지 않습니다.
- 짧은 탭은 빠른 배율 전환으로 유지하되, 선택한 현재 수치가 화면의 금색 원 안에 실시간 표시됩니다. `ZoomState` 피드백은 제스처 동안 비동기 지연값으로 UI를 되돌리지 않도록 처리합니다.
- 터치 이동 중 줌 요청은 약 60Hz로 병합해 카메라 과부하를 피하고, 손을 뗄 때 마지막 배율을 강제로 전달합니다.
- 좌우 드래그 활성화·역방향·한계값 동작에 대한 `ZoomMathTest` 단위 테스트 추가.
- 촬영 화면 핀치 줌과 하드웨어 지원 배율 조건, 사진/영상 기능 및 손떨림 보정 설정은 그대로 유지합니다.
- Apple 카메라와 동일 소스/비공개 API를 복제한 것이 아니라 공개된 **줌 조작 동작 방식**을 Android CameraX에 구현했습니다.
- 0.5× 등 초광각은 갤럭시가 현재 카메라 세션에 제공하는 ZoomState 범위 밖이면 보이지 않습니다.
- APK 빌드는 GitHub Actions에서 검증되며 실기기 촬영·삼성 보안 경고 해소는 별도 확인이 필요합니다.

[다운로드: I photo v2.2.1 Release](https://raw.githubusercontent.com/useToolkit/lol/i-photo-apk/I-photo-v2.2.1-release.apk)

## v2.2.0 — iPhone-like zoom and hardware-assisted stabilization

### 줌 인터랙션
- 카메라 화면 하단에서 **동적 배율 버튼**: 기기의 CameraX ZoomState 범위 내에서만 0.5× / 1× / 2× / 3× / 5× / 8× 등을 표시합니다. **지원하지 않는 배율을 광학 렌즈로 가짜 표시하지 않습니다.**
- 탭하면 210ms 디셀러레이션 애니메이션으로 부드럽게 확대/축소합니다.
- 줌 버튼을 약 360ms 길게 누르면 눈금형 가로 줌 다이얼이 열리며, 그대로 좌우로 드래그해 연속 배율을 설정할 수 있습니다. 길게 누를 때 진동 피드백을 제공합니다.
- 미리보기에서 두 손가락 핀치 확대/축소가 실제 ZoomState 범위 내에서 연속 동작합니다. 영상 녹화 중에도 줌 조작을 허용합니다.
- 로그형 줌 다이얼은 낮은 배율에서도 미세한 줌을 가능하게 합니다. CameraX ZoomState LiveData로 실제 배율 변경과 UI를 동기화합니다.
- 줌 수학을 Android와 분리한 `ZoomMath.java` 및 자동 단위 테스트를 추가했습니다.

### 손떨림 방지 기능
- **끄기 / 표준 / 강화** 3단계. 표준은 가능한 경우 VideoCapture EIS를 사용하고, 불가능할 때 기기 OIS를 요청합니다.
- 강화는 Android 13+와 기기 Preview stabilization 지원, FHD 이하 30fps 등 조건이 만족되면 **미리보기와 영상의 동시 EIS 안정화**를 요청합니다.
- CameraX Preview ON + VideoCapture OFF 충돌을 피하기 위한 기능 선택 정책 `StabilizationPlan` 및 단위 테스트를 추가했습니다.
- Camera2 CaptureResult에서 실제 EIS·OIS 모드를 읽어 상태 배지에 `STAB 영상 EIS`, `STAB 미리보기+영상`, `STAB 렌즈 OIS`, `STAB 제한` 등으로 표시합니다. **지원되지 않는 보정을 적용됐다고 표시하지 않습니다.**
- 4K·60fps는 EIS 적용을 보장하지 않으므로 지원 기기에서 OIS를 요청하고 상태를 확인합니다.
- AE/AF 수동 노출 고정 시 60fps 녹화 셔터 상한을 1/60초에 맞춰 제한하고 ISO를 보정합니다.

### 제한사항·보안
- Apple iPhone 카메라 앱 고유의 소프트웨어·UI/디자인을 복제한 것이 아니라, 공개된 줌 **조작 방식**을 갤럭시 CameraX에 맞게 구현한 것입니다.
- **0.5× 초광각, 5× 망원, 광학 렌즈 전환은 갤럭시 카메라가 앱에 노출하는 사용 가능한 범위에 달려 있습니다.** 지원하지 않는 경우 별도 렌즈 전환은 구현되지 않으며 디지털 줌일 수 있습니다.
- 광학 OIS/EIS 품질 및 떨림 보정 효과는 스마트폰 기종, 조명, 녹화 해상도·fps에 따라 달라집니다. 실제 갤럭시 기기에서 촬영 테스트가 필요합니다.
- GitHub Release 빌드 및 테스트 통과는 실제 기기 호환성이나 삼성 피싱 경고 해결을 보증하지 않습니다. 보호 기능을 끄지 마세요.
- **기존 문제:** 고정 signing keystore가 GitHub Secrets에 없는 상태이므로 APK는 매번 일회용 키로 서명됩니다. 이전 버전을 삭제해야 설치될 수 있습니다.

### 직접 다운로드
- [I photo v2.2.0 APK](https://raw.githubusercontent.com/useToolkit/lol/i-photo-apk/I-photo-v2.2.1-release.apk)

## v2.1.0 — 통합 카메라 업데이트 (v1.9 UI / v2.0 색감 / v2.1 촬영)

### v1.9 UI·UX
- 촬영 하단 `사진 / 동영상 / 프로` 모드. 프로 모드는 ISO·셔터 측정 표시와 AE/AF 잠금 진입을 제공합니다.
- 사진·동영상의 주요 터치 영역을 44~48dp 수준으로 확대하고, 상단 정보 표시가 화면을 넘지 않도록 개선했습니다.
- 화면 비율, 안티플리커, 장면별 색감, 4K·fps·손떨림, Bokeh 설정은 외곽 설정창에 분리합니다.

### v2.0 장면별 색감 엔진
- 17³ LUT를 **33³ LUT**로 업그레이드. 기본 필터 5개(Luxury/Soft/Noir/Canon/원본) 유지.
- 장면 선택: `자동(카메라 노출값 기반 밝은 장면·야경 추정)`, `밝은 장면`, `야경`, `인물`.
- 인물 장면에서는 RGB 조건 기반의 은은한 피부톤 따뜻함을 보정합니다. **얼굴 검출/피부 분할 AI가 아니며**, 색상 프로파일의 픽셀 단위 일치를 보장하지 않습니다.
- 동영상 녹화/AE·AF 잠금 중 자동 LUT가 변하지 않도록 제한해 장면 전환 시 색조 튐을 줄입니다.
- `SceneToneTest`로 값 범위, 무채색 유지, 단조 증가를 자동 점검합니다.

### v2.1 동영상·인물 촬영
- `4K UHD / 1080p FHD / 720p HD`, `30 / 60fps 요청`을 촬영 설정에 추가했습니다.
- CameraX Recorder의 VideoCapabilities, Camera2 fps 범위로 현재 카메라의 지원 여부를 조회하여 지원하지 않는 설정은 자동 하향합니다.
- 60fps는 CameraX 영상 프레임 요청값이며 **최종 fps를 강제 보장하는 기능이 아닙니다**. 저장된 실제 영상 파일의 해상도와 평균 fps(메타데이터가 있는 경우)를 확인합니다.
- 흔들림 보정은 지원 카메라에서만 켜고, Android가 효과를 보장하는 최대 FHD·30fps 범위에서 활성화합니다. UHD/60fps에서 임의로 안정화됐다고 표시하지 않습니다.
- 기기의 OEM CameraX Extension `BOKEH`가 있는 경우에 한해 사진 인물 배경 흐림을 제공합니다. 사진 Bokeh 모드는 일반 촬영 LUT와 결합하지 않으며 동영상 Bokeh는 구현하지 않았습니다.
- 고급 조합에 실패하면 1080p·30fps 등 안전한 설정으로 복구하도록 개선했습니다. `VideoProfileTest`로 지원 능력별 하향 및 안정화 결정을 검사합니다.

### 기존 안정성·보안
- Camera2 기반 수동 ISO·셔터 고정 시 실제 센서 결과 검증, 플리커 설정(자동·50/60Hz), 기존 권한 최소화, GPU 오류 복구 유지.
- 인터넷, 연락처, 문자 접근 권한 없음. GitHub Actions에서 Release 서명·권한 검사 및 단위 테스트 진행.
- **미해결: GitHub Secrets에 고정 서명키가 등록되어 있지 않아 일회용 Release 키로 빌드됨.** 따라서 기존 APK 덮어 설치는 실패할 수 있습니다.
- **미해결: 삼성 보안 경고의 오탐 판정 및 실기기 실행 여부는 미확인.** 보호 기능을 비활성화하지 마세요.
- **기기별 실촬영 테스트 필요:** 사진/영상 크롭, 4K/60fps, 흔들림 보정, Bokeh, 10분 녹화, AE/AF 고정의 실제 동작은 APK 빌드만으로 검증되지 않습니다.

### 다운로드
- [I photo v2.1.0 Release APK](https://raw.githubusercontent.com/useToolkit/lol/i-photo-apk/I-photo-v2.1.0-release.apk)
- [GitHub Actions 검증 결과](https://github.com/useToolkit/lol/actions/workflows/android.yml?query=branch%3Ai-photo)

## v1.8.0 — 실사용 안정화
- 자동노출에는 Camera2 CONTROL_AE_ANTIBANDING_MODE 설정(자동·50Hz·60Hz·끄기)을 적용합니다.
- 수동 ISO/셔터 잠금에는 셔터를 100/120Hz 조명 주기의 배수에 가깝게 조정하고 ISO를 보정합니다(가능한 노출 길이에서만).
- Camera2 메타데이터로 감지된 주파수를 활용하되, 불명확할 때 주파수를 임의로 가정하지 않습니다.
- AF/AE 잠금 프레임 검증의 반복 성공/실패 알림을 억제하고, 카메라 전환 후 이전 세션의 메타데이터는 무시합니다.
- 카메라 연결 실패 시 먼저 필터 없는 촬영, 이어서 사진 전용 촬영으로 복구합니다. UI에 필터/영상 제한을 표시합니다.
- 카메라 준비 반복 횟수를 제한하고 앱 파괴 시 지연 복구를 중단합니다.
- 설정 창을 스크롤 가능하도록 변경해 작은 화면에서도 설정을 사용할 수 있습니다.
- `FlickerGuardTest` JVM 회귀 테스트가 GitHub Actions 빌드 이전에 실행됩니다.
- **미해결:** 서명키를 지속적으로 보관하는 GitHub Actions Secrets가 설정되지 않아, 빌드마다 일회용 Release 서명을 사용합니다. 서명키 설정은 계정 관리자의 명시적 조치가 필요합니다.
- **테스트 한계:** APK 빌드와 JVM 단위 테스트를 통과하더라도 실기기 AF/AE 고정·10분 영상 안정성이나 삼성 보안 경고 해소를 보장하지 않습니다.

## v1.7.0 — Canon EOS R5 Mark II + RF50mm F1.8 STM-inspired Look
- 다섯 번째 필터 `Canon` 추가: Canon EOS R5 Mark II Picture Style(Standard/Portrait)의 일반적 특징을 참고한 개인용 **창작 LUT**.
- 자연스러운 피부톤과 부드러운 주황 계열 색상, 적당한 채도·대비, 하이라이트의 완만한 톤 변화를 적용.
- Media3Effect와 기존 17³ LUT 처리 경로로 사진·동영상·실시간 미리보기 모두 동일한 톤 적용.
- 촬영화면 아래 필터 바에서 `Canon`을 탭해 선택; 앱을 다시 실행해도 마지막 선택 필터 유지.
- 기존 Luxury / Soft / Noir / Original 색상 연산에는 변경 없음. AE/AF 센서 고정 및 사진·영상 사용 로직 유지.
- 광학적 50mm 화각과 F1.8 심도·원형 보케, Canon의 센서·DSP·RAW 현상 결과를 그대로 복제하지는 않음.
- 원본 갤럭시 사진과 실제 Canon R5 II 같은 장면 비교·컬러차트 없이 전문 프로파일의 완전한 일치를 보장할 수 없음.
- Canon® 명칭은 참고한 카메라 모델 식별용이며 공식 제조사 인증/제휴가 아닙니다.
- 참고: https://cam.start.canon/en/C017/manual/html/UG-03_Shooting-1_0110.html
- 참고: https://files.canon-europe.com/files/webcontent/rf-lens-world/features/rf-lineup/index.html

## v1.6.0 — 실측 프레임 기반 AF + 고정 센서 노출
- 기존 AE_LOCK 명령만 보내던 로직을 변경. ProcessCameraProvider + UseCaseGroup + Camera2Interop CaptureCallback로 매 프레임의 SENSOR_SENSITIVITY(ISO), SENSOR_EXPOSURE_TIME(셔터), AE_MODE를 수집합니다.
- AF 지점에서 초점 수렴 후 그때의 ISO와 셔터 시간을 저장합니다. MANUAL_SENSOR 지원 갤럭시는 AE_MODE_OFF + SENSOR_SENSITIVITY + SENSOR_EXPOSURE_TIME 요청을 영상 전체 반복 요청으로 고정합니다.
- 최소 3개의 연속 프레임에서 AE_MODE_OFF와 ISO·셔터 값이 일치할 때만 실제 잠금 UI 표시. 값이 18프레임 연속 불일치하면 경고 후 잠금 해제.
- 태양 아이콘 조절로 밝기를 바꾸면 고정 기준에 대한 EV 차이를 ISO/셔터로 수동 적용합니다. 이후 다른 피사체로 옮겨도 자동으로 ISO·셔터를 재계산하지 않습니다.
- 하드웨어 수동 센서를 지원하지 않는 경우 CAMERA2 AE_LOCK을 실제 프레임에서 검증한 뒤 제한적인 AE 잠금으로 표시합니다. 이는 수동 센서 고정과 다르며 유지가 보장되지 않습니다.
- AF 모드 OFF 및 실제 렌즈 거리 수동 고정이 지원되는 카메라는 고정 지점의 포커스 거리도 보존합니다.
- 실제 고정된 ISO/셔터를 태양 아이콘 옆 텍스트에 표시해 사용자가 확인할 수 있습니다.
- 첨부된 사무실 영상에서는 비슷한 구도 사이에서 프레임 평균 밝기가 약 39 → 71/255로 오르내렸습니다. 이는 고정 노출 검증이 필요한 사례지만 LED 플리커/카메라 움직임도 영향을 줄 수 있습니다.
- 주의: 고정된 ISO·셔터는 장면의 광량 변화 자체, LED PWM 깜빡임, 센서 밖 ISP 처리까지 없애는 것은 아닙니다. 실기기 확인이 필수입니다.
- APK는 계속 비디버그 Release로 만들며 인터넷·SMS·연락처 권한이 없습니다.

## v1.5.0 — 개인정보 보호형 정식 Release 배포
- Debug APK가 아닌 `assembleRelease` 빌드 (앱 디버깅 비활성).
- Android SDK apksigner로 v1/v2/v3 서명 검증, SHA-256 및 매니페스트 권한 자동검사.
- 카메라 앱 최초 실행에는 CAMERA 권한만 요청. 동영상 녹화 시에만 RECORD_AUDIO 권한 요청 (거절하면 무음 녹화).
- INTERNET/ACCESS_NETWORK_STATE 권한이 라이브러리 합쳐지는 과정에서 추가되지 않도록 명시적으로 제거.
- 외부 서버, 광고 SDK 및 수집/분석 SDK 없음 (현재 앱 소스/매니페스트 기준).
- Release 서명키: GitHub Actions Secrets 네 가지가 설정되어 있을 경우 사용자 키를 사용.
  Secrets가 없으면 Actions 실행 중 임시 키로 서명되며, 이는 **이후 버전 덮어쓰기를 보장하지 않습니다**. 임시 키는 업로드하지 않습니다.
- 새 버전 APK: [I photo v1.5.0 Release 직접 다운로드](https://github.com/useToolkit/lol/raw/refs/heads/i-photo-apk/I-photo-v2.1.0-release.apk)
- **삼성 피싱앱 위험 경고를 회피/해제하거나 보안 심사를 우회하지 않습니다.** 삼성은 공식 스토어 외 설치, 설치 맥락, 권한 등의 신호를 사용할 수 있어 경고는 재발할 수 있습니다. 삼성멤버스 오탐 검토 또는 공식 스토어 배포가 필요할 수 있습니다.

### 개인 서명키를 고정해 업데이트 설치하려면
GitHub 저장소 Settings > Secrets and variables > Actions의 Repository secrets에 다음 네 가지를 입력하십시오.
`I_PHOTO_KEYSTORE_B64` (Keystore의 Base64), `I_PHOTO_STORE_PASS`, `I_PHOTO_KEY_PASS`, `I_PHOTO_KEY_ALIAS`.
**서명키나 비밀번호를 공개 저장소에 커밋하거나 채팅에 붙여 넣지 마세요.**
현재 키가 등록되지 않은 경우 첫 Release는 임시 키로 서명되며 다음 새 Release 설치 시 기존 앱 제거가 필요합니다.


> 첨부된 6장의 호텔·야경 레퍼런스를 참고해 만든 안드로이드 카메라 앱. 브랜드명: **I photo**.

## v1.1.0
- 촬영 장면 테두리와 코너 가이드: 실제 촬영 영역이 명확하게 보이도록 분리된 미리보기
- 사진/영상 화면 비율 전환: 4:3(세로 3:4), 9:16, 1:1, 3:2
- CameraX PreviewView ViewPort에 맞춰 미리보기 크기와 사진/영상 크롭 영역 연동
- 보정 슬라이더 접기/펼치기, 이전 촬영 비율 기억
- 실기기별 지원 해상도 및 영상 크롭 동작은 차이가 있을 수 있음

## 설치하기

- **[APK 바로 다운로드](https://github.com/useToolkit/lol/raw/refs/heads/i-photo-apk/I-photo-v2.1.0-release.apk)** (CI 빌드 성공 후 활성화)
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

## v1.4.0 — Persistent AE/AF lock + sun exposure slider
- Long-press viewfinder (520ms) or tap AF to initiate focus metering and lock at the ORIGINAL subject.
- CameraX FocusMeteringAction.disableAutoCancel() keeps AF after focus confirms. Camera2Interop additionally sets CONTROL_AE_LOCK=true, plus CONTROL_AWB_LOCK=true on supported hardware, preserving locked exposure (and color temperature) while reframing.
- The lock UI appears only AFTER hardware AE lock request succeeds; unsupported AE lock devices show an explanation instead of faking a lock.
- Sunlight icon with vertical dotted ruler adjacent to locked AF point. Drag UP to increase exposure compensation, DOWN to decrease (-/+ subject to camera's supported EV range). Camera2 allows EV compensation changes even while AE locked.
- Tap to unlock and focus another point; AF button toggles lock. Switching front/rear camera or changing capture ratio unlocks to prevent stale state.
- Preview UI overlay does NOT appear in saved photos or videos. Actual camera lock and EV behavior depend on device/firmware; physical lens changes may reset hardware camera state.
- On-device shooting verification still required.

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
