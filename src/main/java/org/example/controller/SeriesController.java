package org.example.controller;

import ij.process.ImageProcessor;
import javafx.scene.image.Image;
import org.example.model.DicomSeries;
import org.example.model.DicomSlice;
import org.example.service.DicomService;
import org.example.util.ImageConverter;

import java.io.File;

public class SeriesController {

    private final DicomService dicomService = new DicomService();

    private DicomSeries currentSeries;

    private double seriesMin = Double.MAX_VALUE;
    private double seriesMax = Double.MIN_VALUE;

    public void loadSeries(File folder) {

        currentSeries = dicomService.loadSeries(folder);

        seriesMin = Double.MAX_VALUE;
        seriesMax = Double.MIN_VALUE;

        for (DicomSlice slice : currentSeries.getSlices()) {

            ImageProcessor ip = slice.getImage().getProcessor();

            seriesMin = Math.min(seriesMin, ip.getMin());
            seriesMax = Math.max(seriesMax, ip.getMax());
        }

        System.out.println("Series Min: " + seriesMin);
        System.out.println("Series Max: " + seriesMax);
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

    public int indexOf(DicomSlice slice) {
        if (currentSeries == null || slice == null) return -1;
        return currentSeries.getSlices().indexOf(slice);
    }

    public double getSeriesMin() {
        return seriesMin;
    }

    public double getSeriesMax() {
        return seriesMax;
    }
}