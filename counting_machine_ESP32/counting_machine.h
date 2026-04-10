#pragma once
#include "MPU6050.h"
extern MPU6050 mpu;


// 보드 핀 설정
extern const uint8_t LED_PIN_A;
extern const uint8_t LED_PIN_B;
extern const uint8_t BUZZER_PIN;
extern const uint8_t BUTTON_PIN;



//
// updateBaseline: 지정한 샘플 수로부터 기준 가속도(오프셋)를 계산
//
extern int16_t ax, ay, az, gx, gy, gz;
extern float axOff, ayOff, azOff, gxOff, gyOff, gzOff;

void updateBaseline(int N);



//
// readSensorData: raw 데이터를 읽어 Z축 가속도를 계산
//
extern float accelerationZ, AngleX;
extern float ac_AngleX, gy_AngleX;

void readSensorData(float dt);



//
// processMotion: 가속도 필터링 및 적분을 통해 속도와 위치를 업데이트
//
// 센서 처리 관련 전역 변수
extern float prevLowPassAccelZ, lowPassAccelZ;        // 가속도 로우패스 필터링 값
extern float prevVelocity, velocity;                  // 적분된 속도
extern float prevHighPassVelocity, highPassVelocity;  // 속도 하이패스 필터링 값
extern float prevLowPassVelocity, lowPassVelocity;    // 속도 로우패스 필터링 값
extern float prevPosition, position;                  // 적분된 위치
extern float prevDeltaPosition, deltaPosition;        // 현재 루프에서의 위치 변화량
extern float minPosition;                             // 측정된 최소 위치 (추가 분석 가능)

extern bool movementStopped; // 운동이 정지된 상태 표시
extern bool reachedPeak; // 피크 여부 판단
extern uint8_t stabilityCounter; // 운동 정지 판단용 카운터

// 필터 상수 (각 필터의 시간상수)
extern const float ACCEL_LOW_PASS_COEFF;
extern const float VEL_HIGH_PASS_COEFF;
extern const float VEL_LOW_PASS_COEFF;

void processMotion(float dt);



//
// detectRep: 위치 변화와 변화율을 분석하여 운동 반복을 검출하고 카운트 업데이트
//
// 운동 반복 검출 관련 변수
extern uint16_t prevRepCount, repCount;               // 사이클(반동작) 카운트
extern uint16_t preOneRepMaxCount, oneRepMaxCount;    // 완전한 반복 횟수 (1RM)
extern float lastPosition;                            // 검출 기준 위치 (peak 발생 시점)
extern float averageRate, currentRate;                // 위치 변화율

extern float measuredSpeed;                           // 사용자의 한 동작 측정 시간
extern const uint16_t userSetSpeed;                   // 사용자의 한 동작 설정 시간
extern const uint16_t SPEED_TOL;                      // 여유값
extern uint8_t sampleCounter;                         // 검출 안정화를 위한 샘플 카운터

void detectRep();



//
// resetFiltersAndCounters: 인터럽트 발생 시 모든 필터와 카운터를 초기화
//
void resetFiltersAndCounters();