# inmc-attendance 변경 기록

---

## 미배포 — 손에 든 아이템 대신 올리기(2026-10-14)

- 사용자 요청 "손에 든 것으로 정하지 말고 GUI 에 올려서 등록": 판 편집의 **그림**이 손에 든 것의 재질 → **아이템 올리기 화면**(가운데 칸에 가방의
  아이템을 누르거나 커서로 놓고 [등록] — 재질만 쓴다 · 가방·커서의 것은 빠지지 않는다).
- 이 플러그인은 inmc-core 없이 돌므로 core 의 `ItemInputMenu` 를 쓰지 않고 같은 모양을 `AttendanceGui.ItemInputMenu` 로 두었다.
  `gui/Menu` 에 core 와 같은 약속의 `onPlayerInventoryClick`(창 아래 내 가방 칸을 눌렀을 때 — true 면 취소·제자리)을 더했다.
- 메시지 `hand-empty` 를 뺐다(부르는 곳이 없어졌다). 서버의 `messages.yml` 에 남아 있어도 해가 없다.
- 보상 화면(칸에 올려놓는 방식)은 그대로.

## 2026-10-06 — 워크스페이스 모듈 경로

- 소스 폴더 이름이 `출첵플긴/` 으로 정해졌다(Gradle 경로는 `:attendance`, jar 이름은 그대로 `inmc-attendance-1.0.0.jar`).
  워크스페이스 `settings.gradle.kts` 가 이 폴더를 가리키게 고쳤다(빌드한 뒤 폴더 이름이 바뀌어 워크스페이스 전체가 빌드되지 않던 것)

## 2026-10-05 — 처음

- inmc-menu 의 출석체크만 떼어 낸 **배포용 단독 플러그인**. Java, inmc-core 의존 없음, Paper 26.1 이상
- 기능은 inmc-menu 와 같다 — 달력형·연속형·누적형 × 자동/수동, 칸마다 보상(아이템·명령어·돈)·이달 보너스, 사용할 달·복사(다음 달로),
  판 전체 초기화(회차)·`/출석 초기화 <플레이어> <판>`, 못 받은 보상 보관, 하루 경계(시간대·바뀌는 시각)
- 보상 아이템은 스냅샷으로 늘 보존하고, MMOItems 가 있으면 `mmoitems:TYPE:ID` 참조로 살아 있는 정의에서 지급한다
- Vault·PlaceholderAPI 는 있으면 쓰고 없어도 동작한다. 관리자 입력은 Paper Dialog
- inmc-menu 의 `attendance/`·`attendance-data/` 를 `plugins/inmc-attendance/` 에 복사하면 그대로 이어진다.
  권한은 `inmcattendance.*` 와 옛 `inmcmenu.attendance`·`inmcmenu.admin` 을 함께 인정한다
