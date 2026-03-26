package org.example.opencv;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import org.opencv.core.Mat;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;

public class MatConverter {

    public static Image toFXImage(Mat mat) {

        int width = mat.cols();
        int height = mat.rows();

        // 👉 8-bit grayscale
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);

        byte[] data = ((DataBufferByte) img.getRaster().getDataBuffer()).getData();

        mat.get(0, 0, data);

        return SwingFXUtils.toFXImage(img, null);
    }
}