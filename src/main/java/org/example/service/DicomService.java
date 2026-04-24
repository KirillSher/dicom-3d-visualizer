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

//            System.out.println(info);

            if (info == null) continue;

            String dicomText = info.toString();

            double sliceLocation = parseDouble(getTag(dicomText, "0020,1041"));
            String orientation = getTag(dicomText, "0020,0037");

            // Pixel Spacing (0028,0030) -> "0.5\0.5"
            String pixelSpacingStr = getTag(dicomText, "0028,0030");

            double pixelSpacingX = 1.0;
            double pixelSpacingY = 1.0;

            if (pixelSpacingStr != null && pixelSpacingStr.contains("\\")) {
                String[] parts = pixelSpacingStr.split("\\\\");
                if (parts.length == 2) {
                    pixelSpacingY = parseDouble(parts[0]); // обычно Y
                    pixelSpacingX = parseDouble(parts[1]); // обычно X
                }
            }

            // Slice Thickness (0018,0050)
            double sliceThickness = parseDouble(getTag(dicomText, "0018,0050"));

            // fallback если нет
            if (sliceThickness == Double.MAX_VALUE) {
                sliceThickness = 1.0;
            }

            slices.add(new DicomSlice(
                    file,
                    sliceLocation,
                    orientation,
                    img,
                    pixelSpacingX,
                    pixelSpacingY,
                    sliceThickness
            ));

//            System.out.println("Spacing: "
//                    + pixelSpacingX + " x "
//                    + pixelSpacingY + " x "
//                    + sliceThickness);
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