package de.tum.ls4.generators;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * EAGL's element stability model: a scikit-learn {@code RandomForestRegressor} (100 trees, 6 features) exported as plain
 * data by {@code scripts/eagl/export_for_java.py}. {@link #predict} reproduces {@code model.predict} bit for bit:
 * features are cast to float32 as sklearn does, each tree goes left when {@code x[f] <= threshold}, and the tree outputs
 * are summed in order and divided by the number of trees.
 * <p>
 * Features: (position, length, depth, nr_siblings, nr_children, isattribute). The bundled model is EAGL's
 * {@code database/cross_training.joblib} (https://github.com/hilalosoft/EAGL, MIT License, Copyright (c) 2025 Hilal TAHA),
 * which EAGL_excutor.py loads by default. Despite its name it is byte-identical to the authors' per-site model
 * {@code scaled/adobe_scaled.joblib} (models_and_pickles_all_projects.7z), i.e. a model trained on adobe.com only.
 */
final class EaglForest {

    private static final String BUNDLED = "/eagl/eagl-forest.bin.gz";
    private static volatile EaglForest bundled;

    private final int features;
    private final byte[][] feature;
    private final double[][] threshold;
    private final int[][] left;
    private final int[][] right;
    private final double[][] value;

    private EaglForest(DataInputStream in) throws IOException {
        byte[] magic = new byte[8];
        in.readFully(magic);
        if (!"EAGLRF1\n".equals(new String(magic, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an EAGL forest");
        }
        features = in.readInt();
        int trees = in.readInt();
        feature = new byte[trees][];
        threshold = new double[trees][];
        left = new int[trees][];
        right = new int[trees][];
        value = new double[trees][];
        for (int t = 0; t < trees; t++) {
            int n = in.readInt();
            feature[t] = new byte[n];
            threshold[t] = new double[n];
            left[t] = new int[n];
            right[t] = new int[n];
            value[t] = new double[n];
            for (int i = 0; i < n; i++) {
                feature[t][i] = in.readByte();
                threshold[t][i] = in.readDouble();
                left[t][i] = in.readInt();
                right[t][i] = in.readInt();
                value[t][i] = in.readDouble();
            }
        }
    }

    static EaglForest read(InputStream gzipped) {
        try (DataInputStream in = new DataInputStream(new java.io.BufferedInputStream(new GZIPInputStream(gzipped)))) {
            return new EaglForest(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The model shipped with the library (loaded once). */
    static EaglForest bundled() {
        if (bundled == null) {
            synchronized (EaglForest.class) {
                if (bundled == null) {
                    InputStream in = EaglForest.class.getResourceAsStream(BUNDLED);
                    if (in == null) throw new IllegalStateException("Missing resource " + BUNDLED);
                    bundled = read(in);
                }
            }
        }
        return bundled;
    }

    /** {@code model.predict([x])[0]}; booleans are passed as 0/1. */
    double predict(double... x) {
        if (x.length != features) throw new IllegalArgumentException("Expected " + features + " features");
        double[] xf = new double[x.length];
        for (int i = 0; i < x.length; i++) xf[i] = (float) x[i];
        double sum = 0.0;
        for (int t = 0; t < feature.length; t++) {
            int node = 0;
            while (feature[t][node] >= 0) {
                node = xf[feature[t][node]] <= threshold[t][node] ? left[t][node] : right[t][node];
            }
            sum += value[t][node];
        }
        return sum / feature.length;
    }
}
