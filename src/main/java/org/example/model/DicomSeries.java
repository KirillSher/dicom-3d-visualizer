package org.example.model;

import java.util.List;

public class DicomSeries {

    private final String name;
    private final List<DicomSlice> slices;

    public DicomSeries(String name, List<DicomSlice> slices) {
        this.name = name;
        this.slices = slices;
    }

    public String getName() {
        return name;
    }

    public List<DicomSlice> getSlices() {
        return slices;
    }
}