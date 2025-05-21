/**
 * ESP32-CAM com MLX90640 e RTC DS3231
 * Conexões:
 * - MLX90640 e DS3231: Conectados via I2C nos pinos definidos (14/15)
 * - Câmera: Conectada às portas padrão do ESP32-CAM
 * * Formato:
 * - Timestamp:
 * 4 bytes: timestamp em formato epoch (uint_32) extraído do RTC
 * 2 bytes: millis()%1000 (uint16_t) formatado sempre com 3 caracteres (se for 1 é formatado como 001)
 * 
 * - Mensagem foto:
 * Foto em formato JPEG enviada diretamente como dados binários
 * (Tamanho da mensagem varia conforme a resolução e qualidade da imagem, geralmente será maior)
 *
 * - Mensagem matriz térmica:
 * Matriz 32x24 de floats em binário (3072 bytes = 768 pixels * 4 bytes por float)
 * O servidor identifica o tipo da mensagem pelo seu tamanho
 **/

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
#include "soc/soc.h"           // Sistemas ESP32 necessários
#include "soc/rtc_cntl_reg.h"  // Desativa o detector de brownout
#include <Adafruit_MLX90640.h> // biblioteca do sensor aqui


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
//pinos do sensor aqui
#define I2C_DATA_PIN 14
#define I2C_CLOCK_PIN 15
//UUIDs para serviços e características BLE DO SENSOR aqui
#define SENSOR_UUID             "b07d5e84-4d21-4d4a-8694-5ed9f6aa2aee" //serviço sensor
#define SENSOR_DATA1_UUID       "89aa9a0d-48c4-4c32-9854-e3c7f44ec091" //caracteristica notify (avg_temp)
#define SENSOR_DATA2_UUID       "a430a2ed-0a76-4418-a5ad-4964699ba17c" //caracteristica notify (max_temp)
#define SENSOR_DATA3_UUID       "853f9ba1-94aa-4124-92ff-5a8f576767e4" //caracteristica notify (min_temp)
//Ponteiros para as CARACTERÍSTICAS DO SENSOR aqui
BLECharacteristic *data1Char;
BLECharacteristic *data2Char;
BLECharacteristic *data3Char;
// uuids das CARACTERÍSTICAS DE CONFIGURAÇÃO
#define CONFIG_UUID             "0a3b6985-dad6-4759-8852-dcb266d3a59e" //serviço config
#define CONFIG_TIME_UUID        "ca68ebcd-a0e5-4174-896d-15ba005b668e" //característica read/write
#define CONFIG_ID_UUID          "eee66a40-0189-4dff-9310-b5736f86ee9c" //característica read/write
#define CONFIG_FREQ_UUID        "e742e008-0366-4ec2-b815-98b814112ddc" //característica read/write
// configuração do wifi
#define CONFIG_SSID_UUID        "ab35e54e-fde4-4f83-902a-07785de547b9" //característica write
#define CONFIG_PASS_UUID        "c1c4b63b-bf3b-4e35-9077-d5426226c710" //característica write
#define CONFIG_SERVERIP_UUID    "0c954d7e-9249-456d-b949-cc079205d393" //característica write
BLEServer *server = nullptr;              // Ponteiro para o servidor BLE
BLECharacteristic *timeChar  = nullptr;   // Ponteiro para a caracteristica da config da hora
#define MIN_VALID_TIMESTAMP 1600000000
#define SETTING 0
#define RUNNING_BLE 1
#define RUNNING_WIFI 2
// Constantes para a matriz térmica
#define THERMAL_WIDTH 32
#define THERMAL_HEIGHT 24
#define THERMAL_ARRAY_SIZE THERMAL_WIDTH * THERMAL_HEIGHT

RTC_DS3231 rtc;             //objeto RTC
WebSocketsClient webSocket; // Cliente WebSocket
Adafruit_MLX90640 mlx;      //objeto da câmera térmica
int delay_millis = 250;   //delay em milissegundo entre loops (250MS => 4Hz)
int mode = RUNNING_BLE;   //inicializa no modo ble
String sensorID = "TC";   //id da camera térmica é "TC"

String ssid = "";
String password = "";
char server_ip[40] = "";            //como de tivesse server_ip[0] = '\0'
uint16_t serverPort = 8080;         // Porta hardcoded
const char* serverPath = "/";       // Caminho do WebSocket (geralmente "/" é suficiente)

// Variáveis de controle
unsigned long lastSendTime = 0;      // Última vez que dados foram enviados
const int sendInterval = 500;        // Intervalo de envio em ms (2Hz = 500ms)
bool isConnected = false;            // Status da conexão WebSocket

// Variáveis para reconexão
unsigned long lastReconnectAttempt = 0;
int reconnectInterval = 5000;  // Tenta reconectar a cada 5 segundos (não constante para permitir backoff)
float frameTemp[32*24]; // Buffer para o frame completo de temperaturas
float avgTemp, minTemp, maxTemp;


void print_formated_date(DateTime dt);
int setup_rtc();
void setup_ble();
int setup_sensor();
bool setup_camera();
bool setup_wifi();
int get_sensor_data();
int running_ble();
bool isValidIP(const char* ip);
void setup_webSocket();
void send_data();

/***Classes para funções de callbacks***/
// Classe para gerenciar o servidor BLE
class serverCallbacks : public BLEServerCallbacks {
  void onConnect(BLEServer *server) {
    Serial.println("[BLE] Cliente conectado");
    server->getAdvertising()->stop();  // Para o advertising para evitar novas conexões
    Serial.println("[BLE] Advertising parado.");
  }

  void onDisconnect(BLEServer *server) { // Caso disconecte
    Serial.println("[BLE] Cliente desconectado. Reiniciando advertising.");
    server->getAdvertising()->start();   // Reinicia o advertising para permitir reconexões
  }
};
// Callback para tratar escritas nas características de configuração
class TimeCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String value = pChar->getValue();
    Serial.flush();  // Garantir que a saída seja completada
    Serial.print("[BLE] Novo valor: ");
    Serial.println(value);

    if (mode == SETTING) {
      String raw = value;
      raw.trim();  // Remove \r, \n, espaços extras
      uint32_t timestamp = strtoul(raw.c_str(), NULL, 10);  // Converte para uint32_t

      // Exibe o timestamp recebido
      Serial.print("[BLE] Timestamp Unix recebido: ");
      Serial.println(timestamp);

      // Verifica se o timestamp é válido
      if (timestamp > MIN_VALID_TIMESTAMP) {
        // Se for válido, ajusta o RTC
        DateTime dt(timestamp);
        rtc.adjust(dt);
        Serial.print("[RTC] ajustado para: ");
        print_formated_date(dt);

      } else {
        // Se o timestamp for inválido, não ajusta o RTC
        Serial.println("[Erro] Timestamp inválido, não ajustando RTC.");

        // Define um valor de erro na característica
        String errorMsg = "Error: invalid timestamp";
        timeChar->setValue(errorMsg.c_str());
      }
      Serial.flush();
    }
  }
};
class IdCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String value = pChar->getValue();
    Serial.print("[BLE] Id recebido: ");
    Serial.println(value);
    Serial.flush();
  }
};
class FreqCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String value = pChar->getValue();
    Serial.print("[BLE] Freq recebido: ");
    Serial.println(value);
    Serial.flush();
  }
};
class SSIDCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String value = pChar->getValue();
    Serial.println("[BLE] setting ssid");
    ssid = value; //"testwifi";
    Serial.print("[BLE] SSID atualizado para: ");
    Serial.println(ssid);
    Serial.flush();
    mode = SETTING; // se escrever aqui vamos assumir que quer passar para wifi
  }
};

class PassCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String value = pChar->getValue();
    Serial.println("[BLE] setting password");
    password = value; //"87654321";
    Serial.print("[BLE] Senha atualizada para: ");
    Serial.println(password);
    Serial.flush();
    mode = SETTING; // se escrever aqui vamos assumir que quer passar para wifi
  }
};
class ServerIpCallback : public BLECharacteristicCallbacks {
  void onWrite(BLECharacteristic *pChar) override {
    String value = pChar->getValue();
    String ip = "";
    String port = "";
    int colonPos = value.indexOf(':');
    
    if (colonPos != -1) {
      ip = value.substring(0, colonPos);
      port = value.substring(colonPos + 1);
      serverPort = port.toInt();
      strncpy(server_ip, ip.c_str(), sizeof(server_ip) - 1);
      server_ip[sizeof(server_ip) - 1] = '\0';
    } else {
      // Se não houver ':', assume que é apenas IP e usa porta padrão
      strncpy(server_ip, value.c_str(), sizeof(server_ip) - 1);
      server_ip[sizeof(server_ip) - 1] = '\0';
    }

    if (isValidIP(server_ip) && serverPort > 0) {
      Serial.print("[BLE] Servidor websocket atualizado para: ");
      Serial.print(server_ip);
      Serial.print(":");
      Serial.println(serverPort);
      mode = SETTING;
    } else {
      Serial.println("[BLE] Endereço inválido recebido");
    }

    Serial.flush();
  }
};

// função utilitária
void print_formated_date(DateTime dt) {
  // Imprime a data e hora formatada
  Serial.printf("%04d/%02d/%02d %02d:%02d:%02d\n",
                dt.year(), dt.month(), dt.day(),
                dt.hour(), dt.minute(), dt.second());
  Serial.flush();
}
// função utilitária
bool isValidIP(const char* ip) {
  int dots = 0;
  int num = 0;

  for (int i = 0; ip[i] != '\0'; i++) {
    char c = ip[i];

    if (c == '.') {
      if (dots == 3) return false; // já tem 3 pontos, não pode mais
      dots++;
      if (num < 0 || num > 255) return false;
      num = 0;
    } else if (c >= '0' && c <= '9') {
      num = num * 10 + (c - '0');
      if (num > 255) return false;
    } else {
      return false; // caractere inválido
    }
  }

  return (dots == 3 && num >= 0 && num <= 255);
}

bool setup_camera() {
  // Configuração da câmera
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
  
  // Configuração da qualidade JPEG e tamanho do buffer
  /*Serial.println("[CAM] Usando configuração de alta qualidade");
  config.frame_size = FRAMESIZE_VGA; // 640x480
  config.jpeg_quality = 10; // 0-63 (menor é melhor)
  config.fb_count = 2;
  */  Serial.println("[CAM] Usando configuração de baixa qualidade");
  config.frame_size = FRAMESIZE_CIF; // 400x296 - menor que SVGA
  config.jpeg_quality = 20; // 0-63 (maior número = menor qualidade)
  config.fb_count = 1;
  
  // Inicializar a câmera
  esp_err_t err = esp_camera_init(&config);
  if (err != ESP_OK) {
    Serial.printf("[CAM] Falha na inicialização da câmera com erro 0x%x", err);
    return false;
  }
  
  // Ajustar configurações de imagem se necessário
  sensor_t * s = esp_camera_sensor_get();
  s->set_brightness(s, 0);     // -2 a 2
  s->set_contrast(s, 0);       // -2 a 2
  s->set_saturation(s, 0);     // -2 a 2
  s->set_special_effect(s, 0); // 0 a 6 (0 - Sem efeito, 1 - Negativo...)
  s->set_whitebal(s, 1);       // 0 - Desativado, 1 - Ativado
  s->set_awb_gain(s, 1);       // 0 - Desativado, 1 - Ativado
  s->set_wb_mode(s, 0);        // 0 a 4 - Modos de balanço de branco
  
  return true;
}

int setup_rtc() {
  // Inicializa o RTC
  Serial.print("[RTC] configuring rtc");
  while(!rtc.begin()) {
    Serial.print(".");
    delay(20);
  }
  Serial.println("[RTC] sensor found");
  Serial.flush();  //Garantir que a saída seja completada
  Serial.print("[RTC] Current date/time is: ");
  print_formated_date(rtc.now());
  return 1;
}

// Função para inicializar BLE e serviços de configuração
void setup_ble() {
  BLEDevice::init("THERMAL_CAM");
  server = BLEDevice::createServer();

  Serial.println("[BLE] device created sucessfully");

  // Serviço de configuração BLE
  BLEService *configService = server->createService(CONFIG_UUID);
  timeChar = configService->createCharacteristic(CONFIG_TIME_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ);
  BLECharacteristic *idChar   = configService->createCharacteristic(CONFIG_ID_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ);
  BLECharacteristic *freqChar   = configService->createCharacteristic(CONFIG_FREQ_UUID, BLECharacteristic::PROPERTY_WRITE | BLECharacteristic::PROPERTY_READ);
  BLECharacteristic *ssidChar   = configService->createCharacteristic(CONFIG_SSID_UUID, BLECharacteristic::PROPERTY_WRITE);
  BLECharacteristic *passChar   = configService->createCharacteristic(CONFIG_PASS_UUID, BLECharacteristic::PROPERTY_WRITE);
  BLECharacteristic *ipChar   = configService->createCharacteristic(CONFIG_SERVERIP_UUID, BLECharacteristic::PROPERTY_WRITE);

  // Vincula os callbacks de escrita
  timeChar->setCallbacks(new TimeCallback());
  idChar->setCallbacks(new IdCallback());
  freqChar->setCallbacks(new FreqCallback());
  ssidChar->setCallbacks(new SSIDCallback());
  passChar->setCallbacks(new PassCallback());
  ipChar->setCallbacks(new ServerIpCallback());
  // Vincula callbacks do servidor (gere conexões)
  server->setCallbacks(new serverCallbacks());

  configService->start();
  BLEService *sensorService = server->createService(SENSOR_UUID);

  /*Serviço para envio dos dados do sensor aqui*/
  data1Char = sensorService->createCharacteristic(SENSOR_DATA1_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  data1Char->addDescriptor(new BLE2902());
  data2Char = sensorService->createCharacteristic(SENSOR_DATA2_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  data2Char->addDescriptor(new BLE2902());
  data3Char = sensorService->createCharacteristic(SENSOR_DATA3_UUID, BLECharacteristic::PROPERTY_NOTIFY);
  data3Char->addDescriptor(new BLE2902());

  sensorService->start();

  server->getAdvertising()->start(); //começa a fazer advertising dos serviços que tem
  Serial.println("[BLE] Serviços CONFIG BLE iniciados.");
}


bool setup_wifi() {
  
  //IMPORTANTE DESATIVAR O BLE PRIMEIRO
  BLEDevice::deinit(); // Desliga o módulo BLE

  Serial.println("[WIFI] Conectando à rede: " + String(ssid));
  
  // Passo 1: Desativar WiFi, resetar configurações e então iniciar conexão
  WiFi.disconnect(true);  // Desconecta e esquece credenciais salvas
  WiFi.mode(WIFI_STA);    // Configura como estação (não como AP)
  delay(1000);            // Pequena pausa para estabilização
    // Iniciar conexão
  WiFi.begin(ssid.c_str(), password.c_str());
  
  // Aguardar conexão com mais tentativas (30 segundos no total)
  int attempts = 0;
  
  const int max_attempts = 30; // 30 tentativas de 1 segundo cada
  
  Serial.print("[WIFI] Tentando conectar");
  while (WiFi.status() != WL_CONNECTED && attempts < max_attempts) {
    delay(1000);
    Serial.print(".");
    attempts++;
  }
  
  if (WiFi.status() == WL_CONNECTED) {
    Serial.println();
    Serial.println("[WIFI] Conectado!");
    Serial.print("[WIFI] Endereço IP: ");
    Serial.println(WiFi.localIP());
    return true;
  } else {
    Serial.println();
    Serial.println("[WIFI] FALHA NA CONEXÃO! Verifique o SSID e senha.");
    setup_ble();
    mode = RUNNING_BLE;
    return false;
  }
}

void setup_webSocket() {
  // Verificar novamente se o WiFi está conectado antes de tentar a conexão WebSocket
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WEBSOCKET] ERRO: WiFi não está conectado. Não é possível conectar ao WebSocket.");
    return;
  }
  
  Serial.println("[WEBSOCKET] Conectando ao servidor WebSocket: " + 
                String(server_ip) + ":" + String(serverPort) + String(serverPath));
  
  // Configurar callback de eventos
  webSocket.onEvent(webSocketEvent);
  
  // Conectar ao servidor
  webSocket.begin(server_ip, serverPort, serverPath);
  
  // Usar protocolo genérico
  webSocket.setReconnectInterval(5000);
  
  Serial.println("[WEBSOCKET] Esperando conexão...");
}

void webSocketEvent(WStype_t type, uint8_t * payload, size_t length) {
  switch(type) {
    case WStype_DISCONNECTED:
      isConnected = false;
      Serial.println("[WEBSOCKET] Desconectado!");
      break;
      
    case WStype_CONNECTED:
      isConnected = true;
      Serial.println("[WEBSOCKET] Conectado ao servidor!");
      // Enviar uma mensagem para notificar o servidor sobre a conexão
      webSocket.sendTXT("ESP32-CAM Térmico Conectado!");
      break;
      
    case WStype_TEXT:
      Serial.printf("[WEBSOCKET] Mensagem recebida: %s\n", payload);
      
      // Processar mensagens de confirmação do servidor
      if (strcmp((char*)payload, "CONNECTED") == 0) {
        Serial.println("[WEBSOCKET] Conexão confirmada pelo servidor!");
        isConnected = true;
      } 
      else if (strcmp((char*)payload, "RECEIVED_TEXT") == 0) {
        Serial.println("[WEBSOCKET] Servidor confirmou recebimento de texto");
      }
      else if (strcmp((char*)payload, "RECEIVED_BINARY") == 0) {
        Serial.println("[WEBSOCKET] Servidor confirmou recebimento de dados binários");
      }
      break;
      
    case WStype_BIN:
      Serial.printf("[WEBSOCKET] Dados binários recebidos: %u bytes\n", length);
      break;
      
    case WStype_ERROR:
      Serial.println("[WEBSOCKET] Erro na conexão!");
      isConnected = false;
      break;
      
    case WStype_FRAGMENT_TEXT_START:
    case WStype_FRAGMENT_BIN_START:
    case WStype_FRAGMENT:
    case WStype_FRAGMENT_FIN:
      // Geralmente não precisamos tratar esses eventos
      break;
  }
}

void send_data() {
  if (!isConnected) {
    Serial.println("[WEBSOCKET] Não conectado - não enviando dados");
    return;
  }
    // 1. Capturar e enviar imagem da câmera
  Serial.println("[CAM] Capturando imagem...");
  camera_fb_t * fb = esp_camera_fb_get();
  if (!fb) {
    Serial.println("[CAM] ERRO: Falha ao capturar imagem!");
    return;
  }
  // Enviar os dados da imagem diretamente (sem cabeçalho separado)
  webSocket.sendBIN(fb->buf, fb->len);
  
  Serial.printf("[WEBSOCKET] Imagem enviada: %u bytes\n", (unsigned int)(fb->len));
  
  // Liberar o buffer da câmera imediatamente após o uso
  esp_camera_fb_return(fb);

  // 2. Capturar e enviar dados térmicos
  Serial.println("[MLX] Capturando frame térmico...");
  if (mlx.getFrame(frameTemp) != 0) {
    Serial.println("[MLX] ERRO: Falha ao ler o frame térmico!");
    return;
  }
  // Enviar dados térmicos diretamente (sem cabeçalho separado)
  webSocket.sendBIN((uint8_t*)frameTemp, sizeof(frameTemp));
  Serial.printf("[WEBSOCKET] Dados térmicos enviados: %u bytes\n", (unsigned int)(sizeof(frameTemp)));

  // Calcular estatísticas térmicas para uso interno/debug
  float sumTemp = 0;
  minTemp = frameTemp[0];
  maxTemp = frameTemp[0];

  for (int i = 0; i < 768; i++) {
    float temp = frameTemp[i];
    sumTemp += temp;
    if (temp < minTemp) minTemp = temp;
    if (temp > maxTemp) maxTemp = temp;
  }

  avgTemp = sumTemp / 768;
}

//----------INSERIR CÓDIGO CONFIGURAÇÃO DO SENSOR AQUI------------
int setup_sensor() {
  Wire.setClock(50000); // Velocidade reduzida para melhorar estabilidade com resistores pull-up de 1kΩ
  
  // Inicializa o MLX90640
  if (!mlx.begin(MLX90640_I2CADDR_DEFAULT, &Wire)) {
    Serial.println("MLX90640 não encontrado!");
    while (1) delay(10); //trava
  }
  
  mlx.setMode(MLX90640_CHESS);
  mlx.setResolution(MLX90640_ADC_18BIT);
  mlx.setRefreshRate(MLX90640_4_HZ);
  return 1;
}

//----------INSERIR CÓDIGO CAPTURAR DADOS DO SENSOR AQUI------------
int get_sensor_data() {
  if (mlx.getFrame(frameTemp) != 0) {
    Serial.println("Falha ao ler o frame");
    return 0;
  }

  float sumTemp = 0;
  minTemp = frameTemp[0];
  maxTemp = frameTemp[0];
  
  // Calcular a temperatura média, mínima e máxima
  for (uint8_t h = 0; h < 24; h++) {
    for (uint8_t w = 0; w < 32; w++) {
      float t = frameTemp[h*32 + w];
      sumTemp += t;
      
      if (t < minTemp) minTemp = t;
      if (t > maxTemp) maxTemp = t;
    }
  }
  
  avgTemp = sumTemp / (32 * 24); // Temperatura média
  
  // Imprime os resultados no monitor serial
  Serial.print("Temperatura média: ");
  Serial.println(avgTemp);
  Serial.print("Temperatura mínima: ");
  Serial.println(minTemp);
  Serial.print("Temperatura máxima: ");
  Serial.println(maxTemp);

  return 1;
}

//-----------SETUP e LOOP---------------------------//
void setup() {
  Serial.begin(115200);
  
  // Desativar o detector de brownout para evitar resets
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
  
  delay(50);
  Serial.println("inicio");

  Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
  Serial.printf("Wire started at SDA=%d, SCL=%d\n", I2C_DATA_PIN, I2C_CLOCK_PIN);

  if (setup_rtc()) {
    Serial.println("rtc ok");
  };

  if (setup_sensor()) {
    Serial.println("sensor ok");
  };

  setup_ble();
}


int running_ble() {
  int success = get_sensor_data();
  if (success) { //conseguiu fazer avgTemp, nimTemp, maxTemp
    uint32_t timestamp = rtc.now().unixtime();
    String ts = String(timestamp)+String(millis()%1000);
    String payload;

    Serial.println("[DEBUG] BLE notifications sent: ");

    // mudar o payload partir a depender do sensor aqui
    payload = sensorID + ts + '.' + String((int)(avgTemp*100));
    Serial.println(payload);
    data1Char->setValue(payload.c_str());
    data1Char->notify();

    payload = sensorID + ts + '.' + String((int)(maxTemp*100));
    Serial.println(payload);
    data2Char->setValue(payload.c_str());
    data2Char->notify();

    payload = sensorID + ts + '.' + String((int)(minTemp*100));
    Serial.println(payload);
    data3Char->setValue(payload.c_str());
    data3Char->notify();
    return 1;
  }
  else {
    Serial.println("[DEBUG] unnable to get sensor data");
    return 0;
  }
}

void running_wifi() {
  // Verificar se o WiFi ainda está conectado
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("[WIFI] A conexão WiFi foi perdida! Tentando reconectar...");
    if (setup_wifi()) {
      setup_webSocket();  // Reconecta o WebSocket se o WiFi reconectar
      lastReconnectAttempt = millis();
      reconnectInterval = 1000; // Reinicia o intervalo de backoff
    } else {
      // Se a reconexão WiFi falhar, aguarde antes de tentar novamente
      delay(5000);
      return;  // Sai da função loop e tenta novamente no próximo ciclo
    }
  }
  
  // Manter a conexão WebSocket ativa
  webSocket.loop();
    // Verificar se está conectado ao WebSocket
  if (isConnected) {    
    // Enviar dados no intervalo definido (2 Hz = 500ms)
    if (millis() - lastSendTime > sendInterval) {
      send_data();
      lastSendTime = millis();
    }
  } else {
    // Somente tenta reconectar o WebSocket se o WiFi estiver conectado
    // e se passou tempo suficiente desde a última tentativa
    unsigned long currentTime = millis();
    if (WiFi.status() == WL_CONNECTED && 
        currentTime - lastReconnectAttempt > reconnectInterval) {
      
      Serial.println("[WEBSOCKET] Tentando reconectar...");
      
      webSocket.disconnect();
      delay(100);
      setup_webSocket();
      
      // Atualizar o timestamp da última tentativa
      lastReconnectAttempt = currentTime;
    }
  }
  
  // Pequena pausa para evitar sobrecarga da CPU
  delay(10);
}


void loop() {
  Serial.print("[DEBUG] running_mode: ");
  Serial.println(mode);

  if(mode == RUNNING_BLE) {
    running_ble();
  }
  if(mode == RUNNING_WIFI) {
    running_wifi();
  }
  if(mode == SETTING) {
    if(ssid != "" && password != "" && server_ip[0] != '\0') {
      //espera até todas as características terem sido preenchidas
      setup_camera();
      setup_wifi(); //uma vez preeenchidas faz o set up do wifi (que desliga o bluetooth)
      setup_webSocket();
      mode = RUNNING_WIFI; //e quando estiver terminado o setup passamos para modo mqtt
    }
  }
  delay(delay_millis);
}