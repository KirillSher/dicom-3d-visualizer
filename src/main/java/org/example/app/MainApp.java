package org.example.app;

import ij.ImagePlus;
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
import org.example.util.ImageConverter;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

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
    private final Slider thresholdMinSlider = new Slider(0, 255, 0);
    private final Slider thresholdMaxSlider = new Slider(0, 255, 255);

    private final TilePane sliceTilePane = new TilePane();
    private final ScrollPane sliceScrollPane = new ScrollPane(sliceTilePane);

    private final Label fileNameLabel = new Label("File:");
    private final Label sliceLocationLabel = new Label("Slice Location:");
    private final Label orientationLabel = new Label("Orientation:");

    private final SeriesController controller = new SeriesController();

//    private final String DATA_PATH = "./src/main/resources/mri/media";

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

        Label thresholdMinLabel = new Label("Threshold Min: 0");
        Label thresholdMaxLabel = new Label("Threshold Max: 255");

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

        box.getChildren().addAll(
                thresholdMinLabel,
                thresholdMinSlider,
                thresholdMaxLabel,
                thresholdMaxSlider
        );
        return box;
    }

    private HBox createImagesPanel() {
        HBox box = new HBox(10);

        originalView.setFitWidth(350);
        originalView.setFitHeight(350);
        originalView.setPreserveRatio(true);

        processedView.setFitWidth(350);
        processedView.setFitHeight(350);
        processedView.setPreserveRatio(true);

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
        File folder = new File("./src/main/resources/mri/media");
        File[] dirs = folder.listFiles(File::isDirectory);
        if (dirs == null || dirs.length == 0) return;

        // Загружаем определенный source
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

        // Настройка слайдера
        sliceSlider.setMin(0);
        sliceSlider.setMax(sliceCount - 1);
        sliceSlider.setValue(0);

        thresholdMinSlider.setMin(0);
        thresholdMinSlider.setMax(255);

        thresholdMaxSlider.setMin(0);
        thresholdMaxSlider.setMax(255);

        thresholdMinSlider.setValue(0);
        thresholdMaxSlider.setValue(255);

        showSlice(0);
    }

    private void showSlice(int index) {
        DicomSlice slice = controller.getSlice(index);
        if (slice == null) return;

        originalView.setImage(controller.getSliceImage(index));

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
            Mat mat = slice.getMat8();

            double minVal = thresholdMinSlider.getValue();
            double maxVal = thresholdMaxSlider.getValue();

            if (maxVal <= minVal) {
                processedView.setImage(null);
                return;
            }

            Mat result = mat.clone();

            // Полосовой фильтр
            Imgproc.threshold(result, result, minVal, 255, Imgproc.THRESH_TOZERO);
            Imgproc.threshold(result, result, maxVal, 255, Imgproc.THRESH_TOZERO_INV);

            Image fxImage = MatConverter.toFXImage(result);
            if (fxImage != null) processedView.setImage(fxImage);

        } catch (Exception e) {
            System.err.println("Error processing image: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch();
    }
}