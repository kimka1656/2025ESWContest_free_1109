#include <Arduino.h>

#include "IMU.h"
#include "counting_machine.h"


// ===== 전역 함수 =============================================================================================================



//
// processMotion: 가속도 필터링 및 적분을 통해 속도와 위치를 업데이트
//
float prevLowPassSVM = 0.0f, lowPassSVM = 0.0f;
float prevVelocity = 0.0f, velocity = 0.0f;
float prevHighPassVelocity = 0.0f, highPassVelocity = 0.0f;
float prevLowPassVelocity = 0.0f, lowPassVelocity = 0.0f;

static bool movementStopped = true;
static uint8_t stabilityCounter = 0;

const float ACCEL_LOW_PASS_COEFF = 53.0f;
const float VEL_HIGH_PASS_COEFF  =  0.5f;
const float VEL_LOW_PASS_COEFF   = 70.0f;

void processMotion(float dt) {
  lowPassSVM = (prevLowPassSVM + dt * SVM * ACCEL_LOW_PASS_COEFF) / (1 + ACCEL_LOW_PASS_COEFF * dt);
  
  if (fabsf(lowPassSVM) <= 80.0f) {
    lowPassVelocity = prevLowPassVelocity / 1.02f;

    // 운동 상태 진행 판단
    if (++stabilityCounter > 3) {
      movementStopped = true;
      stabilityCounter = 0;
    }
  } else {
    velocity = prevVelocity + ((lowPassSVM + prevLowPassSVM) / 2) * dt; // 사다리꼴 적분
    highPassVelocity = (VEL_HIGH_PASS_COEFF / (VEL_HIGH_PASS_COEFF + dt)) * (prevHighPassVelocity + velocity - prevVelocity);
    
    highPassVelocity -= highPassVelocity / 1000.0f;
    
    lowPassVelocity = (prevLowPassVelocity + dt * highPassVelocity * VEL_LOW_PASS_COEFF) / (1 + VEL_LOW_PASS_COEFF * dt);
    position = prevPosition + ((lowPassVelocity + prevLowPassVelocity) / 2) * dt; // 사다리꼴 적분

    // 운동 상태 정지 판단
    movementStopped = false;
    stabilityCounter = 0;
  }
}



//
// detectRep: 위치 변화와 변화율을 분석하여 운동 반복을 검출하고 카운트 업데이트
//
float prevPosition = 0.0f, position = 0.0f;
float prevDeltaPosition = 0.0f, deltaPosition = 0.0f;
float averageRate = 5.0f, currentRate = 0.0f;
float lastPosition = 0.0f;
uint16_t prevRepCount = 0, repCount = 0;
uint16_t preOneRepMaxCount = 0, oneRepMaxCount = 0;

static bool directionChanged = false;
static bool reachedPeak = false;

void detectRep() {
  deltaPosition = position - prevPosition;

  if (deltaPosition * prevDeltaPosition < 0) { directionChanged = true; }
  reachedPeak = (fabsf(prevDeltaPosition) > fabsf(deltaPosition)) ? true : false;

  currentRate = fabsf(position - lastPosition);
  
  // 변화량 부호가 바뀌고 피크 조건이 맞으면
  if (directionChanged && (repCount % 2) != reachedPeak) {

    // 사이클이 시작됐다고 판단되면 repCount 카운트 증가
    if ((repCount % 2) == 0 && currentRate >= averageRate && !movementStopped) {
      repCount++;
      averageRate = fabsf((averageRate + currentRate  * 0.47f) * 0.5f);
      lastPosition = position;
      directionChanged = false;
    }

    // 완전한 사이클(전체 rep)이 완료되면 1RM 카운트 증가
    if ((repCount % 2) == 1 && currentRate >= averageRate && movementStopped) {
      repCount++; oneRepMaxCount++;
      averageRate = fabsf((averageRate + currentRate * 0.47f) * 0.5f);
      lastPosition = position;
      directionChanged = false;

      prevVelocity = velocity = 0.0f;
      prevPosition = position = 0.0f;
    }
  }
}



//
// measureUserSpeed: 횟수 당 사용자 운동시간 측정 - 첫 1RM: 반사이클에서 측정 시작, 이후: 완전 사이클 시작점에서 측정 시작
//
static bool moveStoppedSpeed = true;
static uint8_t counterSpeed = 0;
static bool isFirstRep = true;
static bool phaseCheck = false;

bool measuring = false;
float measuredTime = 0.0f;
uint16_t userSetTime = 3;

bool measureUserSpeed() {
  if (fabsf(lowPassSVM) <= 80.0f) {
    moveStoppedSpeed = true;
    counterSpeed = 0;
  } else {
    if (++counterSpeed > 5) {
      moveStoppedSpeed = false;
      counterSpeed = 0;
    }
  }

  static uint32_t repLastTime;
  isFirstRep = (oneRepMaxCount == 0); // 운동 시작 전 움직임 측정시간 미반영
  phaseCheck = isFirstRep ? ((repCount % 2) == 1) : ((repCount % 2) == 0);

  if (!measuring) {
    if (phaseCheck && !moveStoppedSpeed) {
      repLastTime = millis();
      measuring = true;
    }
  } else {
    if (preOneRepMaxCount != oneRepMaxCount) {
      uint32_t repNowTime = millis();
      measuredTime = (repNowTime - repLastTime) / 1000.0f;
      measuredTime = isFirstRep ? (measuredTime * 2.0f) : measuredTime; // 첫 1RM: 반사이클 * 2, 이후: 완전 사이클
      measuring = false;

      return true;
    }
  }

  return false;
}



//
// updatePreviousState: // 다음 루프를 위한 이전 값 업데이트
//
void updatePreviousState() {
  prevLowPassSVM       = lowPassSVM;
  prevVelocity         = velocity; 
  prevLowPassVelocity  = lowPassVelocity;
  prevHighPassVelocity = highPassVelocity;
  prevPosition         = position;
  prevDeltaPosition    = deltaPosition;
  prevRepCount         = repCount;
  preOneRepMaxCount    = oneRepMaxCount;
}



//
// resetFiltersAndCounters: 인터럽트 발생 시 모든 필터와 카운터를 초기화
//
void resetFiltersAndCounters() {
  SVM = 0.0f;
  // AngleX = 0.0f;
  /////////////////////////////////////////////////
  prevLowPassSVM = lowPassSVM = 0.0f;
  prevVelocity = velocity = 0.0f;
  prevHighPassVelocity = highPassVelocity = 0.0f;
  prevLowPassVelocity = lowPassVelocity = 0.0f;

  movementStopped = true;
  stabilityCounter = 0;
  /////////////////////////////////////////////////
  prevPosition = position = 0.0f;
  prevDeltaPosition = deltaPosition = 0.0f;
  averageRate = 5.0f; currentRate = 0.0f;
  lastPosition = 0.0f;
  prevRepCount = repCount = 0;
  preOneRepMaxCount = oneRepMaxCount = 0;

  directionChanged = reachedPeak = false;
  sampleCounter = 0;
  /////////////////////////////////////////////////
  measuring = false;
  measuredTime = 0.0f; 
  
  moveStoppedSpeed = true;
  counterSpeed = 0;
}


