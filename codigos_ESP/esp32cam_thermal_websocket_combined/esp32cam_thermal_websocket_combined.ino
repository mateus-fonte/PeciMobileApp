#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include "RTClib.h"
#include <WiFi.h>
#include <WebSocketsClient.h>
#include "esp_camera.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include <Adafruit_MLX90640.h>

// Configuração da câmera ESP32-CAM
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22

// Configurações I2C e Sensores
#define I2C_DATA_PIN 14
#define I2C_CLOCK_PIN 15
#define I2C_CLOCK_SPEED 100000
#define I2C_TIMEOUT 1000
#define I2C_RETRY_COUNT 3
#define I2C_POWER_STABILIZE_DELAY 50
#define MAX_MLX_RETRIES 3
#define MLX_RETRY_DELAY 100

// UUIDs para serviços e características BLE
#define SENSOR_UUID             "b07d5e84-4d21-4d4a-8694-5ed9f6aa2aee"
#define SENSOR_DATA1_UUID       "89aa9a0d-48c4-4c32-9854-e3c7f44ec091"
#define SENSOR_DATA2_UUID       "a430a2ed-0a76-4418-a5ad-4964699ba17c"
#define SENSOR_DATA3_UUID       "853f9ba1-94aa-4124-92ff-5a8f576767e4"
#define CONFIG_UUID             "0a3b6985-dad6-4759-8852-dcb266d3a59e"
#define CONFIG_TIME_UUID        "ca68ebcd-a0e5-4174-896d-15ba005b668e"
#define CONFIG_ID_UUID          "eee66a40-0189-4dff-9310-b5736f86ee9c"
#define CONFIG_FREQ_UUID        "e742e008-0366-4ec2-b815-98b814112ddc"
#define CONFIG_SSID_UUID        "ab35e54e-fde4-4f83-902a-07785de547b9"
#define CONFIG_PASS_UUID        "c1c4b63b-bf3b-4e35-9077-d5426226c710"
#define CONFIG_SERVERIP_UUID    "0c954d7e-9249-456d-b949-cc079205d393"

// Constantes do sistema
#define MIN_VALID_TIMESTAMP 1600000000
#define SETTING 0
#define RUNNING_BLE 1
#define RUNNING_WIFI 2
#define THERMAL_WIDTH 32
#define THERMAL_HEIGHT 24
#define THERMAL_ARRAY_SIZE THERMAL_WIDTH * THERMAL_HEIGHT

// Variáveis globais
RTC_DS3231 rtc;
WebSocketsClient webSocket;
Adafruit_MLX90640 mlx;
BLEServer *server = nullptr;
BLECharacteristic *timeChar = nullptr;
BLECharacteristic *data1Char;
BLECharacteristic *data2Char;
BLECharacteristic *data3Char;

String sensorID = "TC";
String ssid = "";
String password = "";
char server_ip[40] = "";
uint16_t serverPort = 8080;
const char* serverPath = "/";

int mode = RUNNING_BLE;
unsigned long lastSendTime = 0;
const int sendInterval = 500;
bool isConnected = false;
int delay_millis = 500;
int frameCount = 0;
const int imageSendInterval = 1;

float frameTemp[THERMAL_ARRAY_SIZE];
float avgTemp, minTemp, maxTemp;

// Forward declarations
void print_formated_date(DateTime dt);
int setup_rtc();
void setup_ble();
bool setup_camera();
bool setup_wifi();
void setup_webSocket();
void send_data();
bool recover_i2c_bus();
bool stabilize_i2c_after_wifi();
bool isValidIP(const char* ip);

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer *server) {
        Serial.println("[BLE] Cliente conectado");
        server->getAdvertising()->stop();
    }

    void onDisconnect(BLEServer *server) {
        Serial.println("[BLE] Cliente desconectado. Reiniciando advertising.");
        server->getAdvertising()->start();
    }
};

class TimeCallback : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) override {
        String value = pChar->getValue();
        if (mode == SETTING) {
            String raw = value;
            raw.trim();
            uint32_t timestamp = strtoul(raw.c_str(), NULL, 10);
            
            if (timestamp > MIN_VALID_TIMESTAMP) {
                DateTime dt(timestamp);
                rtc.adjust(dt);
                Serial.print("[RTC] ajustado para: ");
                print_formated_date(dt);
            } else {
                Serial.println("[Erro] Timestamp inválido");
                timeChar->setValue("Error: invalid timestamp");
            }
        }
    }
};

class FreqCallback : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) override {
        String raw = pChar->getValue();
        raw.trim();
        uint32_t val = strtoul(raw.c_str(), NULL, 10);
        if (val >= 200 && val <= 2000) {
            delay_millis = val;
            Serial.printf("[System] Frequencia ajustada para: %d ms\n", val);
        } else {
            Serial.println("[Erro] Valor de frequencia inválido");
            timeChar->setValue("Error: invalid freq");
        }
    }
};

class WiFiConfigCallback : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) override {
        String value = pChar->getValue();
        String uuid = pChar->getUUID().toString();

        if (uuid == CONFIG_SSID_UUID) {
            ssid = value;
            Serial.println("[BLE] SSID: " + ssid);
        } else if (uuid == CONFIG_PASS_UUID) {
            password = value;
            Serial.println("[BLE] Password configurada");
        } else if (uuid == CONFIG_SERVERIP_UUID) {
            int colonPos = value.indexOf(':');
            if (colonPos > 0) {
                String ip = value.substring(0, colonPos);
                String port = value.substring(colonPos + 1);
                strncpy(server_ip, ip.c_str(), sizeof(server_ip) - 1);
                serverPort = port.toInt();
                Serial.printf("[BLE] Server: %s, Port: %d\n", server_ip, serverPort);
            } else {
                strncpy(server_ip, value.c_str(), sizeof(server_ip) - 1);
                Serial.printf("[BLE] Server (default port): %s:%d\n", server_ip, serverPort);
            }
        }
        mode = SETTING;
    }
};

void print_formated_date(DateTime dt) {
    Serial.printf("%04d/%02d/%02d %02d:%02d:%02d\n",
                dt.year(), dt.month(), dt.day(),
                dt.hour(), dt.minute(), dt.second());
}

bool isValidIP(const char* ip) {
    int dots = 0;
    int num = 0;

    for (int i = 0; ip[i] != '\0'; i++) {
        char c = ip[i];
        if (c == '.') {
            if (dots == 3) return false;
            dots++;
            if (num < 0 || num > 255) return false;
            num = 0;
        } else if (c >= '0' && c <= '9') {
            num = num * 10 + (c - '0');
            if (num > 255) return false;
        } else {
            return false;
        }
    }
    return (dots == 3 && num >= 0 && num <= 255);
}

bool stabilize_i2c_after_wifi() {
    Wire.end();
    delay(100);
    pinMode(I2C_DATA_PIN, INPUT_PULLUP);
    pinMode(I2C_CLOCK_PIN, INPUT_PULLUP);
    delay(50);
    Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
    Wire.setClock(50000);
    return mlx.begin();
}

void setup_ble() {
    BLEDevice::init("THERMAL_CAM-Heart_Box");
    server = BLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks());

    // Serviço de configuração
    BLEService *configService = server->createService(CONFIG_UUID);
    timeChar = configService->createCharacteristic(CONFIG_TIME_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ);
    BLECharacteristic *freqChar = configService->createCharacteristic(CONFIG_FREQ_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ);
    BLECharacteristic *ssidChar = configService->createCharacteristic(CONFIG_SSID_UUID, BLECharacteristic::PROPERTY_WRITE);
    BLECharacteristic *passChar = configService->createCharacteristic(CONFIG_PASS_UUID, BLECharacteristic::PROPERTY_WRITE);
    BLECharacteristic *ipChar = configService->createCharacteristic(CONFIG_SERVERIP_UUID, BLECharacteristic::PROPERTY_WRITE);

    timeChar->setCallbacks(new TimeCallback());
    freqChar->setCallbacks(new FreqCallback());
    
    WiFiConfigCallback* wifiCallback = new WiFiConfigCallback();
    ssidChar->setCallbacks(wifiCallback);
    passChar->setCallbacks(wifiCallback);
    ipChar->setCallbacks(wifiCallback);

    configService->start();

    // Serviço de sensor
    BLEService *sensorService = server->createService(SENSOR_UUID);
    data1Char = sensorService->createCharacteristic(SENSOR_DATA1_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    data1Char->addDescriptor(new BLE2902());
    data2Char = sensorService->createCharacteristic(SENSOR_DATA2_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    data2Char->addDescriptor(new BLE2902());
    data3Char = sensorService->createCharacteristic(SENSOR_DATA3_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    data3Char->addDescriptor(new BLE2902());

    sensorService->start();
    server->getAdvertising()->start();
    Serial.println("[BLE] Serviços iniciados");
}

bool setup_camera() {
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer = LEDC_TIMER_0;
    config.pin_d0 = Y2_GPIO_NUM;
    config.pin_d1 = Y3_GPIO_NUM;
    config.pin_d2 = Y4_GPIO_NUM;
    config.pin_d3 = Y5_GPIO_NUM;
    config.pin_d4 = Y6_GPIO_NUM;
    config.pin_d5 = Y7_GPIO_NUM;
    config.pin_d6 = Y8_GPIO_NUM;
    config.pin_d7 = Y9_GPIO_NUM;
    config.pin_xclk = XCLK_GPIO_NUM;
    config.pin_pclk = PCLK_GPIO_NUM;
    config.pin_vsync = VSYNC_GPIO_NUM;
    config.pin_href = HREF_GPIO_NUM;
    config.pin_sscb_sda = SIOD_GPIO_NUM;
    config.pin_sscb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn = PWDN_GPIO_NUM;
    config.pin_reset = RESET_GPIO_NUM;
    config.xclk_freq_hz = 20000000;
    config.pixel_format = PIXFORMAT_JPEG;
    config.frame_size = FRAMESIZE_CIF;
    config.jpeg_quality = 15;
    config.fb_count = 1;

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        Serial.printf("[CAM] Falha na inicialização da câmera com erro 0x%x\n", err);
        return false;
    }

    sensor_t * s = esp_camera_sensor_get();
    s->set_brightness(s, 0);
    s->set_contrast(s, 0);
    s->set_saturation(s, 0);
    s->set_special_effect(s, 0);
    s->set_whitebal(s, 1);
    s->set_awb_gain(s, 1);
    s->set_wb_mode(s, 0);

    return true;
}

int setup_rtc() {
    Serial.print("[RTC] Configurando RTC");
    while (!rtc.begin()) {
        Serial.print(".");
        delay(20);
    }
    Serial.println("\n[RTC] Sensor encontrado");
    Serial.print("[RTC] Data/hora atual: ");
    print_formated_date(rtc.now());
    return 1;
}

bool setup_wifi() {
    BLEDevice::deinit();
    WiFi.disconnect(true);
    WiFi.mode(WIFI_STA);
    delay(1000);

    Wire.end();
    delay(100);
    pinMode(I2C_DATA_PIN, INPUT_PULLUP);
    pinMode(I2C_CLOCK_PIN, INPUT_PULLUP);
    delay(50);
    Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
    Wire.setClock(50000);

    WiFi.begin(ssid.c_str(), password.c_str());

    int attempts = 0;
    const int max_attempts = 30;

    Serial.print("[WIFI] Conectando");
    while (WiFi.status() != WL_CONNECTED && attempts < max_attempts) {
        delay(1000);
        Serial.print(".");
        attempts++;

        if (attempts % 5 == 0) {
            stabilize_i2c_after_wifi();
        }
    }

    if (WiFi.status() == WL_CONNECTED) {
        Serial.println("\n[WIFI] Conectado!");
        Serial.print("[WIFI] IP: ");
        Serial.println(WiFi.localIP());

        if (!stabilize_i2c_after_wifi()) {
            Serial.println("[WIFI] Aviso: I2C instável após conexão WiFi");
        }
        return true;
    } else {
        Serial.println("\n[WIFI] Falha na conexão!");
        setup_ble();
        mode = RUNNING_BLE;
        return false;
    }
}

void webSocketEvent(WStype_t type, uint8_t * payload, size_t length) {
    switch(type) {
        case WStype_DISCONNECTED:
            isConnected = false;
            Serial.println("[WS] Desconectado");
            break;
        case WStype_CONNECTED:
            isConnected = true;
            Serial.println("[WS] Conectado");
            // Send identification message as JSON
            webSocket.sendTXT("{\"type\":\"CONNECTED\",\"deviceId\":\"" + sensorID + "\"}");
            break;
        case WStype_TEXT:
            if (length > 0) {
                String message((char*)payload);
                Serial.println("[WS] Recebido: " + message);
                // Handle text messages with proper JSON format
                if (message.indexOf("\"type\":\"IDENTIFY\"") >= 0) {
                    webSocket.sendTXT("{\"type\":\"CONNECTED\",\"deviceId\":\"" + sensorID + "\"}");
                }
            }
            break;
        case WStype_BIN:
            Serial.printf("[WS] Received binary length: %u\n", length);
            break;
        case WStype_ERROR:
            Serial.println("[WS] Error occurred");
            break;
        case WStype_FRAGMENT:
            Serial.println("[WS] Fragmented frame received");
            break;
    }
}

void setup_webSocket() {
    webSocket.begin(server_ip, serverPort, "/");
    webSocket.onEvent(webSocketEvent);
    webSocket.setReconnectInterval(5000);  // Try to reconnect every 5 seconds
    webSocket.enableHeartbeat(15000, 3000, 2);  // Enable heartbeat with 15s interval
    Serial.println("[WS] WebSocket server initialized");
}

void send_data() {
    if (!isConnected) return;

    DateTime now = rtc.now();
    uint32_t timestamp = now.unixtime();
    uint16_t millisec = millis() % 1000;

    // Leitura do sensor térmico
    if (mlx.getFrame(frameTemp) != 0) {
        Serial.println("[MLX] Erro na leitura do frame");
        return;
    }

    // Cálculo das temperaturas
    avgTemp = 0;
    minTemp = frameTemp[0];
    maxTemp = frameTemp[0];

    for (int i = 0; i < THERMAL_ARRAY_SIZE; i++) {
        avgTemp += frameTemp[i];
        if (frameTemp[i] < minTemp) minTemp = frameTemp[i];
        if (frameTemp[i] > maxTemp) maxTemp = frameTemp[i];
    }
    avgTemp /= THERMAL_ARRAY_SIZE;

    // Envio dos dados térmicos
    uint8_t thermalHeader = 0x02;
    size_t thermalSize = 1 + 6 + sizeof(float) * THERMAL_ARRAY_SIZE;
    uint8_t* thermalBuffer = (uint8_t*)malloc(thermalSize);
    
    if (thermalBuffer) {
        int offset = 0;
        thermalBuffer[offset++] = thermalHeader;
        memcpy(thermalBuffer + offset, &timestamp, 4);
        offset += 4;
        memcpy(thermalBuffer + offset, &millisec, 2);
        offset += 2;
        memcpy(thermalBuffer + offset, frameTemp, sizeof(float) * THERMAL_ARRAY_SIZE);
        
        webSocket.sendBIN(thermalBuffer, thermalSize);
        free(thermalBuffer);
    }

    // Envio da imagem (a cada imageSendInterval ciclos)
    if (++frameCount >= imageSendInterval) {
        frameCount = 0;
        camera_fb_t* fb = esp_camera_fb_get();
        if (fb) {
            uint8_t* imageBuffer = (uint8_t*)malloc(1 + 6 + fb->len);
            if (imageBuffer) {
                int offset = 0;
                imageBuffer[offset++] = 0x01;  // Header para imagem
                memcpy(imageBuffer + offset, &timestamp, 4);
                offset += 4;
                memcpy(imageBuffer + offset, &millisec, 2);
                offset += 2;
                memcpy(imageBuffer + offset, fb->buf, fb->len);
                
                webSocket.sendBIN(imageBuffer, 1 + 6 + fb->len);
                free(imageBuffer);
            }
            esp_camera_fb_return(fb);
        }
    }

    // Atualizar características BLE se estiver no modo BLE
    if (mode == RUNNING_BLE) {
        char tempStr[10];
        dtostrf(avgTemp, 6, 2, tempStr);
        data1Char->setValue(tempStr);
        data1Char->notify();
        
        dtostrf(maxTemp, 6, 2, tempStr);
        data2Char->setValue(tempStr);
        data2Char->notify();
        
        dtostrf(minTemp, 6, 2, tempStr);
        data3Char->setValue(tempStr);
        data3Char->notify();
    }
}

void setup() {
    Serial.begin(115200);
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

    Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
    Wire.setClock(I2C_CLOCK_SPEED);

    if (!setup_rtc()) {
        Serial.println("[ERROR] Falha ao inicializar RTC");
    }

    if (!mlx.begin()) {
        Serial.println("[ERROR] Falha ao inicializar MLX90640");
    }

    setup_ble();
}

void loop() {
    if (mode == SETTING) {
        if (ssid != "" && password != "" && server_ip[0] != '\0') {
            if (setup_camera() && setup_wifi()) {
                setup_webSocket();
                mode = RUNNING_WIFI;
            }
        }
    }
    else if (mode == RUNNING_WIFI) {
        if (WiFi.status() != WL_CONNECTED) {
            Serial.println("[WIFI] Conexão perdida, reconectando...");
            WiFi.reconnect();
            delay(500);
            return;
        }
        
        webSocket.loop();
        
        if (isConnected) {
            unsigned long currentTime = millis();
            if (currentTime - lastSendTime >= sendInterval) {
                send_data();
                lastSendTime = currentTime;
            }
        }
    }
    else if (mode == RUNNING_BLE) {
        unsigned long currentTime = millis();
        if (currentTime - lastSendTime >= delay_millis) {
            send_data();
            lastSendTime = currentTime;
        }
    }
    
    delay(10);
    yield();
}
