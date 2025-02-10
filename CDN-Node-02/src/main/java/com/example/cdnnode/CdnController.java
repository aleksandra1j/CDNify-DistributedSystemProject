package com.example.cdnnode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.net.URLDecoder;

@RestController
@RequestMapping("/cdn")
public class CdnController {
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final RestTemplate restTemplate = new RestTemplate();
    private final String originServerUrl = "http://172.20.10.6:8081/origin";

    private static final Logger logger = LoggerFactory.getLogger(CdnController.class);

    @Value("${cdn.cache.path}")
    private String cdnCacheDir;

    // Utility method to format the date to the required format
    private String formatLastModified(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date(timestamp));
    }

    private ResponseEntity<List<String>> getListResponseEntity(String originUrl) {
        ResponseEntity<String[]> response = restTemplate.getForEntity(originUrl, String[].class);
        response.getBody();
        if (response.getBody().length == 0) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        return ResponseEntity.ok(Arrays.asList(response.getBody()));
    }

    @GetMapping("/list-series")
    public ResponseEntity<List<String>> listSeries() {
        String originUrl = originServerUrl + "/series";
        logger.info("Origin URL: {}", originUrl);
        try {
            return getListResponseEntity(originUrl);
        } catch (Exception e) {
            logger.error("Failed to fetch series list from Origin Server: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    @GetMapping("/list-types/{series}")
    public ResponseEntity<List<String>> listTypes(@PathVariable String series) {
        String originUrl = originServerUrl + "/types/" + series;
        logger.info("Origin URL: {}", originUrl);
        try {
            return getListResponseEntity(originUrl);
        } catch (Exception e) {
            logger.error("Failed to fetch types list from Origin Server: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

    // Fetch the file from the origin server if not in cache
    private ResponseEntity<?> fetchFromOriginServer(String series, String type, String filename) {
        String originUrl = originServerUrl + "/" + series + "/" + type + "/" + filename;
        logger.info("Origin URL: {}", originUrl);
        try {
            ResponseEntity<Resource> response = restTemplate.getForEntity(originUrl, Resource.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                InputStream inputStream = response.getBody().getInputStream();
                byte[] fileData = inputStream.readAllBytes();

                // Fix: Avoid NullPointerException by providing a default content type
                String contentType = (response.getHeaders().getContentType() != null)
                        ? response.getHeaders().getContentType().toString()
                        : "application/octet-stream";  // Default Content-Type

                long lastModifiedTimestamp = System.currentTimeMillis();

                logger.info("File fetched from Origin Server and cached: {}", series + "/" + type + "/" + filename);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=3600, must-revalidate")
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                        .header(HttpHeaders.LAST_MODIFIED, formatLastModified(lastModifiedTimestamp))
                        .header(HttpHeaders.CONTENT_TYPE, contentType)
                        .body(new ByteArrayResource(fileData));
            } else {
                logger.error("Error fetching file from origin server: {}", response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(null);
            }
        } catch (IOException e) {
            logger.error("Error reading file from Origin Server: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }


    // Cache the file to the CDN cache directory
    private void cacheFile(Path cachePath, InputStream fileStream) throws IOException {
        Files.createDirectories(cachePath.getParent());
        try (InputStream inputStream = fileStream) {
            Files.copy(inputStream, cachePath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // Stream the file to the client
    private ResponseEntity<?> streamFile(Path filePath) {
        try {
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream"; // Default fallback
            }

            // Always force download instead of opening inline
            String disposition = "attachment; filename=\"" + filePath.getFileName().toString() + "\"";

            Resource resource = new ByteArrayResource(Files.readAllBytes(filePath));

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error streaming file");
        }
    }



    @GetMapping("/{series}/{type}/{filename}")
    public ResponseEntity<?> getFile(@PathVariable String series,
                                     @PathVariable String type,
                                     @PathVariable String filename) {
        // Decode the filename, series, and type to ensure proper URL decoding
        try {
            series = URLDecoder.decode(series, "UTF-8");
            type = URLDecoder.decode(type, "UTF-8");
            filename = URLDecoder.decode(filename, "UTF-8");
        } catch (Exception e) {
            logger.error("Error decoding URL parameters: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid URL parameters");
        }

        logger.info("Fetching file: {}/{}/{}", series, type, filename);
        String cacheKey = series + "/" + type + "/" + filename;
        Path cachePath = Paths.get(cdnCacheDir, series, type, filename);

        // Check if file is in cache
        if (Files.exists(cachePath) && Files.isRegularFile(cachePath)) {
            logger.info("Serving file from cache: {}", cacheKey);
            return streamFile(cachePath);  // Stream from cache
        }

        // Fetch file from origin server
        ResponseEntity<?> originResponse = fetchFromOriginServer(series, type, filename);
        if (originResponse.getStatusCode().is2xxSuccessful() && originResponse.getBody() instanceof ByteArrayResource) {
            try {
                // Cache the file locally
                cacheFile(cachePath, ((ByteArrayResource) originResponse.getBody()).getInputStream());
                logger.info("File cached successfully: {}", cacheKey);

                // Serve the cached file
                return streamFile(cachePath);
            } catch (IOException e) {
                logger.error("Error caching file: {}", cacheKey, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error caching file");
            }
        }
        // If fetch from origin server fails, return the origin server's response
        return originResponse;
    }

    @PostMapping("/invalidate/{series}/{type}/{filename}")
    public ResponseEntity<String> invalidateCache(@PathVariable String series,
                                                  @PathVariable String type,
                                                  @PathVariable String filename) {
        String cacheKey = series + "/" + type + "/" + filename;
        Path cachePath = Paths.get(cdnCacheDir, series, type, filename);
        if (Files.exists(cachePath)) {
            try {
                Files.delete(cachePath);  // Invalidate cache
                logger.info("Cache invalidated for: {}/{}/{}", series, type, filename);
                return ResponseEntity.ok("Cache invalidated successfully");
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to invalidate cache");
            }
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found in cache");
    }

    @GetMapping("/list-files/{series}/{type}")
    public ResponseEntity<List<String>> listFiles(@PathVariable String series, @PathVariable String type) {
        String originUrl = originServerUrl + "/list-files/" + series + "/" + type;
        logger.info("Fetching file list from Origin: {}", originUrl);

        try {
            ResponseEntity<String[]> response = restTemplate.getForEntity(originUrl, String[].class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                logger.info("Received file list from Origin Server: {}", Arrays.toString(response.getBody()));
                return ResponseEntity.ok(Arrays.asList(response.getBody()));
            } else {
                logger.error("Failed to fetch file list from Origin Server. Status: {}", response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body(Collections.emptyList());
            }
        } catch (Exception e) {
            logger.error("Error fetching file list from Origin Server: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
    }

}