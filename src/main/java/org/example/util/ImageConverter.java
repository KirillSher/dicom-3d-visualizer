package org.example.util;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

import java.awt.image.BufferedImage;

public class ImageConverter {

    public static Image toFX(ImagePlus img) {

        if (img == null) return null;

        ImageProcessor processor = img.getProcessor();

        BufferedImage bufferedImage = processor.getBufferedImage();

        return SwingFXUtils.toFXImage(bufferedImage, null);
    }
}