package org.example.threed;

import javafx.scene.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.transform.Rotate;
import javafx.stage.Stage;
import org.example.model.DicomSeries;
import org.example.model.DicomSlice;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MarchingCubesVolumeRenderer {

    private final ExecutorService executor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()
    );

    public void showVolume(Stage parentStage, DicomSeries series) {
        Stage volumeStage = new Stage();
        volumeStage.setTitle("3D Визуализация (Marching Cubes)");
        volumeStage.initOwner(parentStage);

        Group root3D = new Group();

        PerspectiveCamera camera = new PerspectiveCamera(true);
        camera.setNearClip(0.1);
        camera.setFarClip(10000);
        camera.setTranslateZ(-400);

        Scene scene = new Scene(root3D, 900, 700, true, SceneAntialiasing.BALANCED);
        scene.setFill(Color.BLACK);
        scene.setCamera(camera);

        addLighting(root3D);
        addCoordinateAxes(root3D);

        Group modelGroup = new Group();
        root3D.getChildren().add(modelGroup);

        volumeStage.setScene(scene);
        volumeStage.show();

        javafx.scene.control.Label loadingLabel = new javafx.scene.control.Label(
                "Построение поверхностей (Marching Cubes)...");
        loadingLabel.setTextFill(Color.WHITE);
        loadingLabel.setTranslateX(-100);
        loadingLabel.setTranslateY(-10);
        root3D.getChildren().add(loadingLabel);

        CompletableFuture.supplyAsync(() -> buildMarchingCubesModel(series), executor)
                .thenAccept(meshViews -> {
                    javafx.application.Platform.runLater(() -> {
                        root3D.getChildren().remove(loadingLabel);
                        modelGroup.getChildren().addAll(meshViews);

                        MouseController mouseController = new MouseController(modelGroup, scene);

                        System.out.println("Модель построена. Объектов: " + meshViews.size());
                    });
                })
                .exceptionally(e -> {
                    javafx.application.Platform.runLater(() -> {
                        root3D.getChildren().remove(loadingLabel);
                        loadingLabel.setText("Ошибка: " + e.getMessage());
                        loadingLabel.setTextFill(Color.RED);
                    });
                    e.printStackTrace();
                    return null;
                });
    }

    private List<MeshView> buildMarchingCubesModel(DicomSeries series) {
        List<DicomSlice> slices = series.getSlices();

        DicomSlice first = slices.get(0);

        float spacingX = (float) first.getPixelSpacingX();
        float spacingY = (float) first.getPixelSpacingY();
        float spacingZ = (float) first.getSliceThickness();

        // нормализация (чтобы модель не была огромной)
        float scaleFactor = 1.0f / Math.max(Math.max(spacingX, spacingY), spacingZ);

        float voxelSizeX = spacingX * scaleFactor;
        float voxelSizeY = spacingY * scaleFactor;
        float voxelSizeZ = spacingZ * scaleFactor;

        System.out.println("Voxel size: " + voxelSizeX + ", " + voxelSizeY + ", " + voxelSizeZ);

        if (slices.isEmpty()) return new ArrayList<>();

        List<Mat> volumeData = new ArrayList<>();

        System.out.println("Построение изоповерхностей...");
        long startTime = System.currentTimeMillis();

        int width = 0, height = 0;

        for (DicomSlice slice : slices) {
            Mat mat = slice.getMat8();
            if (width == 0) {
                width = mat.cols();
                height = mat.rows();
            }
            volumeData.add(mat.clone());
        }

        int depth = volumeData.size();
        System.out.println("Объем: " + width + "x" + height + "x" + depth);

        int step = 3;
        int w = width / step;
        int h = height / step;
        int d = depth / step;

        System.out.println("Рабочий объем: " + w + "x" + h + "x" + d);

        float[][][] volume = new float[d][h][w];
        int centerX = w / 2;
        int centerY = h / 2;
        int radius = Math.min(w, h) / 2 - 5;

        for (int z = 0; z < d; z++) {
            Mat slice = volumeData.get(z * step);
            Mat resized = new Mat();
            Imgproc.resize(slice, resized, new Size(w, h));

            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int dx = x - centerX;
                    int dy = y - centerY;
                    if (dx * dx + dy * dy <= radius * radius) {
                        double[] pixel = resized.get(y, x);
                        volume[z][y][x] = (float) pixel[0];
                    } else {
                        volume[z][y][x] = 0;
                    }
                }
            }
            resized.release();
        }

        volumeData.forEach(Mat::release);

        normalizeVolume(volume, w, h, d);

        List<MeshView> meshes = new ArrayList<>();

        // Кости
        MeshView bones = createSurfaceMC(volume, w, h, d, 200,
                new Color(0.95, 0.95, 0.95, 1.0), voxelSizeX, voxelSizeY, voxelSizeZ);
        if (bones != null) meshes.add(bones);

        // Мягкие ткани
        MeshView softTissue = createSurfaceMC(volume, w, h, d, 120,
                new Color(0.8, 0.3, 0.3, 0.7), voxelSizeX, voxelSizeY, voxelSizeZ);
        if (softTissue != null) meshes.add(softTissue);

        // Легкие
        MeshView lungs = createSurfaceMC(volume, w, h, d, 60,
                new Color(0.2, 0.5, 0.9, 0.5), voxelSizeX, voxelSizeY, voxelSizeZ);
        if (lungs != null) meshes.add(lungs);

        // Кожа
        MeshView skin = createSurfaceMC(volume, w, h, d, 20,
                new Color(0.9, 0.75, 0.5, 0.3), voxelSizeX, voxelSizeY, voxelSizeZ);
        if (skin != null) meshes.add(skin);

        long endTime = System.currentTimeMillis();
        System.out.println("Построение завершено за " + (endTime - startTime) + " мс");
        System.out.println("Всего вершин: " +
                (meshes.stream().mapToInt(m -> ((TriangleMesh)m.getMesh()).getPoints().size() / 3).sum()));

        return meshes;
    }

    private void normalizeVolume(float[][][] volume, int w, int h, int d) {
        float min = Float.MAX_VALUE, max = Float.MIN_VALUE;

        for (int z = 0; z < d; z++) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    float val = volume[z][y][x];
                    if (val > 10) {
                        min = Math.min(min, val);
                        max = Math.max(max, val);
                    }
                }
            }
        }

        System.out.println("Диапазон интенсивностей: " + min + " - " + max);

        float range = max - min;
        if (range > 0) {
            for (int z = 0; z < d; z++) {
                for (int y = 0; y < h; y++) {
                    for (int x = 0; x < w; x++) {
                        if (volume[z][y][x] > 10) {
                            volume[z][y][x] = ((volume[z][y][x] - min) / range) * 255;
                        }
                    }
                }
            }
        }
    }

    private MeshView createSurfaceMC(float[][][] volume, int w, int h, int d,
                                     float isoLevel, Color color,
                                     float voxelSizeX, float voxelSizeY, float voxelSizeZ) {
        List<Point3D> vertices = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Map<Long, Integer> vertexMap = new HashMap<>();

        float offsetX = -w * voxelSizeX / 2;
        float offsetY = -h * voxelSizeY / 2;
        float offsetZ = -d * voxelSizeZ / 2;

        float[] cubeValues = new float[8];

        for (int z = 0; z < d - 1; z++) {
            for (int y = 0; y < h - 1; y++) {
                for (int x = 0; x < w - 1; x++) {
                    // Читаем 8 значений один раз
                    cubeValues[0] = volume[z][y][x];
                    cubeValues[1] = volume[z][y][x + 1];
                    cubeValues[2] = volume[z][y + 1][x + 1];
                    cubeValues[3] = volume[z][y + 1][x];
                    cubeValues[4] = volume[z + 1][y][x];
                    cubeValues[5] = volume[z + 1][y][x + 1];
                    cubeValues[6] = volume[z + 1][y + 1][x + 1];
                    cubeValues[7] = volume[z + 1][y + 1][x];

                    // Быстрая проверка min/max и вычисление cubeIndex за один проход
                    float minVal = cubeValues[0], maxVal = cubeValues[0];
                    int cubeIndex = (cubeValues[0] < isoLevel) ? 1 : 0;

                    for (int i = 1; i < 8; i++) {
                        if (cubeValues[i] < minVal) minVal = cubeValues[i];
                        if (cubeValues[i] > maxVal) maxVal = cubeValues[i];
                        if (cubeValues[i] < isoLevel) cubeIndex |= (1 << i);
                    }

                    // Пропускаем кубы, которые не пересекаются с изоповерхностью
                    if (minVal > isoLevel || maxVal < isoLevel) continue;
                    if (MarchingCubesTables.edgeTable[cubeIndex] == 0) continue;

                    processCubeFast(cubeValues, cubeIndex, x, y, z,
                            offsetX, offsetY, offsetZ,
                            voxelSizeX, voxelSizeY, voxelSizeZ,
                            isoLevel, vertices, indices, vertexMap);
                }
            }
        }

        if (vertices.isEmpty()) return null;
        return createMeshView(vertices, indices, color);
    }

    private void processCubeFast(float[] cubeValues, int cubeIndex,
                                 int x, int y, int z,
                                 float offsetX, float offsetY, float offsetZ,
                                 float voxelSizeX, float voxelSizeY, float voxelSizeZ,
                                 float isoLevel,
                                 List<Point3D> vertices, List<Integer> indices,
                                 Map<Long, Integer> vertexMap) {

        // Координаты углов куба
        float x0 = x * voxelSizeX + offsetX;
        float y0 = y * voxelSizeY + offsetY;
        float z0 = z * voxelSizeZ + offsetZ;
        float x1 = (x + 1) * voxelSizeX + offsetX;
        float y1 = (y + 1) * voxelSizeY + offsetY;
        float z1 = (z + 1) * voxelSizeZ + offsetZ;

        int edgeMask = MarchingCubesTables.edgeTable[cubeIndex];
        int[] vertIndices = new int[12];
        Arrays.fill(vertIndices, -1);

        // Вычисляем вершины на рёбрах (только если ребро активно)
        if ((edgeMask & 1) != 0) vertIndices[0] = addEdgeVertex(cubeValues[0], cubeValues[1], isoLevel, x0, y0, z0, x1, y0, z0, vertices, vertexMap);
        if ((edgeMask & 2) != 0) vertIndices[1] = addEdgeVertex(cubeValues[1], cubeValues[2], isoLevel, x1, y0, z0, x1, y1, z0, vertices, vertexMap);
        if ((edgeMask & 4) != 0) vertIndices[2] = addEdgeVertex(cubeValues[2], cubeValues[3], isoLevel, x1, y1, z0, x0, y1, z0, vertices, vertexMap);
        if ((edgeMask & 8) != 0) vertIndices[3] = addEdgeVertex(cubeValues[3], cubeValues[0], isoLevel, x0, y1, z0, x0, y0, z0, vertices, vertexMap);
        if ((edgeMask & 16) != 0) vertIndices[4] = addEdgeVertex(cubeValues[4], cubeValues[5], isoLevel, x0, y0, z1, x1, y0, z1, vertices, vertexMap);
        if ((edgeMask & 32) != 0) vertIndices[5] = addEdgeVertex(cubeValues[5], cubeValues[6], isoLevel, x1, y0, z1, x1, y1, z1, vertices, vertexMap);
        if ((edgeMask & 64) != 0) vertIndices[6] = addEdgeVertex(cubeValues[6], cubeValues[7], isoLevel, x1, y1, z1, x0, y1, z1, vertices, vertexMap);
        if ((edgeMask & 128) != 0) vertIndices[7] = addEdgeVertex(cubeValues[7], cubeValues[4], isoLevel, x0, y1, z1, x0, y0, z1, vertices, vertexMap);
        if ((edgeMask & 256) != 0) vertIndices[8] = addEdgeVertex(cubeValues[0], cubeValues[4], isoLevel, x0, y0, z0, x0, y0, z1, vertices, vertexMap);
        if ((edgeMask & 512) != 0) vertIndices[9] = addEdgeVertex(cubeValues[1], cubeValues[5], isoLevel, x1, y0, z0, x1, y0, z1, vertices, vertexMap);
        if ((edgeMask & 1024) != 0) vertIndices[10] = addEdgeVertex(cubeValues[2], cubeValues[6], isoLevel, x1, y1, z0, x1, y1, z1, vertices, vertexMap);
        if ((edgeMask & 2048) != 0) vertIndices[11] = addEdgeVertex(cubeValues[3], cubeValues[7], isoLevel, x0, y1, z0, x0, y1, z1, vertices, vertexMap);

        // Добавляем треугольники
        int i = 0;
        while (i < 16 && MarchingCubesTables.triTable[cubeIndex][i] != -1) {
            int e1 = MarchingCubesTables.triTable[cubeIndex][i];
            int e2 = MarchingCubesTables.triTable[cubeIndex][i + 1];
            int e3 = MarchingCubesTables.triTable[cubeIndex][i + 2];

            if (e1 >= 0 && e1 < 12 && e2 >= 0 && e2 < 12 && e3 >= 0 && e3 < 12) {
                int i1 = vertIndices[e1];
                int i2 = vertIndices[e2];
                int i3 = vertIndices[e3];

                if (i1 != -1 && i2 != -1 && i3 != -1) {
                    indices.add(i1);
                    indices.add(i2);
                    indices.add(i3);
                }
            }
            i += 3;
            if (i >= 15) break;
        }
    }

    // Вспомогательный метод для вычисления вершины на ребре
    private int addEdgeVertex(float val1, float val2, float isoLevel,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              List<Point3D> vertices, Map<Long, Integer> vertexMap) {
        float mu;
        if (Math.abs(val1 - val2) < 0.00001f) {
            mu = 0.5f;
        } else {
            mu = (isoLevel - val1) / (val2 - val1);
        }

        // Ограничиваем mu диапазоном [0,1] для стабильности
        if (mu < 0) mu = 0;
        if (mu > 1) mu = 1;

        Point3D vertex = new Point3D(
                x1 + mu * (x2 - x1),
                y1 + mu * (y2 - y1),
                z1 + mu * (z2 - z1)
        );

        // Хеш-ключ из x и y (как раньше)
        long key = ((long) Float.floatToIntBits(vertex.x) << 32)
                | (Float.floatToIntBits(vertex.y) & 0xFFFFFFFFL);

        Integer index = vertexMap.get(key);
        if (index == null || !verticesEqual(vertices.get(index), vertex)) {
            // Проверяем соседние ключи при коллизии
            for (int offset = 1; offset < 10; offset++) {
                index = vertexMap.get(key + offset);
                if (index != null && verticesEqual(vertices.get(index), vertex)) {
                    return index;
                }
            }
            index = vertices.size();
            vertices.add(vertex);
            vertexMap.put(key, index);
        }
        return index;
    }

    private boolean verticesEqual(Point3D v1, Point3D v2) {
        return Math.abs(v1.x - v2.x) < 0.0001f
                && Math.abs(v1.y - v2.y) < 0.0001f
                && Math.abs(v1.z - v2.z) < 0.0001f;
    }

    private MeshView createMeshView(List<Point3D> vertices, List<Integer> indices, Color color) {
        TriangleMesh mesh = new TriangleMesh();

        float[] points = new float[vertices.size() * 3];
        for (int i = 0; i < vertices.size(); i++) {
            Point3D p = vertices.get(i);
            points[i * 3] = p.x;
            points[i * 3 + 1] = p.y;
            points[i * 3 + 2] = p.z;
        }
        mesh.getPoints().addAll(points);

        mesh.getTexCoords().addAll(0, 0);

        int[] faces = new int[indices.size() * 2];
        for (int i = 0; i < indices.size(); i += 3) {
            faces[i * 2] = indices.get(i);
            faces[i * 2 + 1] = 0;
            faces[i * 2 + 2] = indices.get(i + 1);
            faces[i * 2 + 3] = 0;
            faces[i * 2 + 4] = indices.get(i + 2);
            faces[i * 2 + 5] = 0;
        }
        mesh.getFaces().addAll(faces);

        PhongMaterial material = new PhongMaterial();
        material.setDiffuseColor(color);
        material.setSpecularColor(Color.WHITE);
        material.setSpecularPower(32);

        MeshView meshView = new MeshView(mesh);
        meshView.setMaterial(material);

        return meshView;
    }

    private int[] getEdgeVertices(int edge) {
        int[][] edges = {
                {0, 1}, {1, 2}, {2, 3}, {3, 0},
                {4, 5}, {5, 6}, {6, 7}, {7, 4},
                {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        return edges[edge];
    }

    private void addLighting(Group root) {
        PointLight light1 = new PointLight(Color.WHITE);
        light1.setTranslateX(300);
        light1.setTranslateY(-200);
        light1.setTranslateZ(-300);

        PointLight light2 = new PointLight(Color.rgb(200, 220, 255));
        light2.setTranslateX(-300);
        light2.setTranslateY(200);
        light2.setTranslateZ(-200);

        PointLight light3 = new PointLight(Color.rgb(255, 240, 200));
        light3.setTranslateY(-300);
        light3.setTranslateZ(200);

        AmbientLight ambient = new AmbientLight(Color.rgb(40, 40, 40));

        root.getChildren().addAll(light1, light2, light3, ambient);
    }

    private void addCoordinateAxes(Group root) {
        PhongMaterial redMat = new PhongMaterial(Color.RED);
        PhongMaterial greenMat = new PhongMaterial(Color.GREEN);
        PhongMaterial blueMat = new PhongMaterial(Color.BLUE);

        Box xAxis = new Box(200, 1, 1);
        xAxis.setMaterial(redMat);
        xAxis.setTranslateX(100);

        Box yAxis = new Box(1, 200, 1);
        yAxis.setMaterial(greenMat);
        yAxis.setTranslateY(100);

        Box zAxis = new Box(1, 1, 200);
        zAxis.setMaterial(blueMat);
        zAxis.setTranslateZ(100);

        root.getChildren().addAll(xAxis, yAxis, zAxis);
    }

    private static class Point3D {
        float x, y, z;
        Point3D(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private static class MouseController {
        private double anchorX, anchorY;
        private double anchorAngleX = 0, anchorAngleY = 0;
        private final Rotate rotateX = new Rotate(0, Rotate.X_AXIS);
        private final Rotate rotateY = new Rotate(0, Rotate.Y_AXIS);

        public MouseController(Group group, Scene scene) {
            group.getTransforms().addAll(rotateY, rotateX);

            scene.setOnMousePressed(e -> {
                anchorX = e.getSceneX();
                anchorY = e.getSceneY();
                anchorAngleX = rotateX.getAngle();
                anchorAngleY = rotateY.getAngle();
            });

            scene.setOnMouseDragged(e -> {
                rotateY.setAngle(anchorAngleY + (e.getSceneX() - anchorX) * 0.3);
                rotateX.setAngle(anchorAngleX - (e.getSceneY() - anchorY) * 0.3);
            });

            scene.setOnScroll(e -> {
                Camera cam = scene.getCamera();
                if (cam instanceof PerspectiveCamera) {
                    PerspectiveCamera pc = (PerspectiveCamera) cam;
                    pc.setTranslateZ(pc.getTranslateZ() + e.getDeltaY() * 0.5);
                }
            });
        }
    }
}