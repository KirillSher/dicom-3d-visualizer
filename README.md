# DICOM 3D Visualizer

Простое desktop-приложение для просмотра DICOM (MRI) изображений с базовой обработкой через OpenCV.

## 🚀 Возможности

* Загрузка серии DICOM файлов
* Просмотр срезов (slice-by-slice)
* Навигация через слайдер
* Миниатюры всех срезов
* Отображение метаданных:

    * имя файла
    * slice location
    * orientation
* Обработка изображения:

    * Gaussian Blur
    * Median Filter
    * Window Level (регулировка контраста)
* Поддержка 16-bit DICOM изображений

---

## 🛠️ Технологии

* Java 17
* JavaFX
* OpenCV
* ImageJ (для чтения DICOM)
* Maven

---

## 📦 Установка зависимостей (Linux / Ubuntu)

### 1. Установить OpenCV

```bash
sudo apt update
sudo apt install libopencv-dev
```

---

### 2. Проверить наличие Java-библиотеки OpenCV

```bash
find /usr -name "*opencv*.so" | grep java
```

Ожидаемый результат:

```bash
/usr/lib/jni/libopencv_java460.so
```

---

### 3. Создать символическую ссылку (если версия отличается)

```bash
sudo ln -s /usr/lib/jni/libopencv_java460.so /usr/lib/x86_64-linux-gnu/libopencv_java490.so
```

---

### 4. Установить переменную окружения

```bash
export LD_LIBRARY_PATH=/usr/lib/x86_64-linux-gnu:$LD_LIBRARY_PATH
```

---

## ▶️ Запуск проекта

Через Maven:

```bash
mvn clean javafx:run
```

---

## 📁 Структура проекта

```text
org.example
├── app          # Main приложение (JavaFX UI)
├── controller   # Контроллеры
├── model        # DICOM модели (Slice, Series)
├── service      # Загрузка DICOM
├── opencv       # OpenCV обработка
└── util         # Конвертеры изображений
```

---

## 📌 Примечания

* Проект работает с 16-bit DICOM изображениями
* Для корректной работы требуется установленный OpenCV
* Поддерживается только локальный запуск (desktop)

---

## 🔮 Планы

* Ускорение обработки (кэширование Mat)
* 3D визуализация
* Window Level управление мышью
* Поддержка drag & drop

---

## 👤 Автор

[KirillSher](https://github.com/KirillSher)
