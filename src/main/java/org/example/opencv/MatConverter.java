package org.example.opencv;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class MatConverter {

    public static Image toFXImageColor(Mat mat) {
        if (mat.type() != CvType.CV_8UC3) {
            // Если не цветное, конвертируем
            Mat colorMat = new Mat();
            Imgproc.cvtColor(mat, colorMat, Imgproc.COLOR_GRAY2BGR);
            mat = colorMat;
        }

        int width = mat.cols();
        int height = mat.rows();
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_3BYTE_BGR);
        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();
        mat.get(0, 0, data);
        return SwingFXUtils.toFXImage(img, null);
    }
}