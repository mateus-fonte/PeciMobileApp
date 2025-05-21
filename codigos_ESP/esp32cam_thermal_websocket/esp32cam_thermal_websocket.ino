#include <Arduino.h>
#include <Wire.h>
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <WiFi.h>
#include <WebSocketsClient.h>
#include "esp_camera.h"
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"
#include <Adafruit_MLX90640.h>

// Configurações da câmera
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

// Configurações I2C
#define I2C_DATA_PIN 14
#define I2C_CLOCK_PIN 15

// UUIDs BLE
#define CONFIG_UUID          "0a3b6985-dad6-4759-8852-dcb266d3a59e"
#define CONFIG_SSID_UUID     "ab35e54e-fde4-4f83-902a-07785de547b9"
#define CONFIG_PASS_UUID     "c1c4b63b-bf3b-4e35-9077-d5426226c710"
#define CONFIG_SERVERIP_UUID "0c954d7e-9249-456d-b949-cc079205d393"

// Variáveis globais
WebSocketsClient webSocket;
Adafruit_MLX90640 mlx;
BLEServer *server = nullptr;

String ssid = "";
String password = "";
char server_ip[40] = "";
uint16_t serverPort = 8080;
const char* serverPath = "/";

bool isConnected = false;
int mode = 0; // 0=SETTING, 1=RUNNING_BLE, 2=RUNNING_WIFI
unsigned long lastSendTime = 0;
const int sendInterval = 500;

// Protótipos
bool setup_camera();
bool setup_wifi();
void setup_webSocket();
void send_data();

// Callbacks BLE
class ServerCallbacks: public BLEServerCallbacks {
    void onConnect(BLEServer *server) {
        Serial.println("[BLE] Cliente conectado");
    }
    void onDisconnect(BLEServer *server) {
        server->getAdvertising()->start();
    }
};

class WiFiConfigCallback: public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic *pChar) override {
        String value = pChar->getValue().c_str();
        if (pChar->getUUID().toString() == CONFIG_SSID_UUID) {
            ssid = value;
            Serial.println("[BLE] SSID: " + ssid);
        } else if (pChar->getUUID().toString() == CONFIG_PASS_UUID) {
            password = value;
            Serial.println("[BLE] Password set");
        } else if (pChar->getUUID().toString() == CONFIG_SERVERIP_UUID) {
            // Parse server address and port from format "ip:port"
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
        mode = 0; // SETTING
    }
};

void setup_ble() {
    BLEDevice::init("THERMAL_CAM");
    server = BLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks());

    BLEService *configService = server->createService(CONFIG_UUID);
    
    auto ssidChar = configService->createCharacteristic(CONFIG_SSID_UUID, BLECharacteristic::PROPERTY_WRITE);
    auto passChar = configService->createCharacteristic(CONFIG_PASS_UUID, BLECharacteristic::PROPERTY_WRITE);
    auto ipChar = configService->createCharacteristic(CONFIG_SERVERIP_UUID, BLECharacteristic::PROPERTY_WRITE);
    
    WiFiConfigCallback* callback = new WiFiConfigCallback();
    ssidChar->setCallbacks(callback);
    passChar->setCallbacks(callback);
    ipChar->setCallbacks(callback);
    
    configService->start();
    server->getAdvertising()->start();
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

    return esp_camera_init(&config) == ESP_OK;
}

bool setup_wifi() {
    BLEDevice::deinit();
    delay(500);
    
    WiFi.disconnect(true);
    WiFi.mode(WIFI_STA);
    
    // Configurar WiFi para reconectar automaticamente em caso de perda de conexão
    WiFi.setAutoReconnect(true);
    WiFi.persistent(true);
    
    WiFi.begin(ssid.c_str(), password.c_str());
    
    int attempts = 0;
    Serial.print("[WIFI] Connecting to: " + String(ssid));
    while (WiFi.status() != WL_CONNECTED && attempts < 60) { // Aumentado para 30 segundos
        delay(500);
        Serial.print(".");
        attempts++;
        yield(); // Allow system to handle background tasks
    }
    Serial.println();
    
    if (WiFi.status() == WL_CONNECTED) {
        Serial.printf("[WIFI] Connected! IP: %s\n", WiFi.localIP().toString().c_str());
        
        // Aguardar um momento para estabilizar a conexão antes de tentar o WebSocket
        delay(2000);
        return true;
    }
    
    Serial.println("[WIFI] Connection failed!");
    return false;
}

void webSocketEvent(WStype_t type, uint8_t * payload, size_t length) {
    if (type == WStype_DISCONNECTED) {
        isConnected = false;
        Serial.println("[WS] Desconectado");
    }
    else if (type == WStype_CONNECTED) {
        isConnected = true;
        Serial.println("[WS] Conectado");
    }
}

void setup_webSocket() {
    webSocket.onEvent(webSocketEvent);
    webSocket.setReconnectInterval(2000);
    webSocket.enableHeartbeat(5000, 2000, 2);
    
    // Begin WebSocket connection with parsed IP and port
    webSocket.begin(server_ip, serverPort, serverPath);
    Serial.printf("[WS] Connecting to: %s:%d%s\n", server_ip, serverPort, serverPath);
}

void send_data() {
    if (!isConnected) return;
    
    // Capturar imagem
    camera_fb_t* fb = esp_camera_fb_get();
    if (!fb) return;

    // Enviar imagem: [1 byte header][dados]
    uint8_t* buffer = (uint8_t*)malloc(1 + fb->len);
    if (buffer) {
        buffer[0] = 0x01; // header para imagem
        memcpy(buffer + 1, fb->buf, fb->len);
        webSocket.sendBIN(buffer, 1 + fb->len);
        free(buffer);
    }
    
    esp_camera_fb_return(fb);
}

void setup() {
    Serial.begin(115200);
    WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
    
    Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
    mlx.begin();
    setup_ble();
}

void loop() {
    if (mode == 0) { // SETTING
        if (ssid != "" && password != "" && server_ip[0] != '\0') {
            if (setup_camera() && setup_wifi()) {
                setup_webSocket();
                mode = 2; // RUNNING_WIFI
            }
        }
    }
    else if (mode == 2) { // RUNNING_WIFI
        // Verificar conexão WiFi e tentar reconectar se necessário
        if (WiFi.status() != WL_CONNECTED) {
            Serial.println("[WIFI] Connection lost, reconnecting...");
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
          // Permitir que o sistema processe outras tarefas
        yield();
    }
    
    delay(10);
}
