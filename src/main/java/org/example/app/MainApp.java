package org.example.app;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.controller.SeriesController;
import org.example.model.DicomSlice;
import org.example.opencv.MatConverter;
import org.example.opencv.OpenCVFilters;
import org.example.opencv.OpenCVUtils;
import org.example.util.ImageConverter;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;

import java.io.File;

public class MainApp extends Application {

    static {
        try {
            // Пытаемся загрузить через системное свойство
            nu.pattern.OpenCV.loadLocally();
            System.out.println("OpenCV loaded successfully via OpenCV.loadLocally()");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Failed to load OpenCV: " + e.getMessage());
            // Fallback
            System.loadLibrary(Core.NATIVE_LIBRARY_NAME);
        }
    }

    private final ImageView originalView = new ImageView();
    private final ImageView processedView = new ImageView();

    private final Slider sliceSlider = new Slider();
    private final Slider thresholdMinSlider = new Slider(0, 255, 50);
    private final Slider thresholdMaxSlider = new Slider(0, 255, 200);
    private final Slider blurGaussianSlider = new Slider(0, 20, 1);

    private final TilePane sliceTilePane = new TilePane();
    private final ScrollPane sliceScrollPane = new ScrollPane(sliceTilePane);

    private final Label fileNameLabel = new Label("File:");
    private final Label sliceLocationLabel = new Label("Slice Location:");
    private final Label orientationLabel = new Label("Orientation:");

    private final SeriesController controller = new SeriesController();

    private final String DATA_PATH = "./src/main/resources/mri/media";

    private double thresholdMinValue = 0;
    private double thresholdMaxValue = 0;

    @Override
    public void start(Stage stage) {

        VBox slidersBox = createSlidersPanel();
        HBox imagesBox = createImagesPanel();
        VBox seriesBox = createSeriesPanel();
        VBox topBox = createTopPanel();

        BorderPane root = new BorderPane();
        root.setLeft(slidersBox);
        root.setCenter(imagesBox);
        root.setRight(seriesBox);
        root.setTop(topBox);

        Scene scene = new Scene(root, 1100, 550);

        stage.setTitle("DICOM Viewer");
        stage.setScene(scene);
        stage.show();
    }

    private VBox createSlidersPanel() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setPrefWidth(160);

        Label thresholdMinLabel = new Label("Threshold Min: 50");
        Label thresholdMaxLabel = new Label("Threshold Max: 200");
        Label blurGaussianLabel = new Label("blurGaussian: 1");

        thresholdMinSlider.valueProperty().addListener((obs, oldVal, newVal) -> {

            if (newVal.doubleValue() > thresholdMaxSlider.getValue()) {
                thresholdMinSlider.setValue(thresholdMaxSlider.getValue());
                return;
            }

            thresholdMinLabel.setText("Threshold Min: " + newVal.intValue());
            updateProcessedImage();
        });

        thresholdMaxSlider.valueProperty().addListener((obs, oldVal, newVal) -> {

            if (newVal.doubleValue() < thresholdMinSlider.getValue()) {
                thresholdMaxSlider.setValue(thresholdMinSlider.getValue());
                return;
            }

            thresholdMaxLabel.setText("Threshold Max: " + newVal.intValue());
            updateProcessedImage();
        });

        blurGaussianSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            blurGaussianLabel.setText("blurGaussian: " + String.format("%.1f", newVal.doubleValue()));
            updateProcessedImage();
        });

        box.getChildren().addAll(
                thresholdMinLabel,
                thresholdMinSlider,
                thresholdMaxLabel,
                thresholdMaxSlider,
                blurGaussianLabel,
                blurGaussianSlider
        );
        return box;
    }

    private HBox createImagesPanel() {
        HBox box = new HBox(10);
        originalView.setFitWidth(350);
        originalView.setFitHeight(350);
        processedView.setFitWidth(350);
        processedView.setFitHeight(350);
        box.getChildren().addAll(originalView, processedView);
        return box;
    }

    private VBox createSeriesPanel() {
        VBox box = new VBox(10);
        box.setPadding(new Insets(10));
        box.setPrefWidth(220);

        // Настройка TilePane для миниатюр
        sliceTilePane.setHgap(5);
        sliceTilePane.setVgap(5);
        sliceTilePane.setPadding(new Insets(5));
        sliceTilePane.setPrefColumns(5);

        sliceScrollPane.setFitToWidth(true);
        sliceScrollPane.setPrefHeight(400);

        loadSeriesFolders();

        box.getChildren().addAll(new Label("Slices"), sliceScrollPane);
        return box;
    }

    private VBox createTopPanel() {
        sliceSlider.setMin(0);
        sliceSlider.setValue(0);
        sliceSlider.setShowTickMarks(true);
        sliceSlider.setShowTickLabels(true);
        sliceSlider.setMajorTickUnit(1);
        sliceSlider.setMinorTickCount(0);
        sliceSlider.setBlockIncrement(1);

        sliceSlider.valueProperty().addListener(
                (obs, oldVal, newVal) -> showSlice(newVal.intValue())
        );

        VBox box = new VBox(5);
        box.setPadding(new Insets(10));
        box.getChildren().addAll(
                sliceSlider,
                fileNameLabel,
                sliceLocationLabel,
                orientationLabel
        );
        return box;
    }

    private void loadSeriesFolders() {
        File folder = new File(DATA_PATH);
        File[] dirs = folder.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return;

        // Загружаем первую серию по умолчанию
        loadSeries(dirs[2]);
    }

    private void loadSeries(File folder) {
        controller.loadSeries(folder);

        sliceTilePane.getChildren().clear();

        int sliceCount = controller.getSliceCount();
        if (sliceCount == 0) return;

        for (int i = 0; i < sliceCount; i++) {
            DicomSlice slice = controller.getSlice(i);

            ImageView iv = new ImageView(ImageConverter.toFX(slice.getImage()));
            iv.setFitWidth(30);
            iv.setFitHeight(30);

            int index = i; // для лямбды
            iv.setOnMouseClicked(e -> {
                showSlice(index);
                sliceSlider.setValue(index);
            });

            sliceTilePane.getChildren().add(iv);
        }

        // Настройка слайдера динамически
        sliceSlider.setMin(0);
        sliceSlider.setMax(sliceCount - 1);
        sliceSlider.setValue(0);

        thresholdMinSlider.setMin(controller.getSeriesMin());
        thresholdMinSlider.setMax(controller.getSeriesMax());

        thresholdMaxSlider.setMin(controller.getSeriesMin());
        thresholdMaxSlider.setMax(controller.getSeriesMax());

        thresholdMinSlider.setValue(controller.getSeriesMin());
        thresholdMaxSlider.setValue(controller.getSeriesMax());

        showSlice(0);
    }

    private void showSlice(int index) {
        DicomSlice slice = controller.getSlice(index);
        if (slice == null) return;

        originalView.setImage(controller.getSliceImage(index));
//        processedView.setImage(controller.getSliceImage(index));

        updateProcessedImage();

        fileNameLabel.setText("File: " + slice.getFile().getName());
        sliceLocationLabel.setText("Slice Location: " + slice.getSliceLocation());
        orientationLabel.setText("Orientation: " + slice.getOrientation());
    }

    private void updateProcessedImage() {

        int index = (int) sliceSlider.getValue();
        DicomSlice slice = controller.getSlice(index);
        if (slice == null) return;

        ImagePlus imp = slice.getImage();
        if (imp == null) return;

        try {

            Mat mat16 = OpenCVUtils.imagePlusToMat(imp);

            double minVal = thresholdMinSlider.getValue();
            double maxVal = thresholdMaxSlider.getValue();

            if (maxVal <= minVal) return;

            // Gaussian
            double blurVal = blurGaussianSlider.getValue();
            if (blurVal > 0) {
                mat16 = OpenCVFilters.gaussianBlur(mat16, blurVal);
            }

            // Median
            mat16 = OpenCVFilters.medianFilter(mat16, 3);

            Mat windowed = new Mat();

            double scale = 65535.0 / (maxVal - minVal);
            double shift = -minVal * scale;

            mat16.convertTo(windowed, CvType.CV_16U, scale, shift);

            Core.min(windowed, new Scalar(65535), windowed);
            Core.max(windowed, new Scalar(0), windowed);

            Image fxImage = MatConverter.toFXImage(windowed);

            if (fxImage != null) {
                processedView.setImage(fxImage);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch();
    }
}