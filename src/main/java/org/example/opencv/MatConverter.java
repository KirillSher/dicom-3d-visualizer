package org.example.opencv;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferUShort;

public class MatConverter {

    public static Image toFXImage(Mat mat) {

        Mat normalized = new Mat();

        org.opencv.core.Core.normalize(
                mat,
                normalized,
                0,
                65535,
                org.opencv.core.Core.NORM_MINMAX
        );

        int width = normalized.cols();
        int height = normalized.rows();

        BufferedImage img =
                new BufferedImage(width, height, BufferedImage.TYPE_USHORT_GRAY);

        short[] data =
                ((DataBufferUShort) img.getRaster().getDataBuffer()).getData();

        normalized.get(0, 0, data);

        return SwingFXUtils.toFXImage(img, null);
    }
}