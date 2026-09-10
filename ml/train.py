"""
Entrena un clasificador multi-etiqueta (N, D, G, C, A, H, M, O) de enfermedades
oculares sobre el dataset ODIR-5K y exporta un .tflite listo para la app Android.

Requiere: ml/data/full_df.csv y ml/data/preprocessed_images/*.jpg
(descargados de https://www.kaggle.com/datasets/andrewmvd/ocular-disease-recognition-odir5k)

Nota: esta maquina no tiene soporte GPU nativo en TensorFlow para Windows
(TF >= 2.11 solo acelera por GPU vía WSL2/DirectML), asi que se entrena en CPU
con una arquitectura liviana (MobileNetV2 alpha=0.35 @ 160x160) para que el
entrenamiento sea razonable en tiempo.
"""
import ast
import os
import sys
import time

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.utils.class_weight import compute_class_weight

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")
IMAGES_DIR = os.path.join(DATA_DIR, "preprocessed_images")
CSV_PATH = os.path.join(DATA_DIR, "full_df.csv")
OUT_DIR = os.path.dirname(__file__)

LABEL_COLS = ["N", "D", "G", "C", "A", "H", "M", "O"]
IMG_SIZE = 160
BATCH_SIZE = 32
SEED = 42

ASSETS_OUT = os.path.join(
    os.path.dirname(__file__), "..", "app", "src", "main", "assets", "ocular_disease_model.tflite"
)


def log(msg):
    print(f"[{time.strftime('%H:%M:%S')}] {msg}", flush=True)


def load_dataset():
    log("Leyendo CSV de etiquetas...")
    df = pd.read_csv(CSV_PATH)
    df = df[df["filename"].apply(lambda f: os.path.exists(os.path.join(IMAGES_DIR, f)))]
    log(f"{len(df)} imagenes con archivo presente en preprocessed_images/")

    labels = df[LABEL_COLS].values.astype("float32")
    filenames = df["filename"].tolist()

    log("Cargando y redimensionando imagenes en memoria (una sola vez)...")
    images = np.zeros((len(filenames), IMG_SIZE, IMG_SIZE, 3), dtype="uint8")
    for i, fname in enumerate(filenames):
        img = tf.io.read_file(os.path.join(IMAGES_DIR, fname))
        img = tf.io.decode_jpeg(img, channels=3)
        img = tf.image.resize(img, [IMG_SIZE, IMG_SIZE], method="bilinear")
        images[i] = img.numpy().astype("uint8")
        if (i + 1) % 1000 == 0:
            log(f"  {i + 1}/{len(filenames)} imagenes procesadas")

    return images, labels


def build_model():
    base = tf.keras.applications.MobileNetV2(
        input_shape=(IMG_SIZE, IMG_SIZE, 3),
        alpha=0.35,
        include_top=False,
        weights="imagenet",
        pooling="avg",
    )
    base.trainable = False

    inputs = tf.keras.Input(shape=(IMG_SIZE, IMG_SIZE, 3))
    x = tf.keras.applications.mobilenet_v2.preprocess_input(inputs)
    x = base(x, training=False)
    x = tf.keras.layers.Dropout(0.3)(x)
    outputs = tf.keras.layers.Dense(len(LABEL_COLS), activation="sigmoid")(x)
    model = tf.keras.Model(inputs, outputs)
    return model, base


def main():
    images, labels = load_dataset()

    primary = labels.argmax(axis=1)
    idx = np.arange(len(images))
    train_idx, val_idx = train_test_split(
        idx, test_size=0.15, random_state=SEED, stratify=primary
    )
    log(f"Train: {len(train_idx)}  Val: {len(val_idx)}")

    # peso por clase para compensar el desbalance (H, A, M, G, C son minoria)
    class_weights = {}
    for i, col in enumerate(LABEL_COLS):
        pos = labels[train_idx, i].sum()
        neg = len(train_idx) - pos
        class_weights[i] = float(neg / max(pos, 1)) if pos > 0 else 1.0
    log(f"Pesos por clase (para referencia): {dict(zip(LABEL_COLS, [round(class_weights[i], 2) for i in range(8)]))}")

    def make_dataset(indices, training):
        x = images[indices]
        y = labels[indices]
        ds = tf.data.Dataset.from_tensor_slices((x, y))
        if training:
            ds = ds.shuffle(2000, seed=SEED)

        def augment(img, lbl):
            img = tf.image.random_flip_left_right(img)
            img = tf.image.random_brightness(img, 0.1)
            img = tf.image.random_contrast(img, 0.9, 1.1)
            return img, lbl

        if training:
            ds = ds.map(augment, num_parallel_calls=tf.data.AUTOTUNE)

        return ds.batch(BATCH_SIZE).prefetch(tf.data.AUTOTUNE)

    train_ds = make_dataset(train_idx, training=True)
    val_ds = make_dataset(val_idx, training=False)

    model, base = build_model()

    # aproximamos el peso por clase con un vector aplicado via loss ponderada,
    # ya que Keras no soporta class_weight directo en salidas multi-etiqueta.
    weight_vec = tf.constant([class_weights[i] for i in range(8)], dtype=tf.float32)

    def weighted_bce(y_true, y_pred):
        bce = tf.keras.losses.binary_crossentropy(y_true, y_pred)
        # bce ya reduce sobre el eje de clases; usamos una version manual para poder pesar
        eps = 1e-7
        y_pred = tf.clip_by_value(y_pred, eps, 1 - eps)
        per_class = -(y_true * tf.math.log(y_pred) * weight_vec + (1 - y_true) * tf.math.log(1 - y_pred))
        return tf.reduce_mean(per_class)

    log("=== Fase 1: entrenando solo la cabeza (backbone congelado) ===")
    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-3),
        loss=weighted_bce,
        metrics=[tf.keras.metrics.AUC(name="auc", multi_label=True)],
    )
    model.fit(train_ds, validation_data=val_ds, epochs=6)

    log("=== Fase 2: fine-tuning del backbone (lr bajo) ===")
    base.trainable = True
    for layer in base.layers[:-30]:
        layer.trainable = False

    model.compile(
        optimizer=tf.keras.optimizers.Adam(1e-5),
        loss=weighted_bce,
        metrics=[tf.keras.metrics.AUC(name="auc", multi_label=True)],
    )
    model.fit(train_ds, validation_data=val_ds, epochs=6)

    log("Evaluando en validacion...")
    results = model.evaluate(val_ds)
    log(f"Resultado final val: {dict(zip(model.metrics_names, results))}")

    log("Convirtiendo a TFLite (float16)...")
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.target_spec.supported_types = [tf.float16]
    tflite_model = converter.convert()

    os.makedirs(os.path.dirname(ASSETS_OUT), exist_ok=True)
    with open(ASSETS_OUT, "wb") as f:
        f.write(tflite_model)
    log(f"Modelo guardado en {ASSETS_OUT} ({len(tflite_model) / 1e6:.2f} MB)")
    log(f"Orden de clases del modelo: {LABEL_COLS}")


if __name__ == "__main__":
    main()
