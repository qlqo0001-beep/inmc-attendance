# inmc-attendance — 출석체크 단독 플러그인

inmc-menu의 출석체크 기능만 떼어낸 단독 플러그인이다. (배포용)
**inmc-core 의존 없음. Java. Paper 26.1 이상.**

## 빌드

```powershell
.\gradlew.bat :attendance:build --offline
```

jar: `출첵플긴/build/libs/inmc-attendance-1.0.0.jar` (소스 폴더는 `출첵플긴/`, Gradle 경로는 `:attendance`)

## 설치

1. 서버를 끈다.
2. `inmc-attendance-1.0.0.jar` 를 `plugins/` 에 넣는다.
3. (inmc-menu에서 옮겨올 때) `plugins/inmc-menu/attendance/` 와
   `plugins/inmc-menu/attendance-data/` 를 `plugins/inmc-attendance/` 에 그대로 복사한다.
   판 설정·개인 기록·못 받은 보상이 그대로 이어진다.
4. 서버를 켠다. inmc-menu와 같이 켜도 되지만 `/출석` 명령이 겹치므로
   옮겼으면 inmc-menu를 빼거나 출석판을 한쪽에만 두는 것을 권장한다.

## 명령어

| 명령어 | 권한 | 설명 |
|---|---|---|
| `/출석` | `inmcattendance.use` | 출석 화면 (판이 1개면 바로, 여러 개면 고르기) |
| `/출석 관리` | `inmcattendance.admin` | 판 만들기·고치기·복사·지우기·하루 경계 |
| `/출석 초기화 <플레이어> <판>` | `inmcattendance.admin` | 한 사람의 판 기록 초기화 (접속 안 해도 됨) |
| `/출석 리로드` | `inmcattendance.admin` | 설정·메시지·출석판 다시 읽기 |

구 권한(`inmcmenu.attendance`, `inmcmenu.admin`)도 함께 인정하므로
기존 권한 설정을 그대로 쓸 수 있다.

## 기능 (inmc-menu와 동일)

- 방식 3종: 달력형(1~31일·다음 달 새로 시작) · 연속형(하루 빠지면 1일째) · 누적형(총 n번째)
- 받는 법 2종: 자동(접속 후 정한 분 뒤) · 수동(오늘 칸 클릭)
- 칸 수 1~45, 끝난 뒤 반복/마지막 칸 유지, 자동 대기 0~1440분
- 칸마다 보상(아이템 + 콘솔 명령어 `{player}` + 돈) + 달력형 이달 보너스
- 월별 판: `사용할 달`(예: `2026-11`)을 정하면 그 달에만 열림.
  다음 달 판은 기존 판 **우클릭 복사** — 보상·설정 그대로, 달·이름의 달 자동 변경
- 가방이 차면 못 받은 보상을 보관했다가 `/출석` 화면 아래에서 받기
- 판 전체 초기화(회차 방식 — 접속 안 한 사람에게도 즉시 적용, 파일 훑지 않음)
- 하루 경계: 시간대 + 바뀌는 시각(0~23시) + 수동 판 미출석 안내
- 보상 아이템은 스냅샷으로 항상 보존. MMOItems가 있으면 `mmoitems:TYPE:ID` 참조도 함께 적어
  살아있는 정의에서 지급한다 (스탯 수정 반영). MMOItems가 없거나 ID가 지워져도
  스냅샷으로 지급되니 보상이 사라지지 않는다. 구 inmc-menu 파일의 MMOItems 보상도
  스냅샷이 들어있어 그대로 지급된다
- 돈 보상은 Vault 있으면 지급, 없으면 조용히 넘김 (서버에 Vault 없어도 동작)
- `%...%` 자리표시는 PlaceholderAPI 있으면 풀고, 없어도 동작
- 관리자 입력은 Paper Dialog 입력창이다 (원본 inmc-menu와 같은 UX — 26.1 API에 포함됨)

자세한 관리자 조작법은 `GUIDE.md`.
