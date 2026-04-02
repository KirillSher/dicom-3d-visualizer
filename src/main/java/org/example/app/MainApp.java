package org.example.app;

import ij.ImagePlus;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.controller.SeriesController;
import org.example.model.DicomSlice;
import org.example.opencv.MatConverter;
import org.example.ui.PolygonDrawer;
import org.example.util.ImageConverter;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.util.List;

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

    private PolygonDrawer drawer;
    private ToggleButton drawToggle;

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
            // При изменении фильтра сбрасываем полигон и выключаем режим рисования
            if (drawer != null) {
                drawer.clear();
                drawer.setDrawingEnabled(false);
            }
            resetDrawingUI();
        });

        thresholdMaxSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() < thresholdMinSlider.getValue()) {
                thresholdMaxSlider.setValue(thresholdMinSlider.getValue());
                return;
            }
            thresholdMaxLabel.setText("Threshold Max: " + newVal.intValue());
            updateProcessedImage();
            // При изменении фильтра сбрасываем полигон и выключаем режим рисования
            if (drawer != null) {
                drawer.clear();
                drawer.setDrawingEnabled(false);
            }
            resetDrawingUI();
        });

        // Создаем ToggleButton для режима рисования
        ToggleButton drawToggle = new ToggleButton("Рисование: ВЫКЛ");
        Button clearPolygonBtn = new Button("Сбросить полигон");
        Button applyMaskBtn = new Button("Применить маску");

        // Toggle логика
        drawToggle.setOnAction(e -> {
            boolean enabled = drawToggle.isSelected();

            if (drawer != null) {
                drawer.setDrawingEnabled(enabled);
            }

            if (enabled) {
                drawToggle.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white;");
                drawToggle.setText("Рисование: ВКЛ");
            } else {
                drawToggle.setStyle("");
                drawToggle.setText("Рисование: ВЫКЛ");
            }
        });
        // Сброс
        clearPolygonBtn.setOnAction(e -> {
            if (drawer != null) {
                drawer.clear();
                drawer.setDrawingEnabled(false);
                resetDrawingUI();
                System.out.println("Полигон сброшен");
            }
        });

        // Применить маску
        applyMaskBtn.setOnAction(e -> {
            if (drawer != null && drawer.isClosed()) {
                applyMaskToImage();
                drawer.clear();
                resetDrawingUI();
                System.out.println("Маска применена");
            } else if (drawer != null) {
                System.out.println("Сначала замкните полигон!");
            }
        });

        // Добавляем разделитель
        Separator separator = new Separator();

        // Сохраняем кнопку в поле класса для доступа из других методов
        this.drawToggle = drawToggle;

        box.getChildren().addAll(
                thresholdMinLabel,
                thresholdMinSlider,
                thresholdMaxLabel,
                thresholdMaxSlider,
                separator,
                drawToggle,
                clearPolygonBtn,
                applyMaskBtn
        );
        return box;
    }

    private HBox createImagesPanel() {
        HBox box = new HBox(10);

        // ORIGINAL
        originalView.setFitWidth(350);
        originalView.setFitHeight(350);
        originalView.setPreserveRatio(false);

        // PROCESSED
        processedView.setFitWidth(350);
        processedView.setFitHeight(350);
        processedView.setPreserveRatio(false);

        Canvas canvas = new Canvas();

        // ❗ ВАЖНО: подгоняем Canvas под РЕАЛЬНОЕ изображение
        processedView.imageProperty().addListener((obs, oldImg, newImg) -> {
            if (newImg != null) {
                canvas.setWidth(newImg.getWidth());
                canvas.setHeight(newImg.getHeight());
            }
        });

        // ❗ ВАЖНО: НЕ центр, а левый верх
        StackPane processedStack = new StackPane();
        processedStack.getChildren().addAll(processedView, canvas);

        StackPane.setAlignment(processedView, javafx.geometry.Pos.TOP_LEFT);
        StackPane.setAlignment(canvas, javafx.geometry.Pos.TOP_LEFT);

        // ❗ фиксируем размер контейнера
        processedStack.setPrefSize(350, 350);
        processedStack.setMinSize(350, 350);
        processedStack.setMaxSize(350, 350);

        drawer = new PolygonDrawer(canvas, processedView);

        drawer.setOnPolygonClosed(() -> {
            System.out.println("Полигон замкнут!");
        });

        box.getChildren().addAll(originalView, processedStack);

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
        if (drawer != null) {
            drawer.clear();
            drawer.setDrawingEnabled(false);
        }
        resetDrawingUI();

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

    private void applyMaskToImage() {
        int index = (int) sliceSlider.getValue();
        DicomSlice slice = controller.getSlice(index);
        if (slice == null) return;

        try {
            // Получаем оригинальное изображение
            Mat originalMat = slice.getMat8();

            // Получаем обработанное изображение
            Mat processedMat = originalMat.clone();

            // Применяем полосовой фильтр (как в updateProcessedImage)
            double minVal = thresholdMinSlider.getValue();
            double maxVal = thresholdMaxSlider.getValue();

            Imgproc.threshold(processedMat, processedMat, minVal, 255, Imgproc.THRESH_TOZERO);
            Imgproc.threshold(processedMat, processedMat, maxVal, 255, Imgproc.THRESH_TOZERO_INV);

            // Получаем точки полигона в координатах изображения
            List<org.opencv.core.Point> imagePoints = drawer.getImagePoints(
                    processedMat.width(),
                    processedMat.height()
            );

            System.out.println(imagePoints);

            if (imagePoints.size() < 3) {
                System.out.println("Нужно минимум 3 точки для полигона");
                return;
            }

            // Создаем маску
            Mat mask = Mat.zeros(processedMat.size(), CvType.CV_8UC1);

            // Конвертируем точки в MatOfPoint
            MatOfPoint polygon = new MatOfPoint();
            polygon.fromList(imagePoints);

            // Создаем список полигонов
            List<MatOfPoint> polygons = List.of(polygon);

            // Заполняем полигон белым цветом (255)
            Imgproc.fillPoly(mask, polygons, new Scalar(255));

            // Применяем маску: оставляем только то, что внутри полигона
            Mat result = new Mat();
            processedMat.copyTo(result, mask);
            processedMat.release();

            // Показываем результат
            Image fxImage = MatConverter.toFXImage(result);
            if (fxImage != null) {
                processedView.setImage(fxImage);
            }

            // Освобождаем ресурсы
            result.release();
            mask.release();

        } catch (Exception e) {
            System.err.println("Ошибка при применении маски: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void resetDrawingUI() {
        if (drawToggle != null) {
            drawToggle.setSelected(false);
            drawToggle.setStyle("");
            drawToggle.setText("Рисование: ВЫКЛ");
        }
    }

    public static void main(String[] args) {
        launch();
    }
}