package com.example.originserver;

import com.example.originserver.utils.FileSerializer;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.tika.Tika;

@RestController
@RequestMapping("/origin")
public class ContentController {

    @Value("${content.base-dir}")
    private String baseDir;

    private static final Logger logger = LoggerFactory.getLogger(ContentController.class);
    private final Tika tika = new Tika();

    private List<String> listSubdirectories(String dirPath) {
        File directory = new File(dirPath);
        if (!directory.exists() || !directory.isDirectory()) {
            return Collections.emptyList();
        }
        String[] subdirs = directory.list((current, name) -> new File(current, name).isDirectory());
        return subdirs == null ? Collections.emptyList() : List.of(subdirs);
    }

    @GetMapping("/series")
    public ResponseEntity<List<String>> listSeries() {
        List<String> seriesDirectories = listSubdirectories(baseDir);
        return ResponseEntity.ok(seriesDirectories);
    }

    @GetMapping("/types/{series}")
    public ResponseEntity<List<String>> listTypes(@PathVariable String series) {
        String decodedSeries = java.net.URLDecoder.decode(series, java.nio.charset.StandardCharsets.UTF_8);
        String seriesPath = Paths.get(baseDir, decodedSeries).toString();
        List<String> typeDirectories = listSubdirectories(seriesPath);
        return ResponseEntity.ok(typeDirectories);
    }

    @GetMapping("/list-files/{series}/{type}")
    public ResponseEntity<List<String>> listFiles(@PathVariable String series, @PathVariable String type) {
        String decodedSeries = java.net.URLDecoder.decode(series, java.nio.charset.StandardCharsets.UTF_8);
        String decodedType = java.net.URLDecoder.decode(type, java.nio.charset.StandardCharsets.UTF_8);
        Path dirPath = Paths.get(baseDir, decodedSeries, decodedType);
        logger.info("Checking directory: {}", dirPath);

        try {
            if (!Files.exists(dirPath) || !Files.isDirectory(dirPath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
            }

            // Fetch all files in the directory
            List<String> files = Files.list(dirPath)
                    .filter(path -> Files.isRegularFile(path) && !path.getFileName().toString().equals("list-files"))
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.toList());

            // Return the list of file names with the correct response type (JSON)
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/json") // Ensure content type is JSON
                    .body(files);
        } catch (IOException e) {
            logger.error("Error listing files in directory: {}", dirPath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @GetMapping("/{series}/{type}/{filename}")
    public ResponseEntity<?> getFile(
            @PathVariable String series,
            @PathVariable String type,
            @PathVariable String filename,
            OutputStream responseOutputStream) {

        String decodedSeries = java.net.URLDecoder.decode(series, java.nio.charset.StandardCharsets.UTF_8);
        String decodedType = java.net.URLDecoder.decode(type, java.nio.charset.StandardCharsets.UTF_8);
        String decodedFilename = java.net.URLDecoder.decode(filename, java.nio.charset.StandardCharsets.UTF_8);

        logger.info("Decoded series: {}", decodedSeries);
        logger.info("Decoded type: {}", decodedType);
        logger.info("Decoded filename: {}", decodedFilename);

        //logger.info("Request received for file: {}/{}/{}", decodedSeries, decodedType, decodedFilename);
        Path filePath = Paths.get(baseDir, decodedSeries, decodedType, decodedFilename);

        logger.info("Resolved file path: {}", filePath);

        try {
            File file = filePath.toFile();
            if (!file.exists() || !file.canRead()) {
                logger.warn("File not found or not readable: {}", filePath);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found");
            }

            // Dynamically detect media type using Apache Tika
            String mediaType = tika.detect(file);
            if (mediaType == null) {
                logger.warn("Unable to detect media type for: {}", filename);
                return ResponseEntity.badRequest().body("Unable to detect media type");
            }

            // Prepare headers
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + decodedFilename + "\"");
            headers.add(HttpHeaders.CACHE_CONTROL, "max-age=3600, must-revalidate");
            headers.add(HttpHeaders.CONTENT_TYPE, mediaType);

            logger.info("Streaming file: {}", filePath);

            // Serialize and stream the file
            FileSerializer.serializeFile(file, responseOutputStream);
            return ResponseEntity.ok().headers(headers).build();

        } catch (IOException e) {
            logger.error("Error while serving file: {}", filePath, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred: " + e.getMessage());
        }
    }


}



