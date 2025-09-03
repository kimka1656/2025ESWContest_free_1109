#pragma once


//
// IMU_setup: IMU 연결 확인 및 Offset 업데이트
//
void IMU_setup();



//
// IMU_readData: raw 데이터를 읽어 가속도 SVM(signal vector magnitude), X축 각도 계산
//
extern float SVM, AngleX;

void IMU_readData(float dt);


