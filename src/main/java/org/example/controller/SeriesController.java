package org.example.controller;

import javafx.scene.image.Image;
import org.example.model.DicomSeries;
import org.example.model.DicomSlice;
import org.example.service.DicomService;
import org.example.util.ImageConverter;
import org.opencv.core.Mat;

import java.io.File;

public class SeriesController {

    private final DicomService dicomService = new DicomService();

    private DicomSeries currentSeries;

    public void loadSeries(File folder) {

        currentSeries = dicomService.loadSeries(folder);

    }

    public int getSliceCount() {
        if (currentSeries == null) return 0;
        return currentSeries.getSlices().size();
    }

    public DicomSlice getSlice(int index) {
        if (currentSeries == null) return null;
        if (index < 0 || index >= currentSeries.getSlices().size()) return null;
        return currentSeries.getSlices().get(index);
    }

    public Image getSliceImage(int index) {
        DicomSlice slice = getSlice(index);
        if (slice == null) return null;
        return ImageConverter.toFX(slice.getImage());
    }

    private Mat currentMask; // Храним текущую маску

    public void setMask(Mat mask) {
        this.currentMask = mask;
    }

    public Mat getCurrentMask() {
        return currentMask;
    }
}