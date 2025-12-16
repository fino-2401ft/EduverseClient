package common.model;

import lombok.*;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lesson implements Serializable {
    private static final long serialVersionUID = 1L;
    
    private String lessonId;
    private String courseId;
    
    private String title;
    private String description;
    private String content;        // Nội dung text
    
    private String videoUrl;       // Link video bài giảng
    private int videoDuration;     // Thời lượng (giây)
    
    private int orderIndex;        // Thứ tự bài học
    
    @Builder.Default
    private List<String> fileIds = new ArrayList<>();  // Tài liệu đính kèm
    
    @Builder.Default
    private long createdAt = System.currentTimeMillis();
    
    @Builder.Default
    private boolean isPublished = false;
    
    @Builder.Default
    private boolean isFree = false;  // Bài học miễn phí (preview)
}