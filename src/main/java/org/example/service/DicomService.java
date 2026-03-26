package org.example.service;

import ij.IJ;
import ij.ImagePlus;
import org.example.model.DicomSeries;
import org.example.model.DicomSlice;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DicomService {

    public DicomSeries loadSeries(File folder) {

        File[] files = folder.listFiles();

        List<DicomSlice> slices = new ArrayList<>();

        if (files == null) {
            return new DicomSeries(folder.getName(), slices);
        }

        for (File file : files) {

            ImagePlus img = IJ.openImage(file.getAbsolutePath());

            if (img == null) continue;

            Object info = img.getProperty("Info");

            if (info == null) continue;

            String dicomText = info.toString();

            double sliceLocation = parseDouble(getTag(dicomText, "0020,1041"));
            String orientation = getTag(dicomText, "0020,0037");

            slices.add(new DicomSlice(
                    file,
                    sliceLocation,
                    orientation,
                    img
            ));
        }

        // сортировка срезов
        slices.sort(Comparator.comparingDouble(DicomSlice::getSliceLocation));

        return new DicomSeries(folder.getName(), slices);
    }

    private String getTag(String dicomText, String tag) {

        for (String line : dicomText.split("\n")) {

            if (line.contains(tag)) {

                int colon = line.indexOf(":");

                if (colon > 0) {
                    return line.substring(colon + 1).trim();
                }
            }
        }

        return null;
    }

    private double parseDouble(String value) {

        if (value == null) return Double.MAX_VALUE;

        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return Double.MAX_VALUE;
        }
    }
}