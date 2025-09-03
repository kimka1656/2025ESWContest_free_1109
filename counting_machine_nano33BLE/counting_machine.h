#pragma once


extern const uint8_t LED_PIN_PWR;
extern const uint8_t LED_PIN_BLE;
extern const uint8_t LED_PIN_L;
extern const uint8_t LED_PIN_R;
extern const uint8_t BUTTON_PIN;
extern const uint8_t BUZZER_PIN;



//
// processMotion: 가속도 필터링 및 적분을 통해 속도와 위치를 업데이트
//
extern float prevLowPassSVM, lowPassSVM ;               // 가속도 로우패스 필터링 값
extern float prevVelocity, velocity;                    // 적분된 속도
extern float prevHighPassVelocity, highPassVelocity;    // 속도 하이패스 필터링 값
extern float prevLowPassVelocity, lowPassVelocity;      // 속도 로우패스 필터링 값

void processMotion(float dt);



//
// detectRep: 위치 변화와 변화율을 분석하여 운동 반복을 검출하고 카운트 업데이트
//
extern float prevPosition, position;                    // 적분된 위치
extern float prevDeltaPosition, deltaPosition;          // 현재 루프에서의 위치 변화량
extern float averageRate, currentRate;                  // 위치 변화율
extern float lastPosition;                              // 검출 기준 위치 (peak 발생 시점)
extern uint16_t prevRepCount, repCount;                 // 사이클(반동작) 카운트
extern uint16_t preOneRepMaxCount, oneRepMaxCount;      // 완전한 반복 횟수 (1RM)

void detectRep();



//
// measureUserSpeed: 횟수 당 사용자 운동시간 측정 - 첫 1RM: 반사이클에서 측정 시작, 이후: 완전 사이클 시작점에서 측정 시작
//
extern float measuredTime;                              // 사용자의 한 동작 측정 시간
extern uint16_t userSetTime;                            // 사용자의 한 동작 설정 시간

bool measureUserSpeed();



//
// updatePreviousState: // 다음 루프를 위한 이전 값 업데이트
//
void updatePreviousState();



//
// resetFiltersAndCounters: 인터럽트 발생 시 모든 필터와 카운터를 초기화
//
void resetFiltersAndCounters();


