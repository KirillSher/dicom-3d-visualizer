package org.example.opencv;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

public class OpenCVUtils {

    public static Mat imagePlusToMat(ImagePlus img) {
        ImageProcessor ip = img.getProcessor();
        int width = ip.getWidth();
        int height = ip.getHeight();

        short[] pixels16 = (short[]) ip.getPixels();

        Mat mat16 = new Mat(height, width, CvType.CV_16U);
        mat16.put(0, 0, pixels16);

        Mat mat8 = new Mat();
        Core.normalize(mat16, mat8, 0, 255, Core.NORM_MINMAX, CvType.CV_8U);

        return mat8;
    }
}