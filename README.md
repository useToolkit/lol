# 주유소픽 (FuelPick)

현재 위치 기준으로 반경 5km의 주유소를 조회하고, 가격순/거리순 비교와 즐겨찾기, 실질비용 기반 추천 주유소를 제공하는 Android 앱입니다.

## 주요 기능
- 휘발유 / 경유 선택
- 현재 위치 반경 5km 오피넷 가격 조회
- 가격순 / 거리순 정렬
- **실질비용 기반 강력추천**
  - 예상 주유량 × 리터당 가격
  - 주유소 이동거리 ÷ 차량 실연비 × 주변 기준 유가
  - 편도/왕복 기준 선택
  - 기본값: 40L / 10km/L / 왕복
  - 가장 가까운 주유소 대비 예상 절약액 표시
- 즐겨찾기 주유소를 추천 카드 바로 아래에서 별도 비교
- 주유소별 네이버지도 열기
- Android 16 edge-to-edge + system bar inset 대응
- 즐겨찾기와 설정은 기기 내부 SharedPreferences에 저장

## 오피넷 API 키
오피넷 일반 API는 인증키가 필요합니다. 앱 우상단 설정에서 인증키를 한 번 저장하면 됩니다.

발급: https://www.opinet.co.kr/user/custapi/custApiInfo.do

사용 API:
- aroundAll.do: 반경 내 주유소
- detailById.do: 즐겨찾기 주유소 상세/가격 갱신

## 빌드
gradle :app:assembleDebug

GitHub Actions는 fuelpick-app 브랜치 push 시 APK를 FuelPick-APK artifact로 생성합니다.
