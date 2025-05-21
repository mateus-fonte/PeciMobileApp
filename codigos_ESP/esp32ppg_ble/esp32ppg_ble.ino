#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "RTClib.h"
#include "DFRobot_Heartrate.h"

// Pinos I2C (ESP32-S3)
#define I2C_DATA_PIN 40  // SDA
#define I2C_CLOCK_PIN 41 // SCL

// Pino do sensor de batimentos
#define HEARTRATE_PIN 5

// UUIDs BLE
#define SENSOR_UUID         "e626a696-36ba-45b3-a444-5c28eb674dd5"
#define SENSOR_DATA_UUID    "aa4fe3ac-56c4-42c7-856e-500b8d4b1a01"

#define CONFIG_UUID         "0a3b6985-dad6-4759-8852-dcb266d3a59e"
#define CONFIG_TIME_UUID    "ca68ebcd-a0e5-4174-896d-15ba005b668e"
#define CONFIG_FREQ_UUID    "e742e008-0366-4ec2-b815-98b814112ddc" //característica read/write

BLECharacteristic *bpmChar;
BLECharacteristic *timeChar;
BLECharacteristic *freqChar;
BLEServer *server = nullptr;

RTC_DS3231 rtc;
DFRobot_Heartrate heartrate(DIGITAL_MODE);

#define MIN_VALID_TIMESTAMP 1600000000
#define SETTING 0
#define RUNNING 1
int mode = SETTING;

int delay_millis = 500;
String sensorID = "PG";

void print_formated_date(DateTime dt) {
  Serial.printf("%d/%02d/%02d %02d:%02d:%02d\n", 
    dt.year(), dt.month(), dt.day(),
    dt.hour(), dt.minute(), dt.second()
  );
}

class serverCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) {
    Serial.println("[BLE] Cliente conectado");
    server->getAdvertising()->stop();  // Para o advertising para evitar novas conexões
    Serial.println("[BLE] Advertising parado.");
    mode = RUNNING;
    Serial.println("[System] modo RUNNING");
  }
  void onDisconnect(BLEServer *server) {
    Serial.println("[BLE] Cliente desconectado. Reiniciando advertising.");
    server->getAdvertising()->start();
    mode = SETTING;
    Serial.println("[System] modo SETTING");
  }
};

class TimeCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String raw = pChar->getValue();
    raw.trim();
    uint32_t timestamp = strtoul(raw.c_str(), NULL, 10);
    Serial.print("[BLE] Timestamp recebido: ");
    Serial.println(timestamp);
    if (timestamp > MIN_VALID_TIMESTAMP) {
      DateTime dt(timestamp);
      rtc.adjust(dt);
      Serial.print("[RTC] ajustado para: ");
      print_formated_date(dt);
    } else {
      Serial.println("[Erro] Timestamp inválido.");
      timeChar->setValue("Error: invalid timestamp");
    }
  }
};

class FreqCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String raw = pChar->getValue();
    raw.trim();
    uint32_t val = strtoul(raw.c_str(), NULL, 10);
    Serial.print("[BLE] Frequencia Recebida: ");
    Serial.println(val);
    if (val >= 200 && val <= 2000) {
      Serial.print("[System] Frequencia ajudata para: ");
      Serial.print(val);
      Serial.println(" ms de delay");
      delay_millis = val;
    } else {
      Serial.println("[Erro] Valor de frequencia inválida. ");
      timeChar->setValue("Error: invalid freq");
    }
  }
};

int setup_rtc() {
  Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
  Serial.print("[RTC] configurando");
  while (!rtc.begin()) {
    Serial.print(".");
    delay(20);
  }
  Serial.println("\n[RTC] RTC encontrado");
  
  // Ajusta para a hora da compilação
  rtc.adjust(DateTime(F(__DATE__), F(__TIME__)));
  
  Serial.print("[RTC] Data/hora atual: ");
  print_formated_date(rtc.now());
  return 1;
}

void setup_ble() {
  BLEDevice::init("ESP32_PPG_Heart_Box");
  server = BLEDevice::createServer();
  server->setCallbacks(new serverCallbacks());

  // Serviço de configuração
  BLEService *configService = server->createService(CONFIG_UUID);
  timeChar = configService->createCharacteristic(CONFIG_TIME_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ);
  freqChar = configService->createCharacteristic(CONFIG_FREQ_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ);
  timeChar->setCallbacks(new TimeCallback());
  freqChar->setCallbacks(new FreqCallback());
  configService->start();

  // Serviço do sensor
  BLEService *sensorService = server->createService(SENSOR_UUID);
  bpmChar = sensorService->createCharacteristic(SENSOR_DATA_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  bpmChar->addDescriptor(new BLE2902());
  sensorService->start();

  // Configuração do advertising
  BLEAdvertising *pAdvertising = BLEDevice::getAdvertising();
  pAdvertising->addServiceUUID(SENSOR_UUID);
  pAdvertising->addServiceUUID(CONFIG_UUID);
  pAdvertising->setScanResponse(true);
  
  server->getAdvertising()->start();
  Serial.println("[BLE] Serviços iniciados e advertising ativo");
}

int setup_sensor() {
  //verificação
  heartrate.getValue(HEARTRATE_PIN);
  heartrate.getRate(); //?
  Serial.println("[PPG] Sensor iniciado");
  return 1;
}

//declarar aqui variável global do valor do sensor
uint8_t bpm;
int get_sensor_data() {
  heartrate.getValue(HEARTRATE_PIN);
  bpm = heartrate.getRate();
  if (bpm == 0) {
    Serial.println("[PPG] Nenhum batimento detectado"); //imprime para log
    //return 0; //tirei isso pq assim manda esse zero por ble e funciona pra manter a conexão ativa
  }
  Serial.print("[PPG] BPM: ");
  Serial.println(bpm);
  return 1;
}

void setup() {
  Serial.begin(115200);
  delay(50);
  Serial.println("[Sistema] Iniciando...");
  setup_rtc();
  setup_sensor();
  setup_ble();
}

void loop() {
  if (mode == RUNNING) {
    if (get_sensor_data()) {
      uint32_t timestamp = rtc.now().unixtime();
      String ts = String(timestamp) + String(millis() % 1000);
      String payload = sensorID + ts + String(bpm);
      Serial.print("[BLE] Enviando: ");
      Serial.println(payload);
      bpmChar->setValue(payload.c_str());
      bpmChar->notify();
    }
  }

  delay(delay_millis);
}