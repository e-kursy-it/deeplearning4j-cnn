package it.ekursy.blog;

import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import spark.Request;

import javax.imageio.ImageIO;
import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.stream.Collectors;

import static spark.Spark.*;

public class CaptchaCracker {
    public static void main(String[] args) throws Exception {

        /**
         * You can download pre-trained model from
         *
         * https://deeplearning4j.e-kursy.it/models/multiDigitNumberRecognition_after_38_epochs.zip
         */
        ComputationGraph computationGraph = ModelSerializer.restoreComputationGraph(new File("src/main/resources/models/multiDigitNumberRecognition_after_38_epochs.zip"), false);

        staticFiles.location("/static/");
        staticFiles.expireTime(1);
        port(4561);

        File uploadDir = new File("upload");
        uploadDir.mkdir();

        staticFiles.externalLocation("upload");

        get("/hello", (req, res) -> "Hello World");

        post( "/captcha", (req, res) -> {
            res.type("application/json");

            Path tempFile = receiveUploadedFile(uploadDir, req);

            try {
                BufferedImage inputImage = ImageIO.read(tempFile.toFile());

                BufferedImage image = invertColors(inputImage);

                BufferedImage gray = resize(image);

                INDArray digit = toINDArray(gray);

                INDArray[] output = computationGraph.output(digit);

                System.out.println(output);
                int resultLength = output.length;
                int[] ret = new int[resultLength];
                for (int i = 0; i < resultLength; i++) {
                    double max = output[i].getRow( 0 ).max().getDouble( 0 );
                    if (max > 0.30) {
                        int idx = findMatchingIndex( output[i], max );
                        ret[ i ] = idx;
                    }
                }
//                if (max > 0.30) {
//                    int idx = findMatchingIndex(output, max);
//                    return "{\"digit\":\"" + idx + "\", \"score\": " + max + "}";
//                } else {
//                    res.status(404);
//                    return "{}";
//                }
                return "{\"digits\": " + Arrays.stream(ret).boxed().collect( Collectors.toList()) + "}";
            } catch (Exception e) {
                e.printStackTrace();
                res.status(500);
                return "{}";
            } finally {
                Files.delete(tempFile);
            }
        });

    }

    private static Path receiveUploadedFile(File uploadDir, Request req) throws IOException, ServletException {
        Path tempFile = Files.createTempFile(uploadDir.toPath(), "", "");

        req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/temp"));

        try (InputStream input = req.raw().getPart("uploaded_file").getInputStream()) { // getPart needs to use same "name" as input field in form
            Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return tempFile;
    }

    private static int findMatchingIndex(INDArray output, double max) {
        for (int i = 0; i < 10; i++) {
            if (max == output.getRow(0).getDouble(i)) {
                return i;
            }
        }
        return -1;
    }

    private static INDArray toINDArray(BufferedImage gray) {
        INDArray digit = Nd4j.create(60, 160);
        for (int i = 0; i < 160; i++) {
            for (int j = 0; j < 60; j++) {
                Color c = new Color(gray.getRGB(i, j));
                digit.putScalar(j, i, (c.getGreen() & 0xFF));
            }
        }

        return digit.reshape(1, 1, 60, 160).divi(0xff);
    }

    private static BufferedImage resize(BufferedImage image) {
        BufferedImage gray = new BufferedImage(160, 60, BufferedImage.TYPE_BYTE_GRAY);

        Graphics2D g = (Graphics2D) gray.getGraphics();
        g.setBackground(Color.WHITE);
        g.clearRect(0, 0, 160, 60);
        g.drawImage(image.getScaledInstance(160, 60, Image.SCALE_SMOOTH), 0, 0, null);
        g.dispose();
        return gray;
    }

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