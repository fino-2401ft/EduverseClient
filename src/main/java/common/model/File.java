package common.model;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class File implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String fileId;
    private String ownerId;        // lessonId or messageId
    private String ownerType;
    
    private String fileName;
    private String fileType;       // pdf, docx, jpg, mp4, etc.
    private String fileUrl;        // Cloudinary URL
    private long fileSize;
    
    private String thumbnailUrl;   // For images/videos
    
    private String uploadedBy;
    
    @Builder.Default
    private long uploadedAt = System.currentTimeMillis();
}