package com.example.originserver.utils;
import java.io.*;

public class FileSerializer {
    public static void serializeFile(File file, OutputStream outputStream) throws IOException {
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096]; // Read in chunks of 4KB
            int bytesRead;

            // Write file content to the output stream
            while ((bytesRead = fis.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
        }
    }
}