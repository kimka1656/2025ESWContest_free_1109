#include <Arduino.h>
#include "counting_machine.h"


//
// processMotion: 가속도 필터링 및 적분을 통해 속도와 위치를 업데이트
//
// 센서 처리 관련 전역 변수
float prevLowPassAccelZ = 0.0, lowPassAccelZ = 0.0;       // 가속도 로우패스 필터링 값
float prevVelocity = 0, velocity = 0;                     // 적분된 속도
float prevHighPassVelocity = 0, highPassVelocity = 0;     // 속도 하이패스 필터링 값
float prevLowPassVelocity = 0, lowPassVelocity = 0;       // 속도 로우패스 필터링 값
float prevPosition = 0, position = 0;                     // 적분된 위치
float prevDeltaPosition = 0, deltaPosition = 0;           // 위치 변화량
float minPosition = 0;                                    // 측정된 최소 위치 (추가 분석 가능)

bool movementStopped = false;                             // 운동이 정지된 상태 표시
bool reachedPeak = false;                                 // 피크 여부 판단
uint8_t stabilityCounter = 0;                             // 운동 정지 판단용 카운터

const float ACCEL_LOW_PASS_COEFF = 53.0f;                 // 가속도 로우패스 필터 상수
const float VEL_HIGH_PASS_COEFF  =  0.5f;                 // 속도 하이패스 필터 상수
const float VEL_LOW_PASS_COEFF   = 70.0f;                 // 속도 로우패스 필터 상수

void processMotion(float dt) {
  // 가속도에 로우패스 필터 적용
  lowPassAccelZ = (prevLowPassAccelZ + dt * accelerationZ * ACCEL_LOW_PASS_COEFF) / (1 + ACCEL_LOW_PASS_COEFF * dt);
  
  if (abs(lowPassAccelZ) <= 30) {
    // 가속도가 기준치 이하이면 속도 감쇠 처리
    lowPassVelocity = prevLowPassVelocity / 1.02;/////////////////////////////////////

    // 운동 상태 진행 판단
    stabilityCounter++;
    if (stabilityCounter > 5) {/////////////////////////////////////
      movementStopped = true;
      stabilityCounter = 0;
    }
  } else {
    // 가속도 적분 -> 속도 (트래피조이드 적분)
    velocity = prevVelocity + ((lowPassAccelZ + prevLowPassAccelZ) / 2) * dt;
    // 속도에 하이패스 필터 적용
    highPassVelocity = (VEL_HIGH_PASS_COEFF / (VEL_HIGH_PASS_COEFF + dt)) * (prevHighPassVelocity + velocity - prevVelocity);
    
    // 드리프트 보정을 위한 보상
    if(highPassVelocity < 0) { 
      highPassVelocity += (0 - highPassVelocity) / 500.0;
    } else {
      highPassVelocity -= (0 + highPassVelocity) / 500.0;
    }
    
    // 하이패스 처리된 속도에 로우패스 필터 적용
    lowPassVelocity = (prevLowPassVelocity + dt * highPassVelocity * VEL_LOW_PASS_COEFF) / (1 + VEL_LOW_PASS_COEFF * dt);
    // 필터링된 속도 적분 -> 위치 (트래피조이드 적분)
    position = prevPosition + ((lowPassVelocity + prevLowPassVelocity) / 2) * dt;

    // 운동 상태 정지 판단
    movementStopped = false;
    stabilityCounter = 0;
  }
  
  // 최소 위치 기록 (추후 분석 용도)
  if (position < minPosition) { minPosition = position; }
  // 위치 변화량 계산 (반복 검출에 사용)
  deltaPosition = position - prevPosition;
  // 현재와 이전 위치 변화량 비교로 피크 도달 여부 판단
  reachedPeak = (prevDeltaPosition > deltaPosition) ? true : false;
}



//
// detectRep: 위치 변화와 변화율을 분석하여 운동 반복을 검출하고 카운트 업데이트
//
// 운동 반복 검출 관련 변수
uint16_t prevRepCount = 0, repCount = 0;              // 사이클(반동작) 카운트
uint16_t preOneRepMaxCount = 0, oneRepMaxCount = 0;   // 완전한 반복 횟수 (1RM)
float lastPosition = 0;                               // 검출 기준 위치 (peak 발생 시점)
float averageRate = 5.0, currentRate = 0;             // 위치 변화율

float measuredSpeed = 0;                              // 사용자의 한 동작 측정 시간
const uint16_t userSetSpeed = 5;                      // 사용자의 한 동작 설정 시간
const uint16_t SPEED_TOL = 0.10;                      // 여유값

uint8_t sampleCounter = 0;                            // 검출 안정화를 위한 샘플 카운터

void detectRep() {
  // 기준 위치(lastPosition)로부터의 현재 변화율 계산
  currentRate = abs(position - lastPosition);
  
  // 변화량 부호가 바뀌고 피크 조건이 맞으면(반동작) repCount 업데이트
  if ((deltaPosition * prevDeltaPosition < 0) && ((repCount % 2) != reachedPeak)) {
    if (sampleCounter >= 3) {
      sampleCounter = 0; /////////////////////////////////////
      if ((repCount % 2) == 0 && currentRate >= averageRate && !movementStopped) {
        repCount++;
        averageRate = abs((averageRate + currentRate * 0.5) / 2);
        lastPosition = position;
        // AngleX;
      } 
      // else if ((repCount % 2) == 1 && currentRate >= averageRate && movementStopped) {
      //   repCount++;
      //   averageRate = abs((averageRate + currentRate * 0.5) / 2);
      //   lastPosition = position;
      // } 
    }
  }
  sampleCounter++;
  
  // 완전한 사이클(전체 rep)이 완료되면 1RM 카운트 증가
  static uint32_t repLastTime = micros(); // 시간 측정 (마이크로초)
  if ((repCount % 2) == 1 && (currentRate > averageRate) && movementStopped) {
    uint32_t repNowTime = micros();
    measuredSpeed = (repNowTime - repLastTime) / 1000000.0; // 지난 시간 dt (초) 계산
    repLastTime = repNowTime;

    repCount++; oneRepMaxCount++;
    averageRate = abs((averageRate + currentRate * 0.5) / 2);
    lastPosition = position;
    // AngleX;

    // 반복 완료 후 위치 초기화
    prevVelocity = velocity = 0;
    prevPosition = position = 0;
  }
}



//
// resetFiltersAndCounters: 인터럽트 발생 시 모든 필터와 카운터를 초기화
//
void resetFiltersAndCounters() {
  accelerationZ = 0.0;
  ac_AngleX = gy_AngleX = AngleX = 0.0;

  prevLowPassAccelZ = lowPassAccelZ = 0;
  prevVelocity = velocity = 0;
  prevHighPassVelocity = highPassVelocity = 0;
  prevLowPassVelocity = lowPassVelocity = 0;
  prevPosition = position = 0;
  prevDeltaPosition = deltaPosition = 0;
  minPosition = 0;

  stabilityCounter = 0;
  movementStopped = false;
  reachedPeak = false;

  /////////////////////////
  averageRate = 5.0; currentRate = 0;
  lastPosition = 0;
  prevRepCount = repCount = 0;
  preOneRepMaxCount = oneRepMaxCount = 0;

  measuredSpeed = 0;
  sampleCounter = 0;
}