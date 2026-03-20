package org.example.opencv;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class OpenCVUtils {

    public static Mat imagePlusToMat(ImagePlus img) {

        ImageProcessor ip = img.getProcessor();

        int width = ip.getWidth();
        int height = ip.getHeight();

        Mat mat = new Mat(height, width, CvType.CV_16U);

        // Получаем весь массив пикселей сразу
        short[] pixels = (short[]) ip.getPixels();

        // Копируем в Mat одной операцией
        mat.put(0, 0, pixels);

        return mat;
    }
}