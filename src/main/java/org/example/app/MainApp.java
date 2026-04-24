package org.example.app;

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
import org.example.threed.MarchingCubesVolumeRenderer;
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
    private ComboBox<String> displayModeBox;

    private Stage primaryStage;

    @Override
    public void start(Stage stage) {

        this.primaryStage = stage;

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

        Label thresholdMinLabel = new Label("IntensityRangeMin: 0");
        Label thresholdMaxLabel = new Label("IntensityRangeMax: 255");

        thresholdMinSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal.doubleValue() > thresholdMaxSlider.getValue()) {
                thresholdMinSlider.setValue(thresholdMaxSlider.getValue());
                return;
            }
            thresholdMinLabel.setText("IntensityRangeMin: " + newVal.intValue());
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
            thresholdMaxLabel.setText("IntensityRangeMax: " + newVal.intValue());
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

        Label modeLabel = new Label("Display Mode");

        displayModeBox = new ComboBox<>();
        displayModeBox.getItems().addAll(
                "Grayscale",
                "Tissue coloring"
        );

        displayModeBox.setValue("Grayscale");

        displayModeBox.setOnAction(e -> updateProcessedImage());

        // Добавляем разделитель
        Separator separator = new Separator();

        // Сохраняем кнопку в поле класса для доступа из других методов
        this.drawToggle = drawToggle;

        Button clearMaskBtn = new Button("Сбросить маску");
        clearMaskBtn.setOnAction(e -> {
            controller.setMask(null);
            updateProcessedImage();
            System.out.println("Маска сброшена");
        });

        Button show3DButton = new Button("Показать 3D модель");
        show3DButton.setOnAction(e -> {
            if (controller.getCurrentSeries() != null) {
                MarchingCubesVolumeRenderer renderer = new MarchingCubesVolumeRenderer();
                renderer.showVolume(primaryStage, controller.getCurrentSeries());
            }
        });

        box.getChildren().addAll(
                thresholdMinLabel,
                thresholdMinSlider,
                thresholdMaxLabel,
                thresholdMaxSlider,

                modeLabel,
                displayModeBox,

                separator,
                drawToggle,
                clearPolygonBtn,
                applyMaskBtn,
                clearMaskBtn,

                new Separator(),
                show3DButton
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
        for (int i = 0; i < 5; i++) {
            System.out.println(dirs[i]);
        }
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
        // НЕ сбрасываем маску полностью
        if (drawer != null) {
            drawer.setDrawingEnabled(false);
        }
        resetDrawingUI(); // только UI кнопки

        DicomSlice slice = controller.getSlice(index);
        if (slice == null) return;

        originalView.setImage(controller.getSliceImage(index));
        updateProcessedImage(); // здесь будет применяться сохранённая маска

        fileNameLabel.setText("File: " + slice.getFile().getName());
        sliceLocationLabel.setText("Slice Location: " + slice.getSliceLocation());
        orientationLabel.setText("Orientation: " + slice.getOrientation());
    }

    private void updateProcessedImage() {
        int index = (int) sliceSlider.getValue();
        DicomSlice slice = controller.getSlice(index);
        if (slice == null) return;

        Mat original = slice.getMat8();
        double minVal = thresholdMinSlider.getValue();
        double maxVal = thresholdMaxSlider.getValue();

        // Конвертация в 8-бит для отображения
        Mat temp = new Mat();
        original.convertTo(temp, CvType.CV_8U);

        // Создаем цветное изображение (по умолчанию grayscale)
        Mat colorResult = new Mat();
        Imgproc.cvtColor(temp, colorResult, Imgproc.COLOR_GRAY2BGR);

        // Проверяем режим отображения
        String mode = displayModeBox.getValue();
        if ("Tissue coloring".equals(mode)) {
            // Нормализация в диапазон 0-255
            Mat norm = new Mat();
            original.convertTo(norm, CvType.CV_32F);
            Core.subtract(norm, new Scalar(minVal), norm);
            Core.divide(norm, new Scalar(Math.max(maxVal - minVal, 1)), norm);
            Core.multiply(norm, new Scalar(255), norm);
            Core.min(norm, new Scalar(255), norm);
            Core.max(norm, new Scalar(0), norm);
            norm.convertTo(norm, CvType.CV_8U);

            // Создаем маски для тканей
            Mat maskAir = new Mat();
            Mat maskLung = new Mat();
            Mat maskFat = new Mat();
            Mat maskTissue = new Mat();
            Mat maskBone = new Mat();

            Core.inRange(norm, new Scalar(0), new Scalar(30), maskAir);
            Core.inRange(norm, new Scalar(31), new Scalar(80), maskLung);
            Core.inRange(norm, new Scalar(81), new Scalar(130), maskFat);
            Core.inRange(norm, new Scalar(131), new Scalar(180), maskTissue);
            Core.inRange(norm, new Scalar(181), new Scalar(255), maskBone);

            // Цвета тканей
            colorResult.setTo(new Scalar(0, 0, 0), maskAir);
            colorResult.setTo(new Scalar(255, 200, 100), maskLung);
            colorResult.setTo(new Scalar(200, 230, 255), maskFat);
            colorResult.setTo(new Scalar(80, 80, 180), maskTissue);
            colorResult.setTo(new Scalar(255, 255, 255), maskBone);

            // Освобождаем временные матрицы
            norm.release();
            maskAir.release();
            maskLung.release();
            maskFat.release();
            maskTissue.release();
            maskBone.release();
        }

        Mat maskRange = new Mat();
        Core.inRange(temp, new Scalar(minVal), new Scalar(maxVal), maskRange);

        Mat filtered = new Mat();
        colorResult.copyTo(filtered, maskRange);

        temp.release();

        // ✅ Применяем пользовательскую маску (полигон) правильно
        Mat currentMask = controller.getCurrentMask();
        Mat finalResult = new Mat();
        if (currentMask != null && !currentMask.empty()) {
            filtered.copyTo(finalResult, currentMask);
        } else {
            finalResult = filtered.clone();
        }

        // Отображаем в JavaFX
        Image fxImage = MatConverter.toFXImageColor(finalResult);
        processedView.setImage(fxImage);

        // Освобождаем ресурсы
        maskRange.release();
        filtered.release();

        finalResult.release();
        colorResult.release();
    }

    private void applyMaskToImage() {
        int index = (int) sliceSlider.getValue();
        DicomSlice slice = controller.getSlice(index);
        if (slice == null) return;

        try {
            // Получаем точки полигона в координатах изображения
            Mat originalMat = slice.getMat8();
            List<org.opencv.core.Point> imagePoints = drawer.getImagePoints(
                    originalMat.width(),
                    originalMat.height()
            );

            System.out.println(imagePoints);

            if (imagePoints.size() < 3) {
                System.out.println("Нужно минимум 3 точки для полигона");
                return;
            }

            // Создаем маску (только маску, без применения фильтра!)
            Mat mask = Mat.zeros(originalMat.size(), CvType.CV_8UC1);

            // Конвертируем точки в MatOfPoint
            MatOfPoint polygon = new MatOfPoint();
            polygon.fromList(imagePoints);

            // Создаем список полигонов
            List<MatOfPoint> polygons = List.of(polygon);

            // Заполняем полигон белым цветом (255)
            Imgproc.fillPoly(mask, polygons, new Scalar(255));

            // Сохраняем маску в контроллер
            controller.setMask(mask);

            System.out.println("Маска сохранена, размер: " + mask.width() + "x" + mask.height());

            // Очищаем UI рисования
            if (drawer != null) {
                drawer.clear();
            }
            resetDrawingUI();

            // Обновляем отображение (применится и фильтр, и маска)
            updateProcessedImage();

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