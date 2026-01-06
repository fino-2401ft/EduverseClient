package org.example.eduverseclient.service;


import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import common.constant.FirebaseConfig;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CloudinaryService {
    private static CloudinaryService instance;
    private final Cloudinary cloudinary;
    
    private CloudinaryService() {
        // Initialize Cloudinary config
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", FirebaseConfig.CLOUDINARY_CLOUD_NAME);
        config.put("api_key", FirebaseConfig.CLOUDINARY_API_KEY);
        config.put("api_secret", FirebaseConfig.CLOUDINARY_API_SECRET);
        this.cloudinary = new Cloudinary(config);
        log.info("Cloudinary initialized with cloud_name: {}", FirebaseConfig.CLOUDINARY_CLOUD_NAME);
    }
    
    public static synchronized CloudinaryService getInstance() {
        if (instance == null) {
            instance = new CloudinaryService();
        }
        return instance;
    }
    
    @Data
    public static class UploadResult {
        private String fileUrl;
        private String thumbnailUrl;
        private long fileSize;
    }
    
    /**
     * Upload file lên Cloudinary
     */
    public CompletableFuture<UploadResult> uploadFile(byte[] fileData, String fileName, String fileType) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Uploading file to Cloudinary: {} ({} bytes)", fileName, fileData.length);
                
                // Prepare upload parameters using ObjectUtils
                @SuppressWarnings("unchecked")
                Map<String, Object> uploadResult = cloudinary.uploader().upload(
                    fileData,
                    ObjectUtils.asMap(
                        "resource_type", "auto", // Auto-detect image/video/raw
                        "public_id", "messages/" + UUID.randomUUID(),
                        "folder", "eduverse/messages" // Organize files in folder
                    )
                );
                
                UploadResult result = new UploadResult();
                result.fileUrl = (String) uploadResult.get("secure_url"); // Use secure_url (HTTPS)
                result.fileSize = fileData.length;
                
                // Generate thumbnail URL for images/videos
                if (fileType.startsWith("image/")) {
                    String publicId = (String) uploadResult.get("public_id");
                    result.thumbnailUrl = cloudinary.url()
                            .transformation(new Transformation<>()
                                    .width(200).height(200).crop("fill"))
                            .generate(publicId);

                } else if (fileType.startsWith("video/")) {
                    String publicId = (String) uploadResult.get("public_id");
                    result.thumbnailUrl = cloudinary.url()
                            .resourceType("video")
                            .transformation(new Transformation<>()
                                    .width(200).height(200).crop("fill"))
                            .generate(publicId + ".jpg");
                }


                log.info("File uploaded successfully: {}", result.fileUrl);
                return result;
                
            } catch (Exception e) {
                log.error("Cloudinary upload failed", e);
                throw new RuntimeException("Upload failed: " + e.getMessage(), e);
            }
        });
    }
    
    /**
     * Upload image với auto thumbnail
     */
    public CompletableFuture<UploadResult> uploadImage(byte[] imageData, String fileName) {
        return uploadFile(imageData, fileName, "image/jpeg");
    }
    
    /**
     * Upload video với auto thumbnail
     */
    public CompletableFuture<UploadResult> uploadVideo(byte[] videoData, String fileName) {
        return uploadFile(videoData, fileName, "video/mp4");
    }
}

