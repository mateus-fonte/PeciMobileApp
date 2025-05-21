package com.example.pecimobileapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.util.Log
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.CascadeClassifier
import java.io.File
import java.io.FileOutputStream

/**
 * Classe utilitária para processamento de imagem com OpenCV
 */
class OpenCVUtils(private val context: Context) {

    // Classificador para detecção facial
    private var faceDetector: CascadeClassifier? = null
    private val TAG = "OpenCVUtils"

    init {
        initFaceDetector()
    }

    /**
     * Inicializa o detector facial usando o arquivo XML do Haar Cascade
     */
    private fun initFaceDetector() {
        try {
            // Copiar o arquivo XML do assets para um arquivo temporário
            val inputStream = context.assets.open("haarcascade_frontalface_default.xml")
            val cascadeDir = File(context.cacheDir, "cascade")
            cascadeDir.mkdir()
            val cascadeFile = File(cascadeDir, "face_cascade.xml")
            val outputStream = FileOutputStream(cascadeFile)
            
            val buffer = ByteArray(4096)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
            }
            inputStream.close()
            outputStream.close()
            
            // Carregar o classificador com o arquivo temporário
            faceDetector = CascadeClassifier(cascadeFile.absolutePath)
            
            if (faceDetector?.empty() == true) {
                Log.e(TAG, "Falha ao carregar o classificador")
                faceDetector = null
            } else {
                Log.d(TAG, "Classificador facial carregado com sucesso")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao inicializar detector facial", e)
        }
    }

    /**
     * Espelha um bitmap horizontalmente
     */
    private fun flipBitmap(bitmap: Bitmap): Bitmap {
        val matrix = android.graphics.Matrix().apply {
            preScale(-1.0f, 1.0f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
    
    /**
     * Espelha os dados térmicos horizontalmente
     */
    private fun flipThermalData(thermalData: FloatArray): FloatArray {
        val width = 32
        val height = 24
        val flippedData = FloatArray(thermalData.size)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val originalIndex = y * width + x
                val flippedIndex = y * width + (width - 1 - x)
                flippedData[flippedIndex] = thermalData[originalIndex]
            }
        }
        
        return flippedData
    }

    /**
     * Detecta rostos em uma imagem e sobrepõe com dados térmicos
     * @param originalBitmap Imagem original da câmera
     * @param thermalData Matriz de dados térmicos (32x24 valores de temperatura)
     * @return Bitmap com rostos detectados e sobreposição térmica
     */
    fun processImagesWithFaceDetection(originalBitmap: Bitmap, thermalData: FloatArray?): Pair<Bitmap, List<FaceData>> {
        // Espelhar a imagem da câmera, mas manter os dados térmicos como estão
        val flippedBitmap = flipBitmap(originalBitmap)
        
        // Cria uma cópia do bitmap espelhado para não modificar o original
        val resultBitmap = flippedBitmap.copy(flippedBitmap.config ?: Bitmap.Config.ARGB_8888, true)
        val facesList = mutableListOf<FaceData>()
        
        // Converte para Mat (formato do OpenCV)
        val rgbaMat = Mat()
        Utils.bitmapToMat(resultBitmap, rgbaMat)
        
        // Converte para escala de cinza para detecção facial
        val grayMat = Mat()
        Imgproc.cvtColor(rgbaMat, grayMat, Imgproc.COLOR_RGBA2GRAY)
        
        // Equaliza o histograma para melhorar a detecção
        Imgproc.equalizeHist(grayMat, grayMat)
        
        // Detecta rostos
        val faces = MatOfRect()
        faceDetector?.detectMultiScale(
            grayMat, 
            faces,
            1.1,  // Fator de escala
            3,    // Vizinhos mínimos
            0,    // Flags
            Size(30.0, 30.0),  // Tamanho mínimo de face
            Size()  // Tamanho máximo de face (sem restrição)
        )
        
        Log.d(TAG, "Rostos detectados: ${faces.toArray().size}")
        
        // Processa cada rosto detectado
        for (face in faces.toArray()) {
            // Desenha um retângulo ao redor do rosto
            Imgproc.rectangle(
                rgbaMat,
                Point(face.x.toDouble(), face.y.toDouble()),
                Point((face.x + face.width).toDouble(), (face.y + face.height).toDouble()),
                Scalar(0.0, 255.0, 0.0, 255.0),  // Verde
                3
            )
            
            // Se temos dados térmicos, calculamos a temperatura do rosto
            var faceTemperature = Float.NaN
            if (thermalData != null) {
                faceTemperature = calculateFaceTemperature(face, flippedBitmap.width, flippedBitmap.height, thermalData)
                
                // Adiciona texto com a temperatura
                val text = String.format("%.1f°C", faceTemperature)
                Imgproc.putText(
                    rgbaMat,
                    text,
                    Point(face.x.toDouble(), face.y - 10.0),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.7,
                    Scalar(255.0, 0.0, 0.0, 255.0),  // Vermelho
                    2
                )
            }
            
            // Adiciona os dados do rosto detectado à lista
            facesList.add(
                FaceData(
                    face.x,
                    face.y,
                    face.width,
                    face.height,
                    faceTemperature
                )
            )
        }
        
        // Converte de volta para Bitmap
        Utils.matToBitmap(rgbaMat, resultBitmap)
        
        // Se houver dados térmicos, vamos sobrepor uma visualização colorida dos dados
        thermalData?.let {
            overlayThermalData(resultBitmap, thermalData)
        }
        
        return Pair(resultBitmap, facesList)
    }
    
    /**
     * Sobrepõe os dados térmicos na imagem usando o algoritmo COLORMAP_JET
     */
    private fun overlayThermalData(bitmap: Bitmap, thermalData: FloatArray) {
        // Usar valores pré-definidos para faixa de temperatura humana para melhorar desempenho
        val minTemp = 20f 
        val maxTemp = 40f
        
        val thermalWidth = 32
        val thermalHeight = 24
        
        try {
            // Processar imagem com otimização:
            // 1. Normalizar a matriz térmica para valores de 0-255 de forma otimizada
            val normalizedData = ByteArray(thermalData.size)
            val range = maxTemp - minTemp
            
            // Otimizado - processar o array em uma passagem com cálculos mais eficientes
            for (i in thermalData.indices) {
                val temp = thermalData[i]
                normalizedData[i] = if (temp.isNaN() || temp <= 0f) {
                    0
                } else {
                    ((temp - minTemp) / range * 255).toInt().coerceIn(0, 255).toByte()
                }
            }
            
            // 2. Converter para Mat e aplicar colormap
            val thermalMat = Mat(thermalHeight, thermalWidth, CvType.CV_8UC1)
            thermalMat.put(0, 0, normalizedData)
            
            // Aplicar o colormap JET
            val colorMat = Mat()
            Core.bitwise_not(thermalMat, thermalMat)
            Imgproc.applyColorMap(thermalMat, colorMat, Imgproc.COLORMAP_JET)
            
            // 3. Aplicar blur diretamente (com kernel ímpar)
            val blurredMat = Mat()
            Imgproc.GaussianBlur(colorMat, blurredMat, Size(15.0, 5.0), 0.0)
            
            // 4. Redimensionar usando método mais rápido
            val resizedMat = Mat()
            Imgproc.resize(blurredMat, resizedMat, Size(bitmap.width.toDouble(), bitmap.height.toDouble()), 
                          0.0, 0.0, Imgproc.INTER_LINEAR)
            
            // 5. Converter para Bitmap e sobrepor
            val thermalBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resizedMat, thermalBitmap)
            
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                alpha = 127  // Opacidade de 50%
            }
            canvas.drawBitmap(thermalBitmap, 0f, 0f, paint)
            
            // 6. Desenhar legenda de cores simplificada
            drawThermalLegend(canvas, minTemp, maxTemp, bitmap.width, bitmap.height)
            
            // Liberar recursos
            thermalMat.release()
            colorMat.release()
            blurredMat.release()
            resizedMat.release()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar sobreposição térmica: ${e.message}", e)
        }
    }
    
    /**
     * Processa apenas os dados térmicos quando não há imagem da câmera
     * Cria uma imagem unicamente a partir dos dados térmicos com 100% de opacidade
     * @param thermalData Matriz de dados térmicos (32x24 valores de temperatura)
     * @return Pair com imagem térmica e lista com FaceData contendo temperatura média
     */    fun processOnlyThermalData(thermalData: FloatArray?): Pair<Bitmap, List<FaceData>> {
        if (thermalData == null) {
            // Se não houver dados térmicos, reutilizar uma imagem preta estática para economizar memória
            val emptyBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(emptyBitmap)
            canvas.drawColor(Color.BLACK)
            return Pair(emptyBitmap, emptyList())
        }
        
        // Criar um bitmap vazio para desenhar os dados térmicos
        val resultBitmap = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        canvas.drawColor(Color.BLACK) // Fundo preto
        
        // Calcular temperatura média para FaceData virtual
        val avgTemp = calculateAverageTemperature(thermalData)
        val facesList = mutableListOf<FaceData>()
        
        // Adiciona um FaceData "virtual" com a temperatura média
        if (!avgTemp.isNaN()) {
            Log.d(TAG, "Usando apenas dados térmicos, temperatura média: $avgTemp°C")
            
            // Adicionar texto com a temperatura média na parte superior da imagem
            val paint = Paint().apply {
                color = Color.WHITE
                textSize = 40f
                isAntiAlias = true
            }
            canvas.drawText("Temperatura média: ${String.format("%.1f°C", avgTemp)}", 20f, 40f, paint)
            
            // Adicionar FaceData virtual com a temperatura média
            facesList.add(
                FaceData(
                    -1, // Coordenadas negativas indicam que não é um rosto real
                    -1,
                    0,
                    0,
                    avgTemp
                )
            )
        }
        
        // Criar e sobrepor a visualização térmica com opacidade de 100%
        overlayThermalDataFullOpacity(resultBitmap, thermalData)
        
        return Pair(resultBitmap, facesList)
    }
      /**
     * Sobrepõe os dados térmicos com 100% de opacidade
     * Usado quando não há imagem de câmera, apenas dados térmicos
     */     private fun overlayThermalDataFullOpacity(bitmap: Bitmap, thermalData: FloatArray) {
        // Encontrar mínimo e máximo para normalização
        var minTemp = Float.MAX_VALUE
        var maxTemp = Float.MIN_VALUE
        
        for (temp in thermalData) {
            if (!temp.isNaN() && temp != 0f) {
                minTemp = minTemp.coerceAtMost(temp)
                maxTemp = maxTemp.coerceAtLeast(temp)
            }
        }
        
        // Ajustar para faixa de temperatura humana típica se os valores estiverem fora
        if (minTemp < 20f || minTemp == Float.MAX_VALUE) minTemp = 20f
        if (maxTemp > 40f || maxTemp == Float.MIN_VALUE) maxTemp = 40f
        
        val thermalWidth = 32
        val thermalHeight = 24
        
        try {            // Pré-processamento dos dados para suavizar o padrão listrado
            // Aplicar filtro horizontal nos dados brutos
            val smoothedData = FloatArray(thermalData.size)
            
            // Aplicar uma média móvel com janela maior na horizontal
            for (y in 0 until thermalHeight) {
                for (x in 0 until thermalWidth) {
                    var sum = 0f
                    var count = 0
                    
                    // Janela 5x3 (maior na horizontal para suavizar listras verticais)
                    for (dy in -1..1) {
                        val ny = y + dy
                        if (ny < 0 || ny >= thermalHeight) continue
                        
                        for (dx in -2..2) { // Mais pixels na horizontal
                            val nx = x + dx
                            if (nx < 0 || nx >= thermalWidth) continue
                            
                            val index = ny * thermalWidth + nx
                            val temp = thermalData[index]
                            if (!temp.isNaN() && temp > 0) {
                                sum += temp
                                count++
                            }
                        }
                    }
                    
                    smoothedData[y * thermalWidth + x] = if (count > 0) sum / count else 0f
                }
            }
            
            // 1. Normalizar a matriz térmica para valores de 0-255
            val normalizedData = ByteArray(thermalData.size)
            for (i in thermalData.indices) {
                val temp = smoothedData[i] // Usar dados suavizados
                if (temp <= 0f) {
                    normalizedData[i] = 0
                } else {
                    val normalized = ((temp - minTemp) / (maxTemp - minTemp) * 255).toInt().coerceIn(0, 255)
                    normalizedData[i] = normalized.toByte()
                }
            }
            
            // 2. Converter para Mat e aplicar colormap
            val thermalMat = Mat(thermalHeight, thermalWidth, CvType.CV_8UC1)
            thermalMat.put(0, 0, normalizedData)
            
            // Aplicar o colormap JET primeiro
            val colorMat = Mat()
            Core.bitwise_not(thermalMat, thermalMat)
            Imgproc.applyColorMap(thermalMat, colorMat, Imgproc.COLORMAP_JET)            // Técnica em múltiplos estágios para suavizar e remover o padrão listrado
            
            // 1º estágio: Primeiro um upsampling pequeno para tamanho intermediário
            val intermediateMat = Mat()
            val intermediateSize = Size(thermalWidth * 4.0, thermalHeight * 4.0) // 128x96
            Imgproc.resize(colorMat, intermediateMat, intermediateSize, 0.0, 0.0, Imgproc.INTER_LINEAR)
            
            // 2º estágio: Aplicar blur horizontal mais forte para eliminar o padrão de listras verticais
            val horizontalBlurMat = Mat()
            Imgproc.GaussianBlur(intermediateMat, horizontalBlurMat, Size(17.0, 3.0), 8.0, 1.0)
            
            // 3º estágio: Aplicar filtro bilateral para preservar bordas enquanto suaviza ainda mais
            val bilateralMat = Mat()
            Imgproc.bilateralFilter(horizontalBlurMat, bilateralMat, 9, 75.0, 75.0)
            
            // 4º estágio: Redimensionar para o tamanho final com interpolação de alta qualidade
            val resizedMat = Mat()
            Imgproc.resize(bilateralMat, resizedMat, Size(bitmap.width.toDouble(), bitmap.height.toDouble()), 
                0.0, 0.0, Imgproc.INTER_CUBIC)
            
            // 4. Converter para Bitmap
            val thermalBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(resizedMat, thermalBitmap)
            
            // 5. Desenhar na imagem com opacidade adequada
            val canvas = Canvas(bitmap)
            val paint = Paint().apply {
                alpha = 255  // Opacidade de 100% para imagem térmica pura
            }
            canvas.drawBitmap(thermalBitmap, 0f, 0f, paint)
            
            // 6. Desenhar legenda de cores
            drawThermalLegend(canvas, minTemp, maxTemp, bitmap.width, bitmap.height)
            
            // 10. Liberar recursos para evitar vazamento de memória
            // Liberar recursos
            thermalMat.release()
            colorMat.release()
            intermediateMat.release()
            horizontalBlurMat.release()
            bilateralMat.release()
            resizedMat.release()
            
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar dados térmicos: ${e.message}", e)
        }
    }
      /**
     * Calcula a temperatura média da área do rosto de forma otimizada
     */
    private fun calculateFaceTemperature(
        face: Rect,
        imageWidth: Int,
        imageHeight: Int,
        thermalData: FloatArray
    ): Float {
        val thermalWidth = 32
        val thermalHeight = 24
        
        try {
            // Otimizar com pré-cálculos e limitação de área
            // Converte as coordenadas do rosto para o espaço térmico com limites verificados
            val faceXThermal = (face.x.toFloat() / imageWidth * thermalWidth).toInt().coerceIn(0, thermalWidth-1)
            val faceYThermal = (face.y.toFloat() / imageHeight * thermalHeight).toInt().coerceIn(0, thermalHeight-1)
            
            // Garantir que a largura e altura térmicas estão dentro dos limites
            val faceWidthThermal = ((face.width.toFloat() / imageWidth) * thermalWidth).toInt()
                .coerceIn(1, thermalWidth - faceXThermal)
            val faceHeightThermal = ((face.height.toFloat() / imageHeight) * thermalHeight).toInt()
                .coerceIn(1, thermalHeight - faceYThermal)
            
            // Calcular a temperatura média na região do rosto com loop otimizado
            var sum = 0f
            var count = 0
            
            // Como sabemos que os índices estão dentro dos limites, não precisamos verificar em cada iteração
            for (y in faceYThermal until (faceYThermal + faceHeightThermal)) {
                val rowOffset = y * thermalWidth
                for (x in faceXThermal until (faceXThermal + faceWidthThermal)) {
                    val temp = thermalData[rowOffset + x]
                    // Verificar apenas se a temperatura é válida
                    if (!temp.isNaN() && temp > 0) {
                        sum += temp
                        count++
                    }
                }
            }
            
            return if (count > 0) sum / count else Float.NaN
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao calcular temperatura facial: ${e.message}")
            return Float.NaN
        }
    }
    
    /**
    * Calcula a temperatura média de toda a matriz térmica
    */
    private fun calculateAverageTemperature(thermalData: FloatArray): Float {
        var sum = 0f
        var count = 0
        
        for (temp in thermalData) {
            if (!temp.isNaN() && temp > 0) {
                sum += temp
                count++
            }
        }
        
        return if (count > 0) sum / count else Float.NaN
    }

    /**
     * Desenha uma legenda para o mapa térmico
     */    private fun drawThermalLegend(canvas: Canvas, minTemp: Float, maxTemp: Float, width: Int, height: Int) {
        val paint = Paint().apply {
            style = Paint.Style.FILL
        }
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 24f
            isFakeBoldText = true
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        
        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(3f, 1f, 1f, Color.BLACK)
        }
        
        // Desenhar título no topo central da imagem (como no matplotlib)
        canvas.drawText(
            "Thermal Image", 
            width / 2f, 
            40f, 
            titlePaint
        )
        
        // Posição e tamanho da barra de legenda (vertical para corresponder ao matplotlib)
        val legendWidth = 30
        val legendHeight = height / 2
        val legendX = width - legendWidth - 40 // Mais afastado da borda
        val legendY = (height - legendHeight) / 2 // Centralizado verticalmente
          // Desenha a barra de cores (vertical)
        for (i in 0 until legendHeight) {
            val normalizedPos = 1f - (i.toFloat() / legendHeight) // Invertido para mostrar quente em cima
            val color = getHotColor(normalizedPos) // Usar o novo mapa de cores estilo HOT/INFERNO
            paint.color = color
            canvas.drawRect(
                legendX.toFloat(),
                legendY + i.toFloat(),
                (legendX + legendWidth).toFloat(),
                legendY + i + 1f,
                paint
            )
        }
        
        // Desenha os valores mínimo e máximo na barra vertical
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            String.format("%.1f°C", maxTemp),
            (legendX - 5).toFloat(),
            (legendY + 20).toFloat(),
            textPaint
        )
        
        canvas.drawText(
            String.format("%.1f°C", minTemp),
            (legendX - 5).toFloat(),
            (legendY + legendHeight).toFloat(),
            textPaint
        )
        
        // Título da barra de cores
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 20f
        
        // Roda o texto para desenhar na vertical
        canvas.save()
        canvas.rotate(90f, legendX - 30f, legendY + legendHeight / 2f)
        canvas.drawText(
            "Temperature [°C]",
            legendX - 30f,
            legendY + legendHeight / 2f,
            textPaint
        )
        canvas.restore()
    }
      /**
     * Retorna uma cor COLORMAP_JET para um valor normalizado (0-1)
     */
    private fun getJetColor(v: Float): Int {
        // Implementação simplificada do mapa de cores JET
        val r: Int
        val g: Int
        val b: Int
        
        val v = v.coerceIn(0f, 1f)
        
        when {
            v < 0.125f -> {
                r = 0
                g = 0
                b = (255 * (0.5 + v / 0.125f)).toInt().coerceIn(0, 255)
            }
            v < 0.375f -> {
                r = 0
                g = (255 * (v - 0.125f) / 0.25f).toInt().coerceIn(0, 255)
                b = 255
            }
            v < 0.625f -> {
                r = (255 * (v - 0.375f) / 0.25f).toInt().coerceIn(0, 255)
                g = 255
                b = (255 * (1 - (v - 0.375f) / 0.25f)).toInt().coerceIn(0, 255)
            }
            v < 0.875f -> {
                r = 255
                g = (255 * (1 - (v - 0.625f) / 0.25f)).toInt().coerceIn(0, 255)
                b = 0
            }
            else -> {
                r = (255 * (1 - (v - 0.875f) / 0.125f)).toInt().coerceIn(0, 255)
                g = 0
                b = 0
            }
        }
        
        return Color.rgb(r, g, b)
    }
    
    /**
     * Retorna uma cor no estilo do mapa de calor "inferno" 
     * similar ao usado no matplotlib para um valor normalizado (0-1)
     */
    private fun getHotColor(v: Float): Int {
        val v = v.coerceIn(0f, 1f)
        
        // Implementação de um mapa de cores do tipo HOT/INFERNO
        val r: Int
        val g: Int
        val b: Int
        
        when {
            v < 0.25f -> {
                // Preto para roxo escuro
                val factor = v / 0.25f
                r = (80 * factor).toInt().coerceIn(0, 255) 
                g = 0
                b = (80 * factor).toInt().coerceIn(0, 255)
            }
            v < 0.5f -> {
                // Roxo escuro para magenta
                val factor = (v - 0.25f) / 0.25f
                r = (80 + 175 * factor).toInt().coerceIn(0, 255)
                g = 0
                b = (80 + 80 * factor).toInt().coerceIn(0, 255)
            }
            v < 0.75f -> {
                // Magenta para laranja/amarelo
                val factor = (v - 0.5f) / 0.25f
                r = 255
                g = (200 * factor).toInt().coerceIn(0, 255)
                b = (160 - 160 * factor).toInt().coerceIn(0, 255)
            }
            else -> {
                // Laranja/amarelo para branco
                val factor = (v - 0.75f) / 0.25f
                r = 255
                g = (200 + 55 * factor).toInt().coerceIn(0, 255)
                b = (0 + 255 * factor).toInt().coerceIn(0, 255)
            }
        }
        
        return Color.rgb(r, g, b)
    }
    
    /**
     * Dados de um rosto detectado
     */
    data class FaceData(
        val x: Int,
        val y: Int,
        val width: Int,
        val height: Int,
        val temperature: Float
    )
}
