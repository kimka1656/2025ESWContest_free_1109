#include <Arduino.h>
#include <string.h>
#include <ArduinoBLE.h>

#include "IMU.h"                   // SVM, AngleX extern
#include "BLE.h"
#include "counting_machine.h"      // BUTTON_PIN / BUZZER_PIN / rep & timing extern


// BLE 서비스/특성 UUID
static BLEService sensorService("12345678-1234-5678-1234-56789abcde00");

// 아두이노 → 앱 (데이터/문자열)
static BLECharacteristic dataChar(
  "12345678-1234-5678-1234-56789abcde02",
  BLERead | BLENotify, 96);

// 앱 → 아두이노 (명령/응답)
static BLECharacteristic cmdChar(
  "12345678-1234-5678-1234-56789abcde10",
  BLEWrite | BLEWriteWithoutResponse, 32);

// 상태 제어
enum State { IDLE, WAIT_ACK, STREAMING };
static State state = IDLE;
static bool streaming = false;



//
// onBleConnected / onBleDisconnected: 이벤트 핸들러 함수
//
static volatile bool BLE_connected = false;

void onBleConnected(BLEDevice central) {
  Serial.print("Connected: ");
  Serial.println(central.address());
  BLE_connected = true;

  digitalWrite(LED_PIN_BLE, HIGH);
}

void onBleDisconnected(BLEDevice central) {
  state = IDLE;
  streaming = false; // 상태초기화
  
  Serial.print("Disconnected: ");
  Serial.println(central.address());
  BLE_connected = false;

  digitalWrite(LED_PIN_BLE, LOW);
  BLE.advertise();
  Serial.println("BLE Advertising...");
}



//
// parseCommand: 명령 parsing
//
static void parseCommand(char *buf) {
  // App Inventor: ["ACK_START",15] 같은 리스트 → 첫 토큰과 숫자 추출
  char* token = strtok(buf, ",");
  if (!token) return;
  if (token[0] == '[' || token[0] == '"') token++;
  if (char* end = strchr(token, '"')) *end = 0;

  Serial.print("Parsed token: "); Serial.println(token);

  // 두 번째 토큰(숫자) → userSetTime 갱신
  if (char* numToken = strtok(NULL, ",]")) {
    int value = atoi(numToken);
    if (value < 0) value = 0;
    userSetTime = (uint16_t)value;
    Serial.print("Parsed number: "); Serial.println(value);
  }

  if (strcmp(token, "ACK_START") == 0) {
    if (state == WAIT_ACK) {
      state = STREAMING;
      streaming = true;
      const char* ack = "ACKED";
      dataChar.writeValue((uint8_t*)ack, strlen(ack));
      tone(BUZZER_PIN, 1200, 100);
    }
  } else if (strcmp(token, "STOP") == 0) {
    state = IDLE;
    streaming = false;
    const char* stopmsg = "ACK STOP";
    dataChar.writeValue((uint8_t*)stopmsg, strlen(stopmsg));
  }
}



//
// sendStartReq_: START_REQ 공통 전송 루틴(내부 전용)
//
static unsigned long lastReqMs = 0;

static inline void sendStartReq_() {
  const char* req = "START_REQ";
  dataChar.writeValue((uint8_t*)req, strlen(req));
  lastReqMs = millis();
}



//
// handleButtonHandshake: 버튼 눌림 → 매번 handshake 재시도 (START_REQ 송신 → WAIT_ACK)
//
static bool btnPrev = HIGH;
static unsigned long lastDebounce = 0;
static const unsigned long debounceMs = 30;

static void handleButtonHandshake() {
  int btnNow = digitalRead(BUTTON_PIN);
  if (btnNow != btnPrev && (millis() - lastDebounce) > debounceMs) {
    lastDebounce = millis();
    btnPrev = btnNow;

    if (btnNow == LOW) {
      // 어떤 상태든 새로 핸드셰이크 시작
      streaming = false;
      state = WAIT_ACK;
      sendStartReq_();
      Serial.println("Notify: START_REQ");
    }
  }
}



//
// timeoutRetryHandshake: WAIT_ACK 동안 ACK 타임아웃 처리 (재요청)
//
static const unsigned long ackTimeoutMs = 3000; // 3초 내 ACK 없으면 재요청

static void timeoutRetryHandshake() {
  if (state == WAIT_ACK && (millis() - lastReqMs) > ackTimeoutMs) {
    sendStartReq_();
    Serial.println("Notify: START_REQ (retry)");
    // 필요하면 일정 횟수 넘으면 IDLE로 되돌리는 로직도 가능
  }
}



//
// notifyJsonAfterCount: oneRepMaxCount 바뀔 때 1회 Notify(JSON)
//
static uint16_t lastSentOneRepMax = 0;

static void notifyJsonAfterCount() {
  if (state != STREAMING || !streaming) return;
  if (oneRepMaxCount == lastSentOneRepMax) return;

  lastSentOneRepMax = oneRepMaxCount;

  char json[96];
  int len = snprintf(json, sizeof(json),
            "{\"balance\":%.3f,\"time\":%.3f,\"count\":%u}",
            (float)AngleX, (float)measuredTime, (uint16_t)oneRepMaxCount);
  if (len <= 0) return;

  // 잘림 대비: 실제 전송 길이를 버퍼 한계로 클램프
  size_t nsend = (len < (int)sizeof(json)) ? (size_t)len : (sizeof(json) - 1);

  Serial.write((const uint8_t*)json, nsend);
  if (dataChar.subscribed()) {
    dataChar.writeValue((uint8_t*)json, nsend);
  }
}



// ===== 전역 함수 =============================================================================================================



//
// BLE_setup: BLE 초기화 및 서비스/특성 등록 후 advertise 시작
//
void BLE_setup() {
  if (!BLE.begin()) {
    Serial.println("Failed to initialize BLE!"); while (1);
  } Serial.println("Succeed to initialize BLE!");
  
  BLE.setLocalName("accelerate");
  BLE.setAdvertisedService(sensorService);
  sensorService.addCharacteristic(dataChar);
  sensorService.addCharacteristic(cmdChar);
  BLE.addService(sensorService);

  BLE.setEventHandler(BLEConnected,    onBleConnected);
  BLE.setEventHandler(BLEDisconnected, onBleDisconnected);

  BLE.advertise();
  Serial.println("BLE Advertising...");
}



//
// BLE_run: 연결/명령/START_REQ/ACK/Notify 상태머신
//
void BLE_run(WorkLoopOnceFn workOnce) {
  BLE.poll();
  digitalWrite(LED_PIN_PWR, HIGH);

  if (!BLE_connected) {
    static uint16_t cnt_loop = 0;
    static bool ledState = LOW;
    if (++cnt_loop >= 10000) {
      cnt_loop = 0; ledState = !ledState;
      digitalWrite(LED_PIN_BLE, ledState);
    }
    return;
  } // 미연결 상태일 때 advertise 유지 & LED 점멸

  // 연결 상태일 때만 수행
  if (cmdChar.written()) {
    char buf[32];
    int n = cmdChar.readValue((uint8_t*)buf, sizeof(buf) - 1);
    if (n <= 0) return;
    buf[n] = '\0';
    parseCommand(buf);             // 앱 → 아두이노 명령 수신
  }

  handleButtonHandshake();         // 핸드셰이크 관리 (버튼)
  timeoutRetryHandshake();         // 핸드셰이크 관리 (타임아웃)

  if (workOnce) { workOnce(); }    // 사용자 주기 작업

  notifyJsonAfterCount();          // 알림 전송
}


