#include "IMU.h"
#include "BLE.h"
#include "counting_machine.h"


const uint8_t LED_PIN_PWR = D7;
const uint8_t LED_PIN_BLE = D4;
const uint8_t LED_PIN_L   = D8;
const uint8_t LED_PIN_R   = D5;
const uint8_t BUTTON_PIN  = D3;
const uint8_t BUZZER_PIN  = D6;

void processLoop();



void setup() {
  Serial.begin(115200);
  
  pinMode(LED_PIN_PWR, OUTPUT);
  pinMode(LED_PIN_BLE, OUTPUT);
  pinMode(LED_PIN_L,   OUTPUT);
  pinMode(LED_PIN_R,   OUTPUT);
  pinMode(BUZZER_PIN,  OUTPUT);
  pinMode(BUTTON_PIN,  INPUT_PULLUP);
  
  attachInterrupt(digitalPinToInterrupt(BUTTON_PIN), resetFiltersAndCounters, FALLING);
  
  IMU_setup();
  BLE_setup();
  delay(1);
}



void loop() {
  BLE_run(processLoop);
  // processLoop();
}



//
// processLoop: // 1프레임 당 작업 콜백
//
void processLoop() {
  static uint32_t lastTime = micros();
  uint32_t nowTime = micros();
  float dt = (nowTime - lastTime) / 1000000.0f;
  lastTime = nowTime;

  // 센서 데이터 업데이트
  IMU_readData(dt);

  // 기울기 피드백
  const float THRESH_ANGLE = 5.0f;
  digitalWrite(LED_PIN_L, (AngleX >  THRESH_ANGLE) ? HIGH : LOW);
  digitalWrite(LED_PIN_R, (AngleX < -THRESH_ANGLE) ? HIGH : LOW);

  // 횟수 측정
  processMotion(dt);
  detectRep();

  // 운동 속도 피드백
  if (measureUserSpeed()) {
    if (measuredTime > userSetTime) {
      tone(BUZZER_PIN, 440, 800);
    } else {
      tone(BUZZER_PIN, 262, 200);
    }
  }

  // 다음 루프를 위한 이전 값 업데이트
  updatePreviousState();
}


