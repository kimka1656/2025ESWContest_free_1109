#include <Arduino.h>
#include <math.h>
#include "Arduino_BMI270_BMM150.h"

#include "IMU.h"

#ifndef RETURN_SENSOR_FRAME         // 보드좌표계 → 센서(칩)좌표계로 역매핑할지 선택
#define RETURN_SENSOR_FRAME 1       // 1: 센서원축(chip frame)로 되돌려 반환, 0: 보드좌표계 그대로
#endif


//
// undoNano33BLEMap: board/ x=-y_s, y=-x_s, z= z_s  → sensor/ x_s=-y_b, y_s=-x_b, z_s=z_b
//
static void undoNano33BLEMapAccel(float& x, float& y, float& z) {
  float xb = x, yb = y; (void)z;
  x = -yb;
  y = -xb;
  // z unchanged
}

static void undoNano33BLEMapGyro(float& x, float& y, float& z) {
  float xb = x, yb = y; (void)z;
  x = -yb;
  y = -xb;
  // z unchanged
}



//
// vectorMagnitude3D: 3D 벡터의 크기 / 크기제곱을 반환
//
static float vectorMagnitudeSq3D(float x, float y, float z) {
  return x*x + y*y + z*z;
}

static float vectorMagnitude3D(float x, float y, float z) {
  return sqrtf(vectorMagnitudeSq3D(x, y, z));
}



//
// IMU_getMotion6: 가속도 3축, 자이로 3축 데이터 획득
//
static float ax, ay, az, gx, gy, gz;

static bool IMU_getMotion6(float& outAx, float& outAy, float& outAz, 
                    float& outGx, float& outGy, float& outGz) {                   
  if (IMU.accelerationAvailable() && IMU.gyroscopeAvailable()) {
    IMU.readAcceleration(outAx, outAy, outAz);
    IMU.readGyroscope(outGx, outGy, outGz);

    #if RETURN_SENSOR_FRAME
      #ifdef TARGET_ARDUINO_NANO33BLE
        undoNano33BLEMapAccel(outAx, outAy, outAz);
        undoNano33BLEMapGyro (outGx, outGy, outGz);
      #endif
    #endif

    return isfinite(outAx) && isfinite(outAy) && isfinite(outAz) &&
           isfinite(outGx) && isfinite(outGy) && isfinite(outGz);
  } 
  return false;
}

static bool IMU_getMotion6() {
  return IMU_getMotion6(ax, ay, az, gx, gy, gz);
}



//
// IMU_updateOffse: 지정한 샘플 수(N)로부터 기준 오프셋을 계산
//
static float axOff, ayOff, azOff, gxOff, gyOff, gzOff;

static void IMU_updateOffset(int N) {
  float axSum, aySum, azSum, gxSum, gySum, gzSum;
  axSum = aySum = azSum = gxSum = gySum = gzSum = 0.0f;
  int collected = 0;

  while (collected < N) {
    if (IMU_getMotion6()) {
      axSum += ax; aySum += ay; azSum += (az - 1.0f); 
      gxSum += gx; gySum += gy; gzSum += gz;
      collected++;
    }
    delayMicroseconds(100);
  }
  const float invN = 1.0f / N;
  axOff = axSum*invN; ayOff = aySum*invN; azOff = azSum*invN; 
  gxOff = gxSum*invN; gyOff = gySum*invN; gzOff = gzSum*invN;
  
  // Serial.print("  axOff = "); Serial.print(axOff, 5);
  // Serial.print("  ayOff = "); Serial.print(ayOff, 5);
  // Serial.print("  azOff = "); Serial.println(azOff, 5);
  // Serial.print("  gxOff = "); Serial.print(gxOff, 5);
  // Serial.print("  gyOff = "); Serial.print(gyOff, 5);
  // Serial.print("  gzOff = "); Serial.println(gzOff, 5);
}



// ===== 전역 함수 =============================================================================================================



//
// IMU_setup: IMU 연결 확인 및 Offset 업데이트
//
void IMU_setup() {
  if (!IMU.begin()) {
    Serial.println("Failed to initialize IMU!"); while (1);
  } Serial.println("Succeed to initialize IMU!");

  // Serial.print("Accel sample rate = "); Serial.print(IMU.accelerationSampleRate()); Serial.println("Hz");
  // Serial.print("Gyro  sample rate = "); Serial.print(IMU.gyroscopeSampleRate());    Serial.println("Hz");

  // 기준 오프셋 업데이트
  Serial.println("IMU stabilizing...");
  IMU_updateOffset(500);
  // axOff =  0.00553f; ayOff = -0.02495f; azOff =  0.01312f; 
  // gxOff = -0.09344f; gyOff =  0.06549f; gzOff = -0.10291f;
  Serial.println("IMU is ready!");
}



//
// IMU_readData: raw 데이터를 읽어 가속도 SVM(signal vector magnitude), X축 각도 계산
//
float SVM, AngleX;

void IMU_readData(float dt) {
  if (!IMU_getMotion6()) return;

  float axG, ayG, azG, gxR, gyR, gzR;
  axG = (ax - axOff); ayG = (ay - ayOff); azG = (az - azOff);
  gxR = (gx - gxOff); gyR = (gy - gyOff); gzR = (gz - gzOff);

  // SVM(signal vector magnitude) 측정
  SVM = (vectorMagnitude3D(axG, ayG, azG) - 1.0f) * 981.0f; //cm/s^2

  // 기울기 측정
  if (vectorMagnitudeSq3D(axG, ayG, azG) > 1e-9f) {
    float inv = 1.0f / vectorMagnitude3D(axG, ayG, azG);
    axG *= inv; ayG *= inv; azG *= inv; // 단위벡터 정규화

    float ac_AngleX = atan2f(ayG,azG) * RAD_TO_DEG;

    const float ALPHA = 0.988f;
    AngleX = ALPHA * (AngleX + gxR * dt) + (1.0 - ALPHA) * ac_AngleX;
  } else {
    AngleX = AngleX + gxR * dt;
  }
}


