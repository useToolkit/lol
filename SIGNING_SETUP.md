# I photo 지속적인 업데이트 서명 설정

현재 공개 저장소의 GitHub Actions는 소유자만 GitHub Secrets로 서명키를 등록할 수 있도록 준비되어 있습니다.

계정 소유자가 신뢰할 수 있는 로컬 환경에서 아래 4가지 Secret을 설정하면, 다음 Release부터 동일한 서명으로 반복 배포할 수 있습니다.

- `I_PHOTO_KEYSTORE_B64`: 로컬에서 생성한 PKCS12/JKS keystore를 Base64 인코딩한 값
- `I_PHOTO_STORE_PASS`: 키스토어 암호
- `I_PHOTO_KEY_PASS`: 서명키 암호
- `I_PHOTO_KEY_ALIAS`: 키 별칭

설정: GitHub 저장소 → Settings → Secrets and variables → Actions → New repository secret.

서명키와 암호를 소스코드, 채팅, GitHub 이슈, Release 에셋에 업로드하지 마세요. 반드시 오프라인 백업하세요.

**주의:** 기존 v1.8.0 등과 동일하게 업데이트 설치하려면 **기존에 사용한 서명키**가 있어야 합니다.
이전 배포가 임시 일회용 키였으므로 과거 APK와 서명 일치는 불가능합니다.
새 키로 최초 설치한 이후에는 해당 키를 잃지 않아야 이후 버전 덮어 설치가 가능합니다.

삼성 피싱 보안 경고는 서명키를 변경한다고 자동 해소되지 않습니다. 삼성 공식 오탐 검토 및 공식 스토어 배포 절차는 별도로 진행해야 합니다.
