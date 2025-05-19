/**
 * ESP32-CAM com MLX90640 e RTC DS3231
 * Conexões:
 * - MLX90640 e DS3231: Conectados via I2C nos pinos definidos (14/15)
 * - Câmera: Conectada às portas padrão do ESP32-CAM
 *
 * Formato:
 * - Timestamp:
 * 4 bytes: timestamp em formato epoch (uint_32) extraído do RTC
 * 2 bytes: millis()%1000 (uint16_t) formatado sempre com 3 caracteres (se for 1 é formatado como 001)
 * 
 * - Mensagem foto:
 * 1º campo (1 byte): 0x01
 * 2º campo (6 bytes): timestamp usando o formato mencionado 
 * 3º campo: foto em binário
 *
 * - Mensagem matriz térmica:
 * 1º campo (1 byte): 0x02
 * 2º campo (6 bytes): timestamp usando o formato mencionado
 * 3º campo: matriz 32x24 de floats em binário
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
 #define PCLK_GPIO_NUM     22 //pinos do sensor aqui
 #define I2C_DATA_PIN 14
 #define I2C_CLOCK_PIN 15
 #define I2C_CLOCK_SPEED 100000  // 100kHz
 #define I2C_TIMEOUT 1000        // 1 second timeout
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
 
 // Definições e variáveis globais
 #define I2C_RETRY_COUNT 3
 #define I2C_POWER_STABILIZE_DELAY 50
 #define MAX_MLX_RETRIES 3
 #define MLX_RETRY_DELAY 100

 RTC_DS3231 rtc;             // objeto RTC
 WebSocketsClient webSocket; // Cliente WebSocket
 Adafruit_MLX90640 mlx;      // objeto da câmera térmica
 String sensorID = "TC";     // id da camera térmica é "TC"
 
 // Strings de conexão WiFi
 String ssid = "";
 String password = "";
 char server_ip[40] = "";            // como se tivesse server_ip[0] = '\0'
 uint16_t serverPort = 8080;         // Porta hardcoded
 const char* serverPath = "/";       // Caminho do WebSocket
 
 // Variáveis de controle
 int mode = RUNNING_BLE;              // inicializa no modo ble
 unsigned long lastSendTime = 0;     // Última vez que dados foram enviados
 const int sendInterval = 500;       // Intervalo de envio em ms (2Hz = 500ms)
 bool isConnected = false;           // Status da conexão WebSocket
 int delay_millis = 500;             // Delay entre leituras do sensor
 
 // Variáveis de reconexão com backoff exponencial
 unsigned long lastReconnectAttempt = 0;
 int reconnectInterval = 1000;       // Começa tentando reconectar a cada 1 segundo
 const int maxReconnectInterval = 30000; // No máximo a cada 30 segundos
 
 // Variáveis para controle de envio de frames da câmera
 int frameCount = 0;
 const int imageSendInterval = 1;    // Envia imagem a cada 3 ciclos térmicos
 
 // Armazenamento para dados térmicos
 float frameTemp[32*24];            // Buffer para o frame completo de temperaturas
 float avgTemp, minTemp, maxTemp;
 
 
 // Forward declarations and setup code
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
 void webSocketEvent(WStype_t type, uint8_t* payload, size_t length);
 bool recover_i2c_bus();
 bool stabilize_i2c_after_wifi();
 
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
     
     // Process the value to extract IP and port if in format "ip:port"
     int colonPos = value.indexOf(':');
     if (colonPos > 0 && colonPos < value.length() - 1) {
       // String contains port information
       String ipPart = value.substring(0, colonPos);
       String portPart = value.substring(colonPos + 1);
       
       // Store IP
       strncpy(server_ip, ipPart.c_str(), sizeof(server_ip) - 1);
       server_ip[sizeof(server_ip) - 1] = '\0';
       
       // Parse and store port
       serverPort = portPart.toInt();
       
       if (isValidIP(server_ip) && serverPort > 0) {
         Serial.print("[BLE] IP do servidor websocket atualizado para: ");
         Serial.println(server_ip);
         Serial.print("[BLE] Porta do servidor websocket atualizada para: ");
         Serial.println(serverPort);
         mode = SETTING;
       } else {
         Serial.print("[BLE] IP ou porta inválidos recebidos: ");
         Serial.print(server_ip);
         Serial.print(":");
         Serial.println(serverPort);
       }
     } else {
       // Format doesn't include port, use only IP with default port
       strncpy(server_ip, value.c_str(), sizeof(server_ip) - 1);
       server_ip[sizeof(server_ip) - 1] = '\0';
       
       if (isValidIP(server_ip)) {
         Serial.print("[BLE] IP do servidor websocket atualizado para: ");
         Serial.println(server_ip);
         Serial.println("[BLE] Usando porta padrão: 8080");
         mode = SETTING;
       } else {
         Serial.print("[BLE] IP inválido recebido: ");
         Serial.println(server_ip);
       }
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
   */
   Serial.println("[CAM] Usando configuração de baixa qualidade");
   config.frame_size = FRAMESIZE_CIF; // 400x296, menor que SVGA
   config.jpeg_quality = 15; // Um pouco pior, mas economiza memória
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
   BLEDevice::init("THERMAL_CAM-Heart_Box");
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

   // Prepare I2C bus for WiFi operation
   Wire.end();
   delay(100);
   pinMode(I2C_DATA_PIN, INPUT_PULLUP);
   pinMode(I2C_CLOCK_PIN, INPUT_PULLUP);
   delay(50);
   Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
   Wire.setClock(50000); // Slower I2C speed in WiFi mode
   
   // Iniciar conexão
   WiFi.begin(ssid, password);
   //WiFi.begin("testwifi", "87654321");
   
   // Aguardar conexão com mais tentativas (30 segundos no total)
   int attempts = 0;
   const int max_attempts = 30; // 30 tentativas de 1 segundo cada
   
   Serial.print("[WIFI] Tentando conectar");
   while (WiFi.status() != WL_CONNECTED && attempts < max_attempts) {
     delay(1000);
     Serial.print(".");
     attempts++;

     // Check I2C stability periodically during connection
     if (attempts % 5 == 0) {
       stabilize_i2c_after_wifi();
     }
   }
   
   if (WiFi.status() == WL_CONNECTED) {
     Serial.println();
     Serial.println("[WIFI] Conectado!");
     Serial.print("[WIFI] Endereço IP: ");
     Serial.println(WiFi.localIP());

     // Final I2C check after WiFi connection
     if(!stabilize_i2c_after_wifi()) {
       Serial.println("[WIFI] Warning: I2C bus unstable after WiFi connection");
     }
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
 
// Function to send sensor data over WebSocket
void send_data() {
  if (!isConnected) {
    Serial.println("[WEBSOCKET] Não conectado - não enviando dados");
    return;
  }
   
  Serial.println("\n[DATA] Iniciando captura e envio de dados...");
   
  // 1. Obter timestamp do RTC DS3231 (usado para todas as mensagens)
  DateTime now = rtc.now();
  uint32_t epochTime = now.unixtime(); // 4 bytes do timestamp epoch
  uint16_t millisPart = millis() % 1000; // 2 bytes para os milissegundos
   
  // 2. Capturar dados do sensor térmico (sempre)
  Serial.println("[MLX] Capturando frame térmico...");
  static bool sensorError = false;
  static unsigned long lastErrorTime = 0;
  static int errorCount = 0;
   
  if (mlx.getFrame(frameTemp) != 0) {
    errorCount++;
    sensorError = true;
    lastErrorTime = millis();
    Serial.println("[MLX] ERRO: Falha ao ler o frame térmico! Tentando recuperar...");
     
    // Enviar dados de erro para o cliente
    float errorTemp = -999.0;  // Valor especial para indicar erro
    minTemp = errorTemp;
    maxTemp = errorTemp;
    avgTemp = errorTemp;
     
    // A cada 3 erros, tenta uma recuperação mais agressiva
    if (errorCount >= 3) {
      Wire.end();
      delay(100);
      pinMode(I2C_DATA_PIN, INPUT_PULLUP);
      pinMode(I2C_CLOCK_PIN, INPUT_PULLUP);
      delay(50);
      Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
      Wire.setClock(100000);
       
      // Tenta reinicializar o sensor
      if (mlx.begin(MLX90640_I2CADDR_DEFAULT, &Wire)) {
        mlx.setMode(MLX90640_CHESS);
        mlx.setResolution(MLX90640_ADC_18BIT);
        mlx.setRefreshRate(MLX90640_2_HZ);
        errorCount = 0;
      }
    }
  } else {
    sensorError = false;
    errorCount = 0;
     
    // Calcular estatísticas térmicas
    float sumTemp = 0;
    minTemp = frameTemp[0];
    maxTemp = frameTemp[0];
   
    for (uint8_t h = 0; h < THERMAL_HEIGHT; h++) {
      for (uint8_t w = 0; w < THERMAL_WIDTH; w++) {
        float t = frameTemp[h * THERMAL_WIDTH + w];
        sumTemp += t;
         
        if (t < minTemp) minTemp = t;
        if (t > maxTemp) maxTemp = t;
      }
    }
    
    avgTemp = sumTemp / (THERMAL_WIDTH * THERMAL_HEIGHT);
  }

  // 3. Enviar dados térmicos com timestamp
  Serial.println("[WEBSOCKET] Enviando array térmico com timestamp...");
   
  // Formato: [1 byte header][6 bytes timestamp][dados térmicos]
  size_t thermal_data_size = sizeof(frameTemp);
  size_t thermal_buffer_size = 1 + 6 + thermal_data_size;
  uint8_t* thermal_buffer = (uint8_t*)malloc(thermal_buffer_size);
   
  if (!thermal_buffer) {  // Verificação de alocação NULL
    Serial.println("[WEBSOCKET] ERRO: Sem memória para o buffer térmico!");
    return;
  }
   
  // Índice atual no buffer
  size_t idx = 0;
   
  // Adicionar byte de cabeçalho (0x02 = array térmico)
  thermal_buffer[idx++] = 0x02;
   
  // Adicionar timestamp como 6 bytes binários
  thermal_buffer[idx++] = (epochTime >> 0) & 0xFF;
  thermal_buffer[idx++] = (epochTime >> 8) & 0xFF;
  thermal_buffer[idx++] = (epochTime >> 16) & 0xFF;
  thermal_buffer[idx++] = (epochTime >> 24) & 0xFF;
  thermal_buffer[idx++] = (millisPart >> 0) & 0xFF;
  thermal_buffer[idx++] = (millisPart >> 8) & 0xFF;
   
  // Estatísticas térmicas para debug
  if (sensorError) {
    Serial.println("[MLX] Enviando estado de erro");
    // Em caso de erro, enviamos um pacote especial
    float errorData[THERMAL_WIDTH * THERMAL_HEIGHT];
    for (int i = 0; i < THERMAL_WIDTH * THERMAL_HEIGHT; i++) {
      errorData[i] = -999.0;  // Valor especial para indicar erro
    }
    memcpy(thermal_buffer + idx, errorData, thermal_data_size);
  } else {
    Serial.printf("[MLX] Envio térmico - mín/méd/máx: %.2f/%.2f/%.2f°C\n", 
                 minTemp, avgTemp, maxTemp);
    memcpy(thermal_buffer + idx, frameTemp, thermal_data_size);
  }
   
  // Enviar buffer combinado
  bool success = webSocket.sendBIN(thermal_buffer, thermal_buffer_size);
   
  // Limpar a memória imediatamente
  free(thermal_buffer);
  thermal_buffer = NULL;  // Definir como NULL após liberar
   
  if (!success) {
    Serial.println("[WEBSOCKET] ERRO: Falha ao enviar array térmico!");
    return;
  }
   
  Serial.printf("[WEBSOCKET] Array térmico enviado: %u bytes\n", (unsigned int)(thermal_buffer_size));
   
  // Adicionando delay para dar tempo ao websocket de processar o primeiro envio
  // e para o cliente também processar os dados recebidos
  delay(200);  // Delay de 200ms entre envios de dados
   
  // 4. Controle de envio de imagem (só envia a cada N ciclos)
  frameCount++;
  if (frameCount < imageSendInterval) {
    Serial.printf("[CAM] Pulando envio de imagem (ciclo %d de %d)\n", frameCount, imageSendInterval);
    delay(50);  // Pequena pausa para gerenciamento de memória
    return;
  }
   
  frameCount = 0; // Resetar contador
   
  // 5. Capturar imagem da câmera apenas quando necessário
  Serial.println("[CAM] Capturando imagem...");
  camera_fb_t* fb = esp_camera_fb_get();
   
  if (!fb) {
    Serial.println("[CAM] ERRO: Falha ao capturar imagem!");
    return;
  }
   
  // 6. Enviar imagem com timestamp
  Serial.println("[WEBSOCKET] Enviando imagem com timestamp...");
   
  // Formato completo: [1 byte header][6 bytes timestamp][dados da imagem]
  size_t image_data_size = fb->len;
  size_t image_buffer_size = 1 + 6 + image_data_size;
  uint8_t* image_buffer = (uint8_t*)malloc(image_buffer_size);
   
  if (!image_buffer) {
    Serial.println("[WEBSOCKET] ERRO: Sem memória para o buffer de imagem!");
    esp_camera_fb_return(fb); 
    fb = NULL;
    return;
  }
   
  // Índice atual no buffer
  idx = 0;
   
  // Adicionar byte de cabeçalho (0x01 = imagem)
  image_buffer[idx++] = 0x01;
   
  // Adicionar timestamp como 6 bytes binários
  image_buffer[idx++] = (epochTime >> 0) & 0xFF;
  image_buffer[idx++] = (epochTime >> 8) & 0xFF;
  image_buffer[idx++] = (epochTime >> 16) & 0xFF;
  image_buffer[idx++] = (epochTime >> 24) & 0xFF;
  image_buffer[idx++] = (millisPart >> 0) & 0xFF;
  image_buffer[idx++] = (millisPart >> 8) & 0xFF;
   
  // Adicionar os dados da imagem
  memcpy(image_buffer + idx, fb->buf, image_data_size);
   
  // Enviar buffer combinado
  success = webSocket.sendBIN(image_buffer, image_buffer_size);
   
  // Limpar recursos após uso
  free(image_buffer);
  image_buffer = NULL;
   
  // Sempre liberar o buffer da câmera quando não precisar mais
  esp_camera_fb_return(fb);
  fb = NULL;
   
  if (!success) {
    Serial.println("[WEBSOCKET] ERRO: Falha ao enviar imagem!");
    return;
  }
   
  Serial.printf("[WEBSOCKET] Imagem enviada: %u bytes\n", (unsigned int)(image_buffer_size));
   
  // Pequeno delay para permitir que o ESP processe e limpe a memória
  delay(50);
} // End of send_data()
 
 //----------INSERIR CÓDIGO CONFIGURAÇÃO DO SENSOR AQUI------------
 int setup_sensor() {
  Serial.println("[MLX] Setting up MLX90640 sensor...");
  
  // First try to recover the I2C bus
  if (!recover_i2c_bus()) {
    Serial.println("[MLX] Failed to initialize I2C bus");
    return 0;
  }
  
  // Attempt MLX90640 initialization with retries
  const int MAX_INIT_RETRIES = 3;
  int initRetries = MAX_INIT_RETRIES;
  bool sensorInitialized = false;
  
  while (initRetries > 0) {
    if (initRetries < MAX_INIT_RETRIES) {
      Serial.printf("[MLX] Retry %d/%d initializing sensor...\n", MAX_INIT_RETRIES - initRetries + 1, MAX_INIT_RETRIES);
      
      // Try to recover I2C bus before retry
      if (!recover_i2c_bus()) {
        Serial.println("[MLX] Failed to recover I2C bus for retry");
        initRetries--;
        delay(200 * (MAX_INIT_RETRIES - initRetries)); // Increasing delay between retries
        continue;
      }
    }
    
    if (mlx.begin(MLX90640_I2CADDR_DEFAULT, &Wire)) {
      sensorInitialized = true;
      Serial.println("[MLX] MLX90640 found and initialized");
      break;
    }
    
    Serial.printf("[MLX] Attempt %d failed to initialize sensor\n", MAX_INIT_RETRIES - initRetries + 1);
    initRetries--;
    if (initRetries > 0) {
      delay(200 * (MAX_INIT_RETRIES - initRetries)); // Increasing delay between retries
    }
  }
  
  if (!sensorInitialized) {
    Serial.println("[MLX] ERROR: MLX90640 not found after maximum retries!");
    return 0;
  }
    // Configure sensor parameters
  Serial.println("[MLX] Configurando parâmetros do sensor...");
  
  // Set refresh rate first
  mlx.setRefreshRate(MLX90640_2_HZ);
  
  // Set resolution
  mlx.setResolution(MLX90640_ADC_18BIT);
  
  // Set operating mode
  mlx.setMode(MLX90640_CHESS);
  
  // Verify the configuration
  Serial.println("[MLX] Verificando configuração do sensor...");
  
  mlx90640_mode_t currentMode = mlx.getMode();
  mlx90640_resolution_t currentResolution = mlx.getResolution();
  mlx90640_refreshrate_t currentRefreshRate = mlx.getRefreshRate();
    if (currentMode == MLX90640_CHESS &&
      currentResolution == MLX90640_ADC_18BIT &&
      currentRefreshRate == MLX90640_2_HZ) {
    Serial.println("[MLX] Configuração do sensor verificada com sucesso");
  } else {
    Serial.println("[MLX] WARNING: Configuração do sensor não coincide!");
    Serial.printf("[MLX] Mode: %d, Resolution: %d, Refresh Rate: %d\n", 
                 currentMode, currentResolution, currentRefreshRate);
  }
  
  // Try to read a frame to confirm everything is working
  float testFrame[768];
  if (mlx.getFrame(testFrame) != 0) {
    Serial.println("[MLX] WARNING: Initial frame read failed!");
  } else {
    Serial.println("[MLX] Initial frame read successful");
  }
  
  Serial.println("[MLX] Sensor setup completed successfully");
  return 1;
}
 
 //----------INSERIR CÓDIGO CAPTURAR DADOS DO SENSOR AQUI------------
 int get_sensor_data() {
    static unsigned long lastSuccessTime = 0;
    static int consecutiveErrors = 0;
    bool success = false;
    float sumTemp = 0;
    bool hasInvalidData = false;
    int validPixels = THERMAL_WIDTH * THERMAL_HEIGHT;
  
    // If we've had too many consecutive errors, try a full sensor reset
    if (consecutiveErrors >= 5) {
        Serial.println("[MLX] Too many consecutive errors, attempting full sensor reset...");
        if (setup_sensor()) {
            consecutiveErrors = 0;
            Serial.println("[MLX] Sensor reset successful");
        } else {
            Serial.println("[MLX] Sensor reset failed!");
            return 0;
        }
    }
    // Try to read frame with retries
  int retryCount = 0;
  while (retryCount < MAX_MLX_RETRIES && !success) {
    if (retryCount > 0) {
      Serial.printf("[MLX] Read attempt %d/%d...\n", retryCount + 1, MAX_MLX_RETRIES);
      
      // Different recovery strategy based on mode
      if (mode == RUNNING_WIFI) {
        if (!stabilize_i2c_after_wifi()) {
          Serial.println("[MLX] Failed to stabilize I2C bus in WiFi mode");
          delay(MLX_RETRY_DELAY * (retryCount + 1)); // Progressive delay
        }
      } else {
        if (!recover_i2c_bus()) {
          Serial.println("[MLX] Failed to recover I2C bus");
          delay(MLX_RETRY_DELAY * retryCount);
        }
      }
      
      // On later retries, try reinitializing the sensor with mode-specific settings
      if (retryCount >= 2) {
        Serial.println("[MLX] Attempting sensor reinitialization...");
        mlx.begin(MLX90640_I2CADDR_DEFAULT, &Wire);
        mlx.setMode(MLX90640_CHESS);
        mlx.setResolution(MLX90640_ADC_18BIT);
        if (mode == RUNNING_WIFI) {
          mlx.setRefreshRate(MLX90640_1_HZ); // Slower refresh rate in WiFi mode
          Wire.setClock(50000); // Ensure slower I2C speed in WiFi mode
        } else {
          mlx.setRefreshRate(MLX90640_2_HZ);
          Wire.setClock(100000);
        }
        delay(100);
      }
    }
      // Try to read a frame with timeout
    unsigned long startTime = millis();
    while (millis() - startTime < 1000) {  // 1 second timeout
      if (mlx.getFrame(frameTemp) == 0) {
        success = true;
        break;
      }
      delay(10);
    }
    
    if (!success) {
      Serial.println("[MLX] Frame read failed!");
      delay(MLX_RETRY_DELAY * (retryCount + 1));
    }
    retryCount++;
  }
  
  if (!success) {
    consecutiveErrors++;
    Serial.printf("[MLX] ERROR: Failed to read thermal frame after %d attempts! Consecutive errors: %d\n", 
                 MAX_MLX_RETRIES, consecutiveErrors);
    return 0;
  }
  
  // Reset error counter on success
  consecutiveErrors = 0;
  lastSuccessTime = millis();
  
  // Data validation - check for unreasonable values
  minTemp = frameTemp[0];
  maxTemp = frameTemp[0];
  sumTemp = 0;
    uint8_t h, w;
  for (h = 0; h < THERMAL_HEIGHT; h++) {
    for (w = 0; w < THERMAL_WIDTH; w++) {
      float t = frameTemp[h * THERMAL_WIDTH + w];
      
      // Check for unreasonable temperatures (-40°C to 300°C is MLX90640's range)
      if (t < -40.0f || t > 300.0f) {
        Serial.printf("[MLX] WARNING: Invalid temperature detected: %.2f°C at pixel (%d,%d)\n", t, w, h);
        hasInvalidData = true;
        t = -999.0f;  // Mark as invalid
      }
      
      sumTemp += t;
      if (t < minTemp && t != -999.0f) minTemp = t;
      if (t > maxTemp && t != -999.0f) maxTemp = t;
    }
  }
  
  // Recalculate for invalid data
  if (hasInvalidData) {
    validPixels = 0;
    sumTemp = 0;
    
    int i;
    for (i = 0; i < THERMAL_WIDTH * THERMAL_HEIGHT; i++) {
      if (frameTemp[i] != -999.0f) {
        sumTemp += frameTemp[i];
        validPixels++;
      }
    }
  }
  
  // Calculate final average
  avgTemp = (validPixels > 0) ? (sumTemp / validPixels) : -999.0f;
  
  // Log results
  if (hasInvalidData) {
    Serial.printf("[MLX] WARNING: Some invalid readings detected. Valid pixels: %d/%d\n", 
                 validPixels, THERMAL_WIDTH * THERMAL_HEIGHT);
  }
  
  Serial.printf("[MLX] Temperature - min: %.2f°C, avg: %.2f°C, max: %.2f°C\n", 
               minTemp, avgTemp, maxTemp);
  
  return (validPixels > 0) ? 1 : 0;
}
 
 //-----------SETUP e LOOP---------------------------//
 void setup() {
   // Desativar o detector de brownout para evitar resets aleatórios
   WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);
   
   Serial.begin(115200);
   delay(100);
   Serial.println("[INIT] Iniciando ESP32-CAM Térmico");   // Initialize I2C with more robust configuration
   Wire.end();  // Make sure we start fresh
   delay(100);  // Give some time for the bus to settle
   
   // Configure I2C pins with explicit pullup
   pinMode(I2C_DATA_PIN, INPUT_PULLUP);
   pinMode(I2C_CLOCK_PIN, INPUT_PULLUP);
   delay(50);  // Allow pull-ups to stabilize
   
   Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
   Wire.setClock(I2C_CLOCK_SPEED);
   Wire.setTimeOut(I2C_TIMEOUT);
   
   Serial.printf("[I2C] Wire iniciado em SDA=%d, SCL=%d, Clock=%dHz\n", 
                 I2C_DATA_PIN, I2C_CLOCK_PIN, I2C_CLOCK_SPEED);

   if (setup_rtc()) {
     Serial.println("[INIT] RTC inicializado com sucesso");
   }

   if (setup_sensor()) {
     Serial.println("[INIT] Sensor térmico inicializado com sucesso");
   }

   setup_ble();
 }
 
 
 int running_ble() {
   int success = get_sensor_data();
   if (success) { //conseguiu fazer avgTemp, nimTemp, maxTemp
     uint32_t timestamp = rtc.now().unixtime();
     String ts = String(timestamp)+String(millis()%1000);
     String payload;
 
     Serial.println("[DEBUG] BLE notifications sent: ");     // Se tiver erro, envia um valor especial
     if (avgTemp == -999.0) {
       payload = sensorID + ts + ".ERR";  // Indicador de erro
     } else {
       payload = sensorID + ts + '.' + String((int)(avgTemp*100));
     }
     Serial.println(payload);
     data1Char->setValue(payload.c_str());
     data1Char->notify();
 
     if (maxTemp == -999.0) {
       payload = sensorID + ts + ".ERR";
     } else {
       payload = sensorID + ts + '.' + String((int)(maxTemp*100));
     }
     Serial.println(payload);
     data2Char->setValue(payload.c_str());
     data2Char->notify();
 
     if (minTemp == -999.0) {
       payload = sensorID + ts + ".ERR";
     } else {
       payload = sensorID + ts + '.' + String((int)(minTemp*100));
     }
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
 
 /**
  * Função que gerencia a conexão WiFi e WebSocket
  */
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
     // Enviar dados a cada 500ms (2Hz)
     unsigned long currentTime = millis();
     if (currentTime - lastSendTime >= sendInterval) {
       send_data();
       lastSendTime = currentTime; // Atualizar o timestamp da última vez que enviou dados
     }
     reconnectInterval = 1000; // Reinicia o intervalo de backoff quando conectado
   } else {
     // Somente tenta reconectar o WebSocket se o WiFi estiver conectado
     // e se passou tempo suficiente desde a última tentativa (backoff exponencial)
     unsigned long currentTime = millis();
     if (WiFi.status() == WL_CONNECTED && 
         currentTime - lastReconnectAttempt > reconnectInterval) {
       
       Serial.println("[WEBSOCKET] Tentando reconectar...");
       Serial.printf("[WEBSOCKET] Intervalo de reconexão: %d ms\n", reconnectInterval);
       
       webSocket.disconnect();
       delay(100);
       setup_webSocket();
       
       // Atualizar o timestamp da última tentativa
       lastReconnectAttempt = currentTime;
       
       // Aumentar exponencialmente o intervalo de reconexão (dobra a cada tentativa)
       reconnectInterval = min(reconnectInterval * 2, maxReconnectInterval);
     }
   }
   
   // Pequena pausa para evitar sobrecarga da CPU
   delay(10);
 }
 
 
void loop() {
  if(mode == RUNNING_BLE) {
    running_ble();
  }
  else if(mode == RUNNING_WIFI) {
    running_wifi();
  }
  else if(mode == SETTING) {
    if(ssid != "" && password != "" && server_ip[0] != '\0') {
      setup_camera();
      if(setup_wifi()) {
        setup_webSocket();
        Serial.println("[WIFI] Conectado ao WebSocket!");
        mode = RUNNING_WIFI;
      }
    }
  }
  

  delay(delay_millis);
}

#define I2C_RETRY_COUNT 3
#define I2C_POWER_STABILIZE_DELAY 50

// Helper function to check and recover I2C bus state
bool recover_i2c_bus() {
  Serial.println("[I2C] Attempting to recover I2C bus...");
  
  // End current I2C communication
  Wire.end();
  delay(100);  // Give time for bus to settle
  
  // Configure pins for manual control
  pinMode(I2C_DATA_PIN, OUTPUT);
  pinMode(I2C_CLOCK_PIN, OUTPUT);
  
  // Toggle the clock 16 times to ensure any stuck device releases SDA
  for(int i = 0; i < 16; i++) {
    digitalWrite(I2C_CLOCK_PIN, HIGH);
    delayMicroseconds(3);
    digitalWrite(I2C_CLOCK_PIN, LOW);
    delayMicroseconds(3);
  }
  
  // Generate STOP condition
  digitalWrite(I2C_DATA_PIN, LOW);
  delayMicroseconds(3);
  digitalWrite(I2C_CLOCK_PIN, HIGH);
  delayMicroseconds(3);
  digitalWrite(I2C_DATA_PIN, HIGH);
  delayMicroseconds(3);
  
  // Reconfigure pins for I2C operation
  pinMode(I2C_DATA_PIN, INPUT_PULLUP);
  pinMode(I2C_CLOCK_PIN, INPUT_PULLUP);
  delay(50);  // Wait for pull-ups to stabilize
  
  // Reinitialize I2C
  Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
  Wire.setClock(I2C_CLOCK_SPEED);
  Wire.setTimeOut(I2C_TIMEOUT);
  
  // Try to read from the MLX90640 address to check if the bus is working
  Wire.beginTransmission(MLX90640_I2CADDR_DEFAULT);
  byte error = Wire.endTransmission();
  
  if(error != 0) {
    Serial.printf("[I2C] Failed to recover bus, error code: %d\n", error);
    return false;
  }
  
  Serial.println("[I2C] Bus recovered successfully");
  return true;
}

// Function to stabilize I2C bus after WiFi operations
bool stabilize_i2c_after_wifi() {
  static unsigned long lastStabilizeTime = 0;
  const unsigned long minStabilizeInterval = 100; // Minimum time between stabilization attempts
  static int stabilizationFailures = 0;
  const int MAX_FAILURES = 3;
  
  // Don't stabilize too frequently
  if (millis() - lastStabilizeTime < minStabilizeInterval) {
    return true;
  }
  
  Serial.println("[I2C] Stabilizing I2C bus after WiFi operation...");
  
  // Extended power stabilization for troubled buses
  if (stabilizationFailures > 0) {
    delay(I2C_POWER_STABILIZE_DELAY * (stabilizationFailures + 1));
  } else {
    delay(I2C_POWER_STABILIZE_DELAY);
  }
  
  // Reset and recover I2C bus
  if (!recover_i2c_bus()) {
    stabilizationFailures++;
    if (stabilizationFailures >= MAX_FAILURES) {
      Serial.println("[I2C] Critical: Multiple I2C recovery failures");
      return false;
    }
    return false;
  }
  
  // Reinitialize at a slower speed when in WiFi mode
  Wire.begin(I2C_DATA_PIN, I2C_CLOCK_PIN);
  Wire.setClock(50000); // Slower clock in WiFi mode (50kHz instead of 100kHz)
  Wire.setTimeOut(200); // Shorter timeout for quick fault detection
  
  bool devicesOk = true;
  int retries = 3;
  
  while (retries-- > 0) {
    bool rtcOk = false;
    bool mlxOk = false;
    
    // Check RTC with multiple attempts
    for (int i = 0; i < 2; i++) {
      Wire.beginTransmission(0x68); // DS3231 address
      if (Wire.endTransmission() == 0) {
        rtcOk = true;
        break;
      }
      delay(5);
    }
    
    // Check MLX90640 with multiple attempts
    for (int i = 0; i < 2; i++) {
      Wire.beginTransmission(MLX90640_I2CADDR_DEFAULT);
      if (Wire.endTransmission() == 0) {
        mlxOk = true;
        break;
      }
      delay(5);
    }
    
    if (rtcOk && mlxOk) {
      Serial.println("[I2C] All devices responding");
      // On success, reset the failure counter
      stabilizationFailures = 0;
      devicesOk = true;
      break;
    } else {
      devicesOk = false;
      if (!rtcOk) Serial.println("[I2C] RTC not responding");
      if (!mlxOk) Serial.println("[I2C] MLX90640 not responding");
      
      if (retries > 0) {
        Serial.printf("[I2C] Retry %d...\n", 3 - retries);
        delay(50 * (3 - retries)); // Increasing delay between retries
        recover_i2c_bus(); // Try to recover before next attempt
      }
    }
  }
  
  lastStabilizeTime = millis();
  
  if (!devicesOk) {
    stabilizationFailures++;
    if (stabilizationFailures >= MAX_FAILURES) {
      Serial.println("[I2C] Critical: Multiple device communication failures");
      return false;
    }
    return false;
  }
  
  return true;
}