package org.example.model;

import ij.ImagePlus;

import java.io.File;

public class DicomSlice {

    private final File file;
    private final double sliceLocation;
    private final String orientation;
    private final double thickness;
    private final ImagePlus image;
    private ImagePlus imagePlus;

    public DicomSlice(File file, double sliceLocation, String orientation, double thickness, ImagePlus image) {
        this.file = file;
        this.sliceLocation = sliceLocation;
        this.orientation = orientation;
        this.thickness = thickness;
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

    public double getThickness() {
        return thickness;
    }

    public ImagePlus getImage() {
        return image;
    }

    public ImagePlus getImagePlus() {
        return imagePlus;
    }
}