package org.example.model;

import ij.ImagePlus;
import org.example.opencv.OpenCVUtils;
import org.opencv.core.Mat;

import java.io.File;

public class DicomSlice {

    private final File file;
    private final double sliceLocation;
    private final String orientation;
    private final ImagePlus image;
    private Mat mat8;

    public DicomSlice(File file, double sliceLocation, String orientation, ImagePlus image) {
        this.file = file;
        this.sliceLocation = sliceLocation;
        this.orientation = orientation;
        this.image = image;
    }

    public File getFile() {
        return file;
    }

    public double getSliceLocation() {
        return sliceLocation;
    }

    public String getOrientation() {
        return orientation;
    }

    public ImagePlus getImage() {
        return image;
    }

    public Mat getMat8() {
        if (mat8 == null) {
            mat8 = OpenCVUtils.imagePlusToMat(image);
        }
        return mat8;
    }
}