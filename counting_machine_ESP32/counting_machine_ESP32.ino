#include "counting_machine.h"

// 보드 핀 설정
const uint8_t LED_PIN_A = 25;
const uint8_t LED_PIN_B = 26;
const uint8_t BUZZER_PIN = 14;
const uint8_t BUTTON_PIN = 13;


void setup() {
  Serial.begin(115200);
  while (!Serial) delay(10);  // USB CDC 기반에서 시리얼 안정화

  // // I2C, LCD 초기화
  // Wire.begin(21,22);
  // Wire.setClock(400000); ///
  
  // 핀 설정
  pinMode(LED_PIN_A, OUTPUT);
  pinMode(LED_PIN_B, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  
  // 버튼에 FALLING 엣지 인터럽트를 연결하여 상태 초기화 실행
  attachInterrupt(digitalPinToInterrupt(BUTTON_PIN), resetFiltersAndCounters, FALLING);
  
  // IMU 초기화 및 연결 확인
  mpu.initialize();
  if (!mpu.testConnection()) {
    Serial.print("MPU6050 connection failed"); while (1);
  } Serial.println("MPU6050 connection successful");

  // 셋업 코드 (예: 센서 설정)
  mpu.setFullScaleAccelRange(MPU6050_ACCEL_FS_4); // 가속도 범위 설정 (예: ±4g)
  mpu.setFullScaleGyroRange(MPU6050_GYRO_FS_250); // 자이로 범위 설정 (예: ±250 °/s)
  mpu.setSleepEnabled(false); // 슬립 모드 비활성화

  // 기준 가속도 업데이트
  Serial.println("Sensor stabilizing...");
  updateBaseline(800);
  Serial.println("Sensor is ready!");
  delay(5000);
}


void loop() {
  static uint32_t lastTime = micros(); // 시간 측정 (마이크로초)
  uint32_t nowTime = micros();
  float dt = (nowTime - lastTime) / 1000000.0; // 지난 시간 dt (초) 계산
  lastTime = nowTime;

  // 운동 처리
  readSensorData(dt); // 센서 데이터 업데이트

  const float THRESH_ANGLE = 10.0f;
  digitalWrite(LED_PIN_A, (AngleX >  THRESH_ANGLE) ? HIGH : LOW);
  digitalWrite(LED_PIN_B, (AngleX < -THRESH_ANGLE) ? HIGH : LOW);

  processMotion(dt); // 필터링 및 적분: 속도와 위치 갱신
  detectRep(); // 위치 변화 기반 운동 반복(Rep) 검출

  if (preOneRepMaxCount != oneRepMaxCount) {
    if (measuredSpeed > userSetSpeed) {
      tone(BUZZER_PIN, 440, 800);
    } else {
      tone(BUZZER_PIN, 262, 200);
    }
  }

  // 다음 루프를 위한 이전 값 업데이트
  prevLowPassAccelZ = lowPassAccelZ;
  prevVelocity = velocity;
  prevLowPassVelocity = lowPassVelocity;
  prevHighPassVelocity = highPassVelocity;
  prevPosition = position;
  prevDeltaPosition = deltaPosition;
  prevRepCount = repCount;
  preOneRepMaxCount = oneRepMaxCount;
  
  static uint8_t cnt_loop = 0;
  if (++cnt_loop < 7) return;  // 7루프마다
  cnt_loop = 0;

  // 디버그 출력 (Serial 모니터)
  // Serial.print(ax); Serial.print("      ");
  // Serial.print(ay); Serial.print("      ");
  // Serial.print(az); Serial.print("      ");
  // Serial.print(axG); Serial.print("      ");
  // Serial.print(ayG); Serial.print("      ");
  // Serial.print(azG); Serial.print("      ");

  // Serial.print(gx); Serial.print("      ");
  // Serial.print(gy); Serial.print("      ");
  // Serial.print(gz); Serial.print("      ");
  // Serial.print(gxR); Serial.print("      ");
  // Serial.print(gyR); Serial.print("      ");
  // Serial.print(gzR); Serial.print("      ");

  // Serial.print(ac_AngleX); Serial.print("      ");
  // Serial.print(gy_AngleX); Serial.print("      ");
  Serial.print(AngleX); Serial.print("      ");

  //Serial.print(accelerationZ, 5); Serial.print("      ");
  //Serial.print(lowPassAccelZ, 5); Serial.print("      ");
  Serial.print(position); Serial.print("      ");

  Serial.print(repCount); Serial.print("      ");
  Serial.print(oneRepMaxCount); Serial.print("      ");
  // Serial.print(averageRate); Serial.print("      ");
  // Serial.print(currentRate); Serial.print("      ");

  Serial.print(userSetSpeed); Serial.print("      ");
  Serial.print(measuredSpeed); Serial.print("      ");
  Serial.println();
}