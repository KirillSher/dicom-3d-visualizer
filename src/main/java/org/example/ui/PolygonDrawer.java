package org.example.ui;

import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

public class PolygonDrawer {

    private final Canvas canvas;
    private final GraphicsContext gc;

    private final List<Point2D> points = new ArrayList<>();

    private boolean isClosed = false;
    private boolean drawingEnabled = false; // По умолчанию выключено
    private static final double CLOSE_THRESHOLD = 15; // пикселей

    // Callback для уведомления о замыкании полигона
    private Runnable onPolygonClosed;

    private final ImageView imageView;

    public PolygonDrawer(Canvas canvas, ImageView imageView) {
        this.canvas = canvas;
        this.imageView = imageView;
        this.gc = canvas.getGraphicsContext2D();
        setupMouse();

        canvas.widthProperty().addListener((obs, oldVal, newVal) -> draw());
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> draw());
    }

    public void setDrawingEnabled(boolean enabled) {
        this.drawingEnabled = enabled;
        if (!enabled) {
            // Если выключаем рисование, можно оставить полигон или очистить
            // По желанию: clear();
            clear();
        }
        draw(); // Перерисовываем (можем показать статус)
    }

    public void setOnPolygonClosed(Runnable callback) {
        this.onPolygonClosed = callback;
    }

    private void setupMouse() {
        canvas.setOnMouseClicked(e -> {
            if (!drawingEnabled || isClosed) {
                return; // Рисование выключено или полигон уже замкнут
            }

            Point2D newPoint = new Point2D(e.getX(), e.getY());

            // Проверка на замыкание (если есть хотя бы 2 точки)
            if (points.size() >= 2) {
                Point2D firstPoint = points.get(0);
                double distance = firstPoint.distance(newPoint);

                if (distance < CLOSE_THRESHOLD) {
                    // Замыкаем полигон
                    isClosed = true;
                    draw();
                    if (onPolygonClosed != null) {
                        onPolygonClosed.run();
                    }
                    return;
                }
            }

            points.add(newPoint);
            draw();
        });
    }

    private void draw() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());

        // Если рисование выключено и нет полигона - ничего не рисуем
        if (!drawingEnabled && points.isEmpty()) return;

        // Рисуем точки
        gc.setFill(Color.RED);
        for (Point2D p : points) {
            gc.fillOval(p.getX() - 4, p.getY() - 4, 8, 8);
        }

        // Рисуем линии
        if (points.size() >= 2) {
            if (isClosed) {
                gc.setStroke(Color.GREEN);
                gc.setLineWidth(3);
            } else {
                gc.setStroke(Color.YELLOW);
                gc.setLineWidth(2);
            }

            // Рисуем линии между последовательными точками
            for (int i = 0; i < points.size() - 1; i++) {
                Point2D p1 = points.get(i);
                Point2D p2 = points.get(i + 1);
                gc.strokeLine(p1.getX(), p1.getY(), p2.getX(), p2.getY());
            }

            // Если полигон замкнут, рисуем последнюю линию к первой
            if (isClosed && points.size() >= 3) {
                Point2D first = points.get(0);
                Point2D last = points.get(points.size() - 1);
                gc.strokeLine(last.getX(), last.getY(), first.getX(), first.getY());
            }
        }

        // Если полигон замкнут, заливаем полупрозрачным цветом для наглядности
        if (isClosed && points.size() >= 3) {
            gc.setFill(Color.rgb(0, 255, 0, 0.2));
            // Рисуем заливку полигона
            double[] xPoints = points.stream().mapToDouble(Point2D::getX).toArray();
            double[] yPoints = points.stream().mapToDouble(Point2D::getY).toArray();
            gc.fillPolygon(xPoints, yPoints, points.size());
        }

        // Показываем статус рисования
        if (!drawingEnabled && points.isEmpty()) {
            gc.setFill(Color.GRAY);
            gc.setFont(javafx.scene.text.Font.font(12));
            gc.fillText("Рисование выключено", 10, 20);
        } else if (drawingEnabled && !isClosed) {
            gc.setFill(Color.BLUE);
            gc.setFont(javafx.scene.text.Font.font(12));
            gc.fillText("Кликайте для построения полигона", 10, 20);
        } else if (isClosed) {
            gc.setFill(Color.GREEN);
            gc.setFont(javafx.scene.text.Font.font(12));
            gc.fillText("Полигон замкнут", 10, 20);
        }
    }

    public List<org.opencv.core.Point> getImagePoints(double imageWidth, double imageHeight) {
        List<org.opencv.core.Point> imagePoints = new ArrayList<>();

        Image img = imageView.getImage();
        if (img == null) return imagePoints;

        double viewWidth = imageView.getFitWidth();
        double viewHeight = imageView.getFitHeight();

        double imgWidth = img.getWidth();
        double imgHeight = img.getHeight();

        double scaleX = imgWidth / viewWidth;
        double scaleY = imgHeight / viewHeight;

        for (Point2D p : points) {
            double x = p.getX() * scaleX;
            double y = p.getY() * scaleY;

            imagePoints.add(new org.opencv.core.Point(x, y));
        }

        return imagePoints;
    }

    public boolean isClosed() {
        return isClosed;
    }

    public boolean isDrawingEnabled() {
        return drawingEnabled;
    }

    public List<Point2D> getPoints() {
        return points;
    }

    public void clear() {
        points.clear();
        isClosed = false;
        drawingEnabled = false; // После сброса выключаем рисование
        draw();
    }

    public void reset() {
        clear();
    }
}