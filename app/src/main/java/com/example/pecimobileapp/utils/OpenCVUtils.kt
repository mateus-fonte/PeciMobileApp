package com.example.pecimobileapp.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
     * Sobrepõe os dados térmicos na imagem
     */
    private fun overlayThermalData(bitmap: Bitmap, thermalData: FloatArray) {
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            style = Paint.Style.FILL
            alpha = 5  // Reduzido drasticamente para apenas 2% de opacidade
        }
        
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val thermalWidth = 32
        val thermalHeight = 24
        
        // Tamanho de cada "pixel" térmico quando sobreposto à imagem original
        val blockWidth = originalWidth.toFloat() / thermalWidth
        val blockHeight = originalHeight.toFloat() / thermalHeight
        
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
        
        // Desenhar cada "pixel" térmico - apenas mostrando os pontos mais significativos
        for (y in 0 until thermalHeight) {
            for (x in 0 until thermalWidth) {
                val index = y * thermalWidth + x
                val temperature = thermalData[index]
                
                if (!temperature.isNaN() && temperature != 0f) {
                    // Normaliza a temperatura para um valor entre 0 e 1
                    val normalizedTemp = (temperature - minTemp) / (maxTemp - minTemp)
                    
                    // Só mostra pixel térmico se estiver acima de um limiar
                    // para reduzir a densidade da sobreposição
                    if (normalizedTemp > 0.3f) {
                        // Converte para cor (azul para valores baixos, vermelho para altos)
                        val color = getColorForTemperature(normalizedTemp)
                        
                        // Define a cor do pincel
                        paint.color = color
                        
                        // Desenha o retângulo correspondente na imagem
                        // Usando espaçamento entre os blocos para mostrar mais da imagem original
                        canvas.drawRect(
                            x * blockWidth + 1,
                            y * blockHeight + 1,
                            (x + 1) * blockWidth - 1,
                            (y + 1) * blockHeight - 1,
                            paint
                        )
                    }
                }
            }
        }
        
        // Desenha uma legenda de cores
        drawThermalLegend(canvas, minTemp, maxTemp, bitmap.width, bitmap.height)
    }
    
    /**
     * Calcula a temperatura média da área do rosto
     */
    private fun calculateFaceTemperature(
        face: Rect,
        imageWidth: Int,
        imageHeight: Int,
        thermalData: FloatArray
    ): Float {
        val thermalWidth = 32
        val thermalHeight = 24
        
        // Converte as coordenadas do rosto para o espaço térmico
        val faceXThermal = (face.x.toFloat() / imageWidth * thermalWidth).toInt()
        val faceYThermal = (face.y.toFloat() / imageHeight * thermalHeight).toInt()
        val faceWidthThermal = (face.width.toFloat() / imageWidth * thermalWidth).toInt().coerceAtLeast(1)
        val faceHeightThermal = (face.height.toFloat() / imageHeight * thermalHeight).toInt().coerceAtLeast(1)
        
        // Calcula a temperatura média na região do rosto
        var sum = 0f
        var count = 0
        
        for (y in faceYThermal until (faceYThermal + faceHeightThermal)) {
            for (x in faceXThermal until (faceXThermal + faceWidthThermal)) {
                if (x < thermalWidth && y < thermalHeight && x >= 0 && y >= 0) {
                    val temp = thermalData[y * thermalWidth + x]
                    if (!temp.isNaN() && temp > 0) {
                        sum += temp
                        count++
                    }
                }
            }
        }
        
        return if (count > 0) sum / count else Float.NaN
    }
    
    /**
     * Obtém uma cor baseada na temperatura normalizada (0-1)
     * De azul (frio) para vermelho (quente)
     */
    private fun getColorForTemperature(normalizedTemp: Float): Int {
        // Convertemos o valor normalizado para uma cor do espectro (azul -> ciano -> verde -> amarelo -> vermelho)
        return when {
            normalizedTemp < 0.2f -> {
                // Azul para ciano
                val ratio = normalizedTemp / 0.2f
                val r = 0
                val g = (255 * ratio).toInt()
                val b = 255
                Color.rgb(r, g, b)
            }
            normalizedTemp < 0.4f -> {
                // Ciano para verde
                val ratio = (normalizedTemp - 0.2f) / 0.2f
                val r = 0
                val g = 255
                val b = (255 * (1 - ratio)).toInt()
                Color.rgb(r, g, b)
            }
            normalizedTemp < 0.6f -> {
                // Verde para amarelo
                val ratio = (normalizedTemp - 0.4f) / 0.2f
                val r = (255 * ratio).toInt()
                val g = 255
                val b = 0
                Color.rgb(r, g, b)
            }
            normalizedTemp < 0.8f -> {
                // Amarelo para vermelho
                val ratio = (normalizedTemp - 0.6f) / 0.2f
                val r = 255
                val g = (255 * (1 - ratio)).toInt()
                val b = 0
                Color.rgb(r, g, b)
            }
            else -> {
                // Vermelho
                Color.rgb(255, 0, 0)
            }
        }
    }
    
    /**
     * Desenha uma legenda para o mapa térmico
     */
    private fun drawThermalLegend(canvas: Canvas, minTemp: Float, maxTemp: Float, width: Int, height: Int) {
        val paint = Paint().apply {
            style = Paint.Style.FILL
        }
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 30f
            isFakeBoldText = true
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        
        // Posição e tamanho da barra de legenda
        val legendWidth = width / 3
        val legendHeight = 30
        val legendX = width - legendWidth - 20
        val legendY = height - legendHeight - 20
        
        // Desenha a barra de cores
        for (i in 0 until legendWidth) {
            val normalizedTemp = i.toFloat() / legendWidth
            paint.color = getColorForTemperature(normalizedTemp)
            canvas.drawRect(
                legendX + i.toFloat(),
                legendY.toFloat(),
                legendX + i + 1f,
                (legendY + legendHeight).toFloat(),
                paint
            )
        }
        
        // Desenha os valores mínimo e máximo
        canvas.drawText(
            String.format("%.1f°C", minTemp),
            legendX.toFloat(),
            (legendY - 10).toFloat(),
            textPaint
        )
        
        canvas.drawText(
            String.format("%.1f°C", maxTemp),
            (legendX + legendWidth - 80).toFloat(),
            (legendY - 10).toFloat(),
            textPaint
        )
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