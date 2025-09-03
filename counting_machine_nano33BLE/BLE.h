#pragma once


typedef void (*WorkLoopOnceFn)(); // 함수 포인터 타입 정의



//
// BLE_setup: BLE 초기화 및 서비스/특성 등록 후 advertise 시작
//
void BLE_setup();



//
// BLE_run: 연결/명령/START_REQ/ACK/Notify 상태머신
//
void BLE_run(WorkLoopOnceFn workOnce);
//  - 명령 수신/파싱
//  - 버튼 처리(START_REQ 송신)
//  - 콜백(workOnce) 실행
//  - 필요 시 JSON Notify


