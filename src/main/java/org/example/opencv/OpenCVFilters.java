package org.example.opencv;

import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

public class OpenCVFilters {

    public static Mat gaussianBlur(Mat mat, double sigma) {
        if (mat == null) return null;
        Mat dst = new Mat();
        Imgproc.GaussianBlur(mat, dst, new Size(0, 0), sigma);
        return dst;
    }

    public static Mat medianFilter(Mat mat, int ksize) {
        if (mat == null) return null;
        Mat dst = new Mat();
        Imgproc.medianBlur(mat, dst, ksize);
        return dst;
    }

    public static Mat applyThreshold(Mat mat, double minVal, double maxVal) {
        if (mat == null) return null;
        Mat dst = new Mat(mat.size(), mat.type());
        for (int y = 0; y < mat.rows(); y++) {
            for (int x = 0; x < mat.cols(); x++) {
                double[] v = mat.get(y, x);
                double val = v[0];
                if (val < minVal || val > maxVal) val = 0;
                dst.put(y, x, val);
            }
        }
        return dst;
    }
}