import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.util.ModelSerializer;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.VGG16;
import org.jetbrains.annotations.NotNull;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;
import org.nd4j.linalg.factory.Nd4j;
import spark.Request;

import javax.imageio.ImageIO;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class CatDogVgg {
    public static void main(String[] args) throws Exception {
        ZooModel zooModel = VGG16.builder().numClasses( 1000 ).build();
        ComputationGraph computationGraph = (ComputationGraph) zooModel.initPretrained( PretrainedType.IMAGENET );

        staticFiles.location("/static/");
        staticFiles.expireTime(1);
        port(4560);

        File uploadDir = new File("upload");
        uploadDir.mkdir();

        staticFiles.externalLocation("upload");

        get("/hello", (req, res) -> "Hello World");

        post( "/captcha", (req, res) -> {
            res.type("application/json");

            Path tempFile = receiveUploadedFile(uploadDir, req);

            try {
                NativeImageLoader loader = new NativeImageLoader(224, 224, 3);
                INDArray image = loader.asMatrix(new FileInputStream(tempFile.toFile()));
                DataNormalization scaler = new VGG16ImagePreProcessor();
                scaler.transform(image);
                INDArray output = computationGraph.outputSingle(false, image);

                System.out.println(output);
                int ret = -1;
                double max = output.getRow( 0 ).max().getDouble( 0 );
                if (max > 0.09) {
                    int idx = findMatchingIndex( output, max );
                    ret = idx;
                }
                return "{\"found\": " + ret + "}";
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "{}";
            } finally {
                Files.delete(tempFile);
            }
        });

    }

    @NotNull
    private static Path receiveUploadedFile(File uploadDir, Request req) throws IOException, ServletException {
        Path tempFile = Files.createTempFile(uploadDir.toPath(), "", "");

        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        try (InputStream input = req.raw().getPart("uploaded_file").getInputStream()) { // getPart needs to use same "name" as input field in form
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private static int findMatchingIndex(INDArray output, double max) {
        for (int i = 0; i < 1000; i++) {
            if (max == output.getRow(0).getDouble(i)) {
                return i;
            }
        }
        return -1;
    }

    private static INDArray toINDArray(BufferedImage gray) {
        INDArray digit = Nd4j.create(3, 224, 224);
        for (int i = 0; i < 224; i++) {
            for (int j = 0; j < 224; j++) {
                Color c = new Color(gray.getRGB(i, j));
                digit.putScalar(0, j, i, (c.getBlue() & 0xFF));
                digit.putScalar(1, j, i, (c.getGreen() & 0xFF));
                digit.putScalar(2, j, i, (c.getRed() & 0xFF));
            }
        }

        return digit.reshape(1, 3, 224, 224).divi(0xff);
    }

    @NotNull
    private static BufferedImage resize(BufferedImage image) {
        BufferedImage gray = new BufferedImage(224, 224, BufferedImage.TYPE_INT_BGR);

        Graphics2D g = (Graphics2D) gray.getGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, 224, 224);
        g.drawImage(image.getScaledInstance(224, 224, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return gray;
    }

    @NotNull
    private static BufferedImage invertColors(BufferedImage inputImage) {
        BufferedImage gray = new BufferedImage(inputImage.getWidth(), inputImage.getHeight(), BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g = (Graphics2D) gray.getGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, inputImage.getWidth(), inputImage.getHeight());
        g.drawImage(inputImage, 0, 0, null);
        g.dispose();

        for (int x = 0; x < gray.getWidth(); x++) {
            for (int y = 0; y < gray.getHeight(); y++) {
                int rgba = gray.getRGB(x, y);
                Color col = new Color(rgba, true);
                col = new Color(255 - col.getRed(),
                        255 - col.getGreen(),
                        255 - col.getBlue());
                gray.setRGB(x, y, col.getRGB());
            }
        }
        return gray;
    }
}