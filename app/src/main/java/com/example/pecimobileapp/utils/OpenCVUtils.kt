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
        
        // Processar imagem conforme o algoritmo fornecido:
        // 1. Normalizar a matriz térmica para valores de 0-255
        val normalizedData = ByteArray(thermalData.size)
        for (i in thermalData.indices) {
            val temp = thermalData[i]
            if (temp.isNaN() || temp == 0f) {
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
        Imgproc.applyColorMap(thermalMat, colorMat, Imgproc.COLORMAP_JET)

        // Depois aplicar Gaussian Blur no resultado colorido
        val blurredMat = Mat()
        Imgproc.GaussianBlur(colorMat, blurredMat, Size(3.0, 3.0), 0.0)
        
        // 3. Redimensionar o resultado borrado para o tamanho da imagem original
        val resizedMat = Mat()
        Imgproc.resize(blurredMat, resizedMat, Size(bitmap.width.toDouble(), bitmap.height.toDouble()), 0.0, 0.0, Imgproc.INTER_CUBIC)
        
        // 4. Converter para Bitmap
        val thermalBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(resizedMat, thermalBitmap)
        
        // 5. Sobrepor na imagem original com opacidade reduzida
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            alpha = 80  // Opacidade de ~30%
        }
        canvas.drawBitmap(thermalBitmap, 0f, 0f, paint)
        
        // 6. Desenhar legenda de cores
        drawThermalLegend(canvas, minTemp, maxTemp, bitmap.width, bitmap.height)
        
        // Liberar recursos
        thermalMat.release()
        blurredMat.release()
        colorMat.release()
        resizedMat.release()
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
     * Desenha uma legenda para o mapa térmico
     */
    private fun drawThermalLegend(canvas: Canvas, minTemp: Float, maxTemp: Float, width: Int, height: Int) {
        val paint = Paint().apply {
            style = Paint.Style.FILL
        }
        
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f
            isFakeBoldText = true
            setShadowLayer(2f, 1f, 1f, Color.BLACK)
        }
        
        // Posição e tamanho da barra de legenda
        val legendWidth = width / 3
        val legendHeight = 20
        val legendX = width - legendWidth - 20
        val legendY = height - legendHeight - 20
        
        // Desenha a barra de cores usando COLORMAP_JET
        for (i in 0 until legendWidth) {
            val normalizedPos = i.toFloat() / legendWidth
            val color = getJetColor(normalizedPos)
            paint.color = color
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
            (legendY - 5).toFloat(),
            textPaint
        )
        
        val maxTempText = String.format("%.1f°C", maxTemp)
        val maxTextWidth = textPaint.measureText(maxTempText)
        
        canvas.drawText(
            maxTempText,
            (legendX + legendWidth - maxTextWidth).toFloat(),
            (legendY - 5).toFloat(),
            textPaint
        )
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