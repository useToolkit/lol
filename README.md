# oily

현재 위치 기준 반경 5km의 주유소를 비교하는 초경량 Android 앱입니다.

## 구조
- 서버/Vercel 없음
- WebView 없음
- 외부 Android 라이브러리 없음
- 휴대폰이 오피넷 공식 API를 HTTPS로 직접 호출
- 주변 주유소 조회는 기본적으로 1회 호출
- 5분 로컬 캐시: 같은 위치(250m 이내)에서는 앱 재실행 시 즉시 표시
- 사용자가 새로고침을 누르면 최신 데이터 강제 조회

## 주요 기능
- 휘발유 / 경유
- 가격순 / 거리순
- 현재 위치 반경 5km
- 즐겨찾기 비교
- 네이버지도 열기
- 실제 예상비용 기반 강력추천
  - 예상 주유량
  - 차량 실연비
  - 편도 / 왕복 이동비
- Android 16 edge-to-edge / system bar inset 대응

## 데이터
오피넷 공식 일반 API를 사용합니다.
API 키는 앱 설정에서 한 번 입력하며 이 휴대폰의 SharedPreferences에만 저장됩니다.
앱은 오피넷 이외의 중간 서버를 사용하지 않습니다.

## 빌드
`gradle :app:assembleDebug`

GitHub Actions는 `oily-app` 브랜치 push 시 `Oily-APK` artifact를 생성합니다.
