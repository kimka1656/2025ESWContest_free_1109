## 운동 애플리케이션 전체 흐름

### 주요 액티비티 및 객체 역할

- **`DailyPlanActivity`**:
  - 앱의 시작점.
  - 사용자가 운동 계획 확인 및 전체 운동 세션 시작.
  - `ExerciseManager.startExerciseSession(plan)` 호출로 운동 계획 설정.
- **`BleScanActivity`**:
  - 블루투스 기기(아두이노) 스캔 및 연결.
- **`DeviceControlActivity`**:
  - 연결된 아두이노와 통신 담당.
  - 아두이노로부터 "START_REQ" 메시지 수신.
  - 아두이노에게 `["ACK_START", 목표속도]` 메시지 전송.
  - `ExerciseManager` 상태에 따라 다음 운동 준비 및 `ExerciseSetActivity` 시작.
  - 한 운동 완료 후 `ExerciseSetActivity`가 종료되면, 이 화면으로 돌아와 다음 "START_REQ" 대기.
- **`ExerciseSetActivity`**:
  - 현재 진행 중인 한 가지 운동 항목(예: 스쿼트 3세트) 표시 및 관리.
  - `ExerciseManager` 통해 현재 운동 정보 UI에 표시.
  - "세트 완료" 시 `ExerciseManager.moveToNextStep()` 호출.
    - 세트 남음: `ExerciseManager`는 `RESTING` 상태, `RestTimerActivity` 시작. 휴식 후 복귀하여 다음 세트 진행.
    - 현재 운동 모든 세트 완료: `ExerciseManager`는 `IDLE` 상태, `ExerciseSetActivity`는 `finish()`로 종료.
- **`RestTimerActivity`**:
  - 세트 간 휴식 시간 관리.
- **`ExerciseManager` (싱글톤 객체)**:
  - 전체 운동 계획, 현재 진행 운동, 현재 세트, 현재 운동 세션 상태(`IDLE`, `WORKING`, `RESTING`, `FINISHED`) 관리.
- **`BleConnectionManager` (싱글톤 객체)**:
  - 블루투스 연결 및 데이터 송수신 로우레벨 관리.

---

### 전체 운동 진행 흐름

1.  **앱 시작 및 운동 계획 로드**:

    - 사용자가 앱 실행 시 `DailyPlanActivity` 표시.
    - `DailyPlanActivity`에서 운동 계획을 로드하여 `ExerciseManager.startExerciseSession(loadedPlan)` 호출.
      - `ExerciseManager`: `exerciseList` 채우고, `currentExerciseIndex`는 -1 (또는 0), `state`는 `IDLE`로 초기 설정. (첫 `START_REQ`를 통해 첫 운동이 `WORKING` 상태가 됨)

2.  **운동 시작 및 BLE 연결**:

    - 사용자가 `DailyPlanActivity`에서 "운동 시작" 버튼 클릭.
    - `BleScanActivity` 실행.
    - `BleScanActivity`에서 아두이노 기기 선택 및 연결.
    - 연결 성공 시, `BleScanActivity`가 `DeviceControlActivity` 실행 (기기 정보 전달).

3.  **첫 번째 운동 시작 (또는 다음 운동 시작)**:

    - `DeviceControlActivity` 활성화, BLE 알림 준비.
    - UI에 "START_REQ 대기 중..." 메시지 표시 가능.
    - **아두이노가 "START_REQ" 문자열 전송.**
    - `DeviceControlActivity`의 `onCharacteristicChanged` 콜백:
      1.  수신 데이터가 "START_REQ"이면, 아두이노에게 `["ACK_START", currentTargetSpeed]` 응답 전송.
      2.  `ExerciseManager.state` 확인:
          - `SessionState.IDLE` (첫 운동 시작 전 / 이전 운동 완료 후):
            - `ExerciseManager.prepareAndStartNextExercise()` 호출.
              - **성공** (다음 운동 있음): `ExerciseManager.state`는 `WORKING`으로 변경.
              - **실패** (모든 운동 완료): `ExerciseManager.state`는 `FINISHED`로 변경. "모든 운동 완료" 메시지 표시 후 흐름 중단/종료 고려.
          - `SessionState.WORKING` (예외적 상황, 또는 첫 운동이 이미 세팅된 경우): 현재 운동 그대로 진행.
          - `SessionState.RESTING` (비정상적): `ExerciseManager.finishRest()` 호출로 `WORKING` 전환 후 진행.
          - `SessionState.FINISHED`: "모든 운동 완료" 메시지 표시 후 흐름 중단/종료 고려.
      3.  진행할 운동이 있고 `ExerciseManager.state`가 `WORKING`이면, `ExerciseSetActivity`를 `Intent`로 시작. (`DeviceControlActivity`는 백스택에 유지)

4.  **운동 세트 진행 (`ExerciseSetActivity`)**:

    - `ExerciseSetActivity`는 `ExerciseManager`로부터 현재 운동 정보 받아 UI 표시.
    - 사용자 운동 수행.
    - **사용자 "세트 완료" (또는 유사 동작)**:
      - `ExerciseManager.moveToNextStep()` 호출.
        - **현재 운동에 남은 세트 존재 시**:
          - `ExerciseManager`: `currentSet` 증가, `state`는 `SessionState.RESTING`으로 변경.
          - `ExerciseSetActivity`는 `RestTimerActivity` 시작.
          - `RestTimerActivity` 종료/스킵 후 `ExerciseSetActivity`로 복귀.
          - `ExerciseSetActivity`에서 `ExerciseManager.finishRest()` 호출 (`state`를 `WORKING`으로), `refreshUi()`로 다음 세트 UI 업데이트.
        - **현재 운동의 모든 세트 완료 시**:
          - `ExerciseManager.moveToNextStep()`: `state`를 `SessionState.IDLE`로 변경.
          - `ExerciseSetActivity`의 `refreshUi()`에서 `ExerciseManager.state == SessionState.IDLE` 감지, `ExerciseSetActivity.finish()` 호출로 액티비티 종료.

5.  **다음 운동 대기 (`DeviceControlActivity`)**:

    - `ExerciseSetActivity`가 `finish()`되면, `DeviceControlActivity`로 제어 복귀.
    - `DeviceControlActivity`의 `onResume()` 호출.
      - UI에 "이전 운동 완료. 다음 운동 시작을 위한 START_REQ 대기 중..." 메시지 표시 (`ExerciseManager.state`는 `IDLE`).
    - 시스템은 다시 3단계의 "아두이노가 "START_REQ" 문자열 전송" 부분으로 돌아가 다음 운동 대기.

6.  **모든 운동 완료**:
    - 마지막 운동의 마지막 세트가 `ExerciseSetActivity`에서 완료.
      - `ExerciseManager.moveToNextStep()` -> `state`는 `IDLE`.
      - `ExerciseSetActivity.finish()`.
    - `DeviceControlActivity`로 제어 복귀 (`onResume` 호출).
    - 아두이노에서 다음 "START_REQ" 수신 시:
      - `DeviceControlActivity`는 `ExerciseManager.prepareAndStartNextExercise()` 호출.
      - `prepareAndStartNextExercise()`: 더 이상 운동 없으므로 `state`를 `SessionState.FINISHED`로 설정, `false` 반환.
      - `DeviceControlActivity`: `ExerciseSetActivity`로 넘어가지 않고 "모든 운동이 완료되었습니다!" 메시지 표시.
      - (이후 앱 동작은 추가 구현에 따름: 결과 화면, 액티비티 종료 등).
