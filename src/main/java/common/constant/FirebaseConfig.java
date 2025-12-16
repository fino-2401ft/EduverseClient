package common.constant;

//file này để lưu các hằng số cấu hình liên quan đến Firebase
public class FirebaseConfig {
    // Firebase Realtime Database Paths
    public static final String USERS_PATH = "users";
    public static final String COURSES_PATH = "courses";
    public static final String LESSONS_PATH = "lessons";
    public static final String MEETINGS_PATH = "meetings";
    public static final String CONVERSATIONS_PATH = "conversations";
    public static final String MESSAGES_PATH = "messages";
    public static final String FILES_PATH = "files";
    
    // Message Room Prefixes
    public static final String COURSE_CHAT_PREFIX = "course_";
    public static final String PRIVATE_CHAT_PREFIX = "private_";
    public static final String MEETING_CHAT_PREFIX = "meeting_";
    
    // Firebase Storage Paths
    public static final String AVATARS_PATH = "avatars/";
    public static final String COURSE_MATERIALS_PATH = "course_materials/";
    
    // Cloudinary Configuration (for file upload)
    public static final String CLOUDINARY_CLOUD_NAME = "your_cloud_name";
    public static final String CLOUDINARY_API_KEY = "your_api_key";
    public static final String CLOUDINARY_API_SECRET = "your_api_secret";



}