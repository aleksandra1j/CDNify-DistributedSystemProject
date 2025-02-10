package com.example.client;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

@SpringBootApplication
@EnableDiscoveryClient
public class ClientApplication {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    private static ConfigurableApplicationContext context;

    public static void main(String[] args) {
        var context = SpringApplication.run(ClientApplication.class, args);

        RestTemplate restTemplate = context.getBean(RestTemplate.class);

        Scanner scanner = new Scanner(System.in);

        // Fetch and display series
        String series = fetchSeries(restTemplate, scanner);
        if (series == null) {
            return;
        }

        // Fetch and display types
        String type = fetchTypes(restTemplate, scanner, series);
        if (type == null) {
            return;
        }

        // Fetch and display files
        fetchFiles(restTemplate, scanner, series, type);
    }

    private static void shutdownApplication() {
        System.out.println("Shutting down the application...");

        // Get the application context and close it
        if(context != null) {
            context.close();
        }
        // Ensure the application fully exits
        System.exit(0);
    }

    private static String getString(Scanner scanner, List<String> seriesList) {
        int seriesIndex = scanner.nextInt();
        scanner.nextLine(); // Consume newline

        if (seriesIndex > 0 && seriesIndex <= seriesList.size()) {
            return seriesList.get(seriesIndex - 1);
        } else {
            System.out.println("Invalid selection.");
            return null;
        }
    }

    private static String fetchSeries(RestTemplate restTemplate, Scanner scanner) {
        String seriesUrl = "http://cdn-node/cdn/list-series";
        try {
            ResponseEntity<String[]> response = restTemplate.getForEntity(seriesUrl, String[].class);

            response.getBody();
            if (response.getBody().length == 0) {
                System.out.println("No series available.");
                return null;
            }

            List<String> seriesList = Arrays.asList(response.getBody());
            System.out.println("Available series:");
            for (int i = 0; i < seriesList.size(); i++) {
                System.out.println((i + 1) + ". " + seriesList.get(i));
            }

            System.out.println("Enter the number of the series you want to choose:");
            return getString(scanner, seriesList);
        } catch (Exception e) {
            System.out.println("Failed to fetch series list: " + e.getMessage());
            return null;
        }
    }


    private static String fetchTypes(RestTemplate restTemplate, Scanner scanner, String series) {
        String typesUrl = "http://cdn-node/cdn/list-types/" + series;
        try {
            ResponseEntity<String[]> response = restTemplate.getForEntity(typesUrl, String[].class);

            response.getBody();
            if (response.getBody().length == 0) {
                System.out.println("No types available for the selected series.");
                return null;
            }

            List<String> typesList = Arrays.asList(response.getBody());
            System.out.println("Available types:");
            for (int i = 0; i < typesList.size(); i++) {
                System.out.println((i + 1) + ". " + typesList.get(i));
            }

            System.out.println("Enter the number of the type you want to choose:");
            return getString(scanner, typesList);
        } catch (Exception e) {
            System.out.println("Failed to fetch types list: " + e.getMessage());
            return null;
        }
    }

    private static void fetchFiles(RestTemplate restTemplate, Scanner scanner, String series, String type) {
        String encodedSeries = java.net.URLEncoder.encode(series, java.nio.charset.StandardCharsets.UTF_8);
        String encodedType = java.net.URLEncoder.encode(type, java.nio.charset.StandardCharsets.UTF_8);
        String listUrl = "http://cdn-node/cdn/list-files/" + encodedSeries + "/" + encodedType;
        try {
            ResponseEntity<String[]> response = restTemplate.getForEntity(listUrl, String[].class);

            response.getBody();
            if (response.getBody().length == 0) {
                System.out.println("No files available.");
                return;
            }

            List<String> files = Arrays.asList(response.getBody());
            System.out.println("Files available:");
            for (int i = 0; i < files.size(); i++) {
                System.out.println((i + 1) + ". " + files.get(i));
            }

            System.out.println("Enter the number of the file you want to download:");
            int fileIndex = scanner.nextInt();
            scanner.nextLine(); // Consume newline

            if (fileIndex > 0 && fileIndex <= files.size()) {
                String filename = files.get(fileIndex - 1);
                String cdnUrl = "http://cdn-node/cdn/" + series + "/" + type + "/" + filename;
                fetchAndSaveFile(restTemplate, cdnUrl, filename);
            } else {
                System.out.println("Invalid selection.");
            }
        } catch (Exception e) {
            System.out.println("Failed to fetch file list: " + e.getMessage());
        }
    }

    private static void fetchAndSaveFile(RestTemplate restTemplate, String url, String filename) {
        try {
            ResponseEntity<Resource> response = restTemplate.getForEntity(url, Resource.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                System.out.println("File fetched successfully: " + filename);
                saveFileLocally(response.getBody(), filename);
                System.out.println("File saved locally in 'downloads/' directory.");
            } else {
                System.out.println("Error fetching file: " + response.getStatusCode());
            }
        } catch (Exception e) {
            System.out.println("Failed to fetch file: " + e.getMessage());
        }

        // Shutdown the application after the file operation
        shutdownApplication();
    }

    private static void saveFileLocally(Resource resource, String filename) throws IOException {
        File downloadsDir = new File("downloads/");
        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs();
        }

        File file = new File(downloadsDir, filename);
        Files.copy(resource.getInputStream(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}