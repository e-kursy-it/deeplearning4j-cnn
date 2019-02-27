import org.datavec.image.loader.NativeImageLoader;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.zoo.PretrainedType;
import org.deeplearning4j.zoo.ZooModel;
import org.deeplearning4j.zoo.model.VGG16;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.preprocessor.DataNormalization;
import org.nd4j.linalg.dataset.api.preprocessor.VGG16ImagePreProcessor;
import spark.Request;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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

}