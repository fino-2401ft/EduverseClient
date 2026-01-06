package org.example.eduverseclient.utils;

import lombok.extern.slf4j.Slf4j;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
public class MediaProcessor {
    
    private static final int MAX_IMAGE_WIDTH = 1920;
    private static final int MAX_IMAGE_HEIGHT = 1920;
    private static final float JPEG_QUALITY = 0.85f;
    private static final int THUMBNAIL_SIZE = 200;
    
    /**
     * Compress image với max dimensions và quality
     */
    public static byte[] compressImage(byte[] imageData, int maxWidth, float quality) {
        try {
            BufferedImage originalImage = ImageIO.read(new java.io.ByteArrayInputStream(imageData));
            if (originalImage == null) {
                log.warn("Failed to read image, returning original");
                return imageData;
            }
            
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            
            // Convert to RGB format (handle alpha channel)
            BufferedImage rgbImage;
            if (originalImage.getType() == BufferedImage.TYPE_INT_RGB) {
                rgbImage = originalImage;
            } else {
                rgbImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = rgbImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(originalImage, 0, 0, null);
                g.dispose();
            }
            
            // Resize nếu cần
            if (width > maxWidth || height > maxWidth) {
                double scale = Math.min((double) maxWidth / width, (double) maxWidth / height);
                int newWidth = (int) (width * scale);
                int newHeight = (int) (height * scale);
                
                BufferedImage resized = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = resized.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.drawImage(rgbImage, 0, 0, newWidth, newHeight, null);
                g.dispose();
                
                rgbImage = resized;
            }
            
            // Convert to JPEG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(rgbImage, "jpg", baos);
            if (!written) {
                log.warn("Failed to write JPEG, returning original");
                return imageData;
            }
            
            byte[] compressed = baos.toByteArray();
            if (compressed.length == 0) {
                log.warn("Compressed image is empty, returning original");
                return imageData;
            }
            
            log.info("Image compressed: {} -> {} bytes", imageData.length, compressed.length);
            
            return compressed;
            
        } catch (IOException e) {
            log.error("Failed to compress image", e);
            return imageData; // Return original if compression fails
        }
    }
    
    /**
     * Tạo thumbnail cho image
     */
    public static byte[] createThumbnail(byte[] imageData, int size) {
        try {
            BufferedImage originalImage = ImageIO.read(new java.io.ByteArrayInputStream(imageData));
            if (originalImage == null) {
                return null;
            }
            
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();
            
            // Calculate thumbnail dimensions
            double scale = Math.min((double) size / width, (double) size / height);
            int thumbWidth = (int) (width * scale);
            int thumbHeight = (int) (height * scale);
            
            BufferedImage thumbnail = new BufferedImage(thumbWidth, thumbHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = thumbnail.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(originalImage, 0, 0, thumbWidth, thumbHeight, null);
            g.dispose();
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(thumbnail, "jpg", baos);
            
            return baos.toByteArray();
            
        } catch (IOException e) {
            log.error("Failed to create thumbnail", e);
            return null;
        }
    }
    
    /**
     * Compress image với default settings
     */
    public static byte[] compressImage(byte[] imageData) {
        return compressImage(imageData, MAX_IMAGE_WIDTH, JPEG_QUALITY);
    }
    
    /**
     * Tạo thumbnail với default size
     */
    public static byte[] createThumbnail(byte[] imageData) {
        return createThumbnail(imageData, THUMBNAIL_SIZE);
    }
    
    /**
     * Detect file type từ file name
     */
    public static String getFileType(String fileName) {
        if (fileName == null) return "unknown";
        
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "application/msword";
        
        return "application/octet-stream";
    }
    
    /**
     * Check if file is image
     */
    public static boolean isImage(String fileName) {
        String type = getFileType(fileName);
        return type.startsWith("image/");
    }
    
    /**
     * Check if file is video
     */
    public static boolean isVideo(String fileName) {
        String type = getFileType(fileName);
        return type.startsWith("video/");
    }
}

