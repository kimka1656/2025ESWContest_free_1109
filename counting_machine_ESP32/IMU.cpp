#include <Arduino.h>
#include "counting_machine.h"
MPU6050 mpu;


//
// updateBaseline: 지정한 샘플 수로부터 기준 가속도(오프셋)를 계산
//
int16_t ax, ay, az, gx, gy, gz;
float axOff, ayOff, azOff, gxOff, gyOff, gzOff;

void updateBaseline(int N) {
  int32_t axSum, aySum, azSum, gxSum, gySum, gzSum;
  axSum = aySum = azSum = gxSum = gySum = gzSum = 0;

  for (int i = 0; i < N; i++) {
    //mpu.getAcceleration(&ax, &ay, &az);
    mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
    axSum += ax; aySum += ay; azSum += az; 
    gxSum += gx; gySum += gy; gzSum += gz;
    delay(1); yield();  //CPU 양보 / RTOS 협력 → WDT 리셋 방지
  }

  const float invN = 1.0f / N;
  axOff = axSum*invN; ayOff = aySum*invN; azOff = azSum*invN; 
  gxOff = gxSum*invN; gyOff = gySum*invN; gzOff = gzSum*invN;

  Serial.print("  axOff = "); Serial.print(axOff, 5);
  Serial.print("  ayOff = "); Serial.print(ayOff, 5);
  Serial.print("  azOff = "); Serial.println(azOff, 5);
  Serial.print("  gxOff = "); Serial.print(gxOff, 5);
  Serial.print("  gyOff = "); Serial.print(gyOff, 5);
  Serial.print("  gzOff = "); Serial.println(gzOff, 5);
}



//
// readSensorData: raw 데이터를 읽어 Z축 가속도를 계산
//
float accelerationZ = 0.0;
float ac_AngleX = 0.0, gy_AngleX = 0.0, AngleX = 0.0;

void readSensorData(float dt) {
  float axG, ayG, azG, gxR, gyR, gzR;

  //mpu.getAcceleration(&ax, &ay, &az);
  mpu.getMotion6(&ax, &ay, &az, &gx, &gy, &gz);
  
  // 횟수 측정용 (32767/4, 981 배율)
  accelerationZ = ((az - azOff) / 8192.0) * 981;

  //기울기 측정용
  axG = (ax - axOff);
  ayG = (ay - ayOff);
  azG = (az - azOff + 8192); //32767 / 4 = 8192
  ac_AngleX = atan2(ayG,azG) * RAD_TO_DEG;
  // ac_AngleX = atan(ayG / sqrt(pow(axG,2) + pow(azG,2))) * RAD_TO_DEG;

  gxR = (gx - gxOff) / 131;
  gyR = (gy - gyOff) / 131;
  gzR = (gz - gzOff) / 131; //32767 / 250 = 131
  gy_AngleX += gxR * dt;

  const float ALPHA = 0.99;
  //float ALPHA = 1 / (1 + dt);
  AngleX = ALPHA * (AngleX + gxR * dt) + (1.0 - ALPHA) * ac_AngleX;
}