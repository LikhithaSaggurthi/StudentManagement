import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import com.sun.net.httpserver.*;

public class StudentWebApp {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/studentdb";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "sasql";
    
    static class Student {
        int id;
        String rollNumber, name, email, course;
        int age, courseId;
        
        Student(int id, String rollNumber, String name, String email, String course, int age, int courseId) {
            this.id = id; this.rollNumber = rollNumber; this.name = name; this.email = email; 
            this.course = course; this.age = age; this.courseId = courseId;
        }
        
        String toJson() {
            return String.format("{\"id\":%d,\"rollNumber\":\"%s\",\"name\":\"%s\",\"email\":\"%s\",\"course\":\"%s\",\"age\":%d,\"courseId\":%d}", 
                id, rollNumber, name, email, course, age, courseId);
        }
    }
    
    static class Course {
        int id;
        String courseName;
        
        Course(int id, String courseName) {
            this.id = id; this.courseName = courseName;
        }
        
        String toJson() {
            return String.format("{\"id\":%d,\"courseName\":\"%s\"}", id, courseName);
        }
    }
    
    public static void main(String[] args) throws Exception {
        Class.forName("org.postgresql.Driver");
        
        // Initialize database
        initializeDatabase();
        
        HttpServer server = HttpServer.create(new InetSocketAddress(7000), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/students", new StudentHandler());
        server.createContext("/api/courses", new CourseHandler());
        server.setExecutor(null);
        server.start();
        
        System.out.println("🚀 Student Management System running at:");
        System.out.println("📱 http://localhost:7000");
        System.out.println("✅ Connected to PostgreSQL database: studentdb");
    }
    
    private static void initializeDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
            // Drop existing tables to reset schema
            conn.createStatement().execute("DROP TABLE IF EXISTS students CASCADE");
            conn.createStatement().execute("DROP TABLE IF EXISTS courses CASCADE");
            
            // Create courses table
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS courses (" +
                "id SERIAL PRIMARY KEY, " +
                "course_name VARCHAR(100) NOT NULL UNIQUE)");
            
            // Insert courses
            String[] courses = {"Computers", "Mathematics", "Science", "ECE", "EEE", 
                               "Mechanical", "Artificial Intelligence", "Data Science", 
                               "Machine Learning", "Cyber Security", "Block Chain"};
            
            PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO courses (course_name) VALUES (?) ON CONFLICT (course_name) DO NOTHING");
            for (String course : courses) {
                stmt.setString(1, course);
                stmt.executeUpdate();
            }
            
            // Create students table
            conn.createStatement().execute(
                "CREATE TABLE IF NOT EXISTS students (" +
                "id SERIAL PRIMARY KEY, " +
                "roll_number VARCHAR(20) UNIQUE NOT NULL, " +
                "name VARCHAR(50) NOT NULL, " +
                "email VARCHAR(100) UNIQUE NOT NULL, " +
                "course_id INTEGER REFERENCES courses(id), " +
                "age INTEGER CHECK (age >= 16 AND age <= 25), " +
                "is_deleted INTEGER DEFAULT 0 CHECK (is_deleted IN (0, 1)), " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            
            // Create index
            conn.createStatement().execute(
                "CREATE INDEX IF NOT EXISTS idx_students_is_deleted ON students(is_deleted)");
            
            System.out.println("✅ Database initialized successfully");
        } catch (SQLException e) {
            System.err.println("❌ Database initialization failed: " + e.getMessage());
        }
    }
    
    static class StaticHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            File file = new File("src/main/resources/static" + path);
            if (file.exists()) {
                String contentType = path.endsWith(".html") ? "text/html" : 
                                   path.endsWith(".css") ? "text/css" : 
                                   path.endsWith(".js") ? "application/javascript" : "text/plain";
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, file.length());
                
                try (FileInputStream fis = new FileInputStream(file);
                     OutputStream os = exchange.getResponseBody()) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, bytesRead);
                    }
                }
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            }
        }
    }
    
    static class CourseHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            if (exchange.getRequestMethod().equals("GET")) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM courses ORDER BY course_name");
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    while (rs.next()) {
                        if (!first) json.append(",");
                        json.append(new Course(rs.getInt("id"), rs.getString("course_name")).toJson());
                        first = false;
                    }
                    json.append("]");
                    sendResponse(exchange, 200, json.toString());
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
                }
            }
        }
        
        private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
    
    static class StudentHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
            
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();
            
            if (method.equals("OPTIONS")) {
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
                return;
            }
            
            try {
                switch (method) {
                    case "GET":
                        if (path.equals("/api/students")) handleGetAll(exchange);
                        else handleGetById(exchange, path);
                        break;
                    case "POST":
                        handlePost(exchange);
                        break;
                    case "PUT":
                        handlePut(exchange, path);
                        break;
                    case "DELETE":
                        handleDelete(exchange, path);
                        break;
                }
            } catch (Exception e) {
                sendResponse(exchange, 500, "{\"error\":\"Server error\"}");
            }
        }
        
        private void handleGetAll(HttpExchange exchange) throws IOException {
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT s.id, s.roll_number, s.name, s.email, c.course_name, s.age, s.course_id " +
                              "FROM students s JOIN courses c ON s.course_id = c.id WHERE s.is_deleted = 0 ORDER BY s.id";
                ResultSet rs = conn.createStatement().executeQuery(query);
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    json.append(new Student(rs.getInt("id"), rs.getString("roll_number"), rs.getString("name"), 
                        rs.getString("email"), rs.getString("course_name"), rs.getInt("age"), rs.getInt("course_id")).toJson());
                    first = false;
                }
                json.append("]");
                sendResponse(exchange, 200, json.toString());
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
            }
        }
        
        private void handleGetById(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                String query = "SELECT s.id, s.roll_number, s.name, s.email, c.course_name, s.age, s.course_id " +
                              "FROM students s JOIN courses c ON s.course_id = c.id WHERE s.id = ? AND s.is_deleted = 0";
                PreparedStatement stmt = conn.prepareStatement(query);
                stmt.setInt(1, id);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    sendResponse(exchange, 200, new Student(rs.getInt("id"), rs.getString("roll_number"), rs.getString("name"), 
                        rs.getString("email"), rs.getString("course_name"), rs.getInt("age"), rs.getInt("course_id")).toJson());
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
                }
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
            }
        }
        
        private void handlePost(HttpExchange exchange) throws IOException {
            Map<String, String> params = parseJson(readBody(exchange));
            String rollNumber = params.get("rollNumber"), name = params.get("name"), email = params.get("email"), 
                   courseIdStr = params.get("courseId"), ageStr = params.get("age");
            
            if (rollNumber != null && name != null && email != null && courseIdStr != null && ageStr != null) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    // Check for duplicate roll number
                    PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM students WHERE roll_number = ? AND is_deleted = 0");
                    checkStmt.setString(1, rollNumber);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next() && rs.getInt(1) > 0) {
                        sendResponse(exchange, 400, "{\"error\":\"Student with this roll number already exists\"}");
                        return;
                    }
                    
                    String insertQuery = "INSERT INTO students (roll_number, name, email, course_id, age) VALUES (?, ?, ?, ?, ?) RETURNING *";
                    PreparedStatement stmt = conn.prepareStatement(insertQuery);
                    stmt.setString(1, rollNumber);
                    stmt.setString(2, name);
                    stmt.setString(3, email);
                    stmt.setInt(4, Integer.parseInt(courseIdStr));
                    stmt.setInt(5, Integer.parseInt(ageStr));
                    
                    ResultSet result = stmt.executeQuery();
                    if (result.next()) {
                        // Get course name for response
                        PreparedStatement courseStmt = conn.prepareStatement("SELECT course_name FROM courses WHERE id = ?");
                        courseStmt.setInt(1, result.getInt("course_id"));
                        ResultSet courseRs = courseStmt.executeQuery();
                        String courseName = courseRs.next() ? courseRs.getString("course_name") : "";
                        
                        sendResponse(exchange, 201, new Student(result.getInt("id"), result.getString("roll_number"), 
                            result.getString("name"), result.getString("email"), courseName, 
                            result.getInt("age"), result.getInt("course_id")).toJson());
                    }
                } catch (SQLException e) {
                    sendResponse(exchange, 400, "{\"error\":\"Database error: " + e.getMessage() + "\"}");
                }
            } else {
                sendResponse(exchange, 400, "{\"error\":\"Missing required fields\"}");
            }
        }
        
        private void handlePut(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            Map<String, String> params = parseJson(readBody(exchange));
            String rollNumber = params.get("rollNumber"), name = params.get("name"), email = params.get("email"), 
                   courseIdStr = params.get("courseId"), ageStr = params.get("age");
            
            if (rollNumber != null && name != null && email != null && courseIdStr != null && ageStr != null) {
                try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                    // Check for duplicate roll number (excluding current student)
                    PreparedStatement checkStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM students WHERE roll_number = ? AND id != ? AND is_deleted = 0");
                    checkStmt.setString(1, rollNumber);
                    checkStmt.setInt(2, id);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next() && rs.getInt(1) > 0) {
                        sendResponse(exchange, 400, "{\"error\":\"Student with this roll number already exists\"}");
                        return;
                    }
                    
                    String updateQuery = "UPDATE students SET roll_number = ?, name = ?, email = ?, course_id = ?, age = ? WHERE id = ? AND is_deleted = 0 RETURNING *";
                    PreparedStatement stmt = conn.prepareStatement(updateQuery);
                    stmt.setString(1, rollNumber);
                    stmt.setString(2, name);
                    stmt.setString(3, email);
                    stmt.setInt(4, Integer.parseInt(courseIdStr));
                    stmt.setInt(5, Integer.parseInt(ageStr));
                    stmt.setInt(6, id);
                    
                    ResultSet result = stmt.executeQuery();
                    if (result.next()) {
                        // Get course name for response
                        PreparedStatement courseStmt = conn.prepareStatement("SELECT course_name FROM courses WHERE id = ?");
                        courseStmt.setInt(1, result.getInt("course_id"));
                        ResultSet courseRs = courseStmt.executeQuery();
                        String courseName = courseRs.next() ? courseRs.getString("course_name") : "";
                        
                        sendResponse(exchange, 200, new Student(result.getInt("id"), result.getString("roll_number"), 
                            result.getString("name"), result.getString("email"), courseName, 
                            result.getInt("age"), result.getInt("course_id")).toJson());
                    } else {
                        sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
                    }
                } catch (SQLException e) {
                    sendResponse(exchange, 400, "{\"error\":\"Database error: " + e.getMessage() + "\"}");
                }
            } else {
                sendResponse(exchange, 400, "{\"error\":\"Missing required fields\"}");
            }
        }
        
        private void handleDelete(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD)) {
                // Soft delete - set is_deleted = 1
                PreparedStatement stmt = conn.prepareStatement("UPDATE students SET is_deleted = 1 WHERE id = ? AND is_deleted = 0");
                stmt.setInt(1, id);
                int rowsAffected = stmt.executeUpdate();
                
                if (rowsAffected > 0) {
                    sendResponse(exchange, 200, "{\"message\":\"Student deleted successfully\"}");
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
                }
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
            }
        }
        
        private String readBody(HttpExchange exchange) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
                return reader.lines().reduce("", String::concat);
            }
        }
        
        private Map<String, String> parseJson(String json) {
            Map<String, String> params = new HashMap<>();
            if (json.startsWith("{")) {
                String[] pairs = json.replaceAll("[{}\"\\s]", "").split(",");
                for (String pair : pairs) {
                    String[] kv = pair.split(":");
                    if (kv.length == 2) params.put(kv[0], kv[1]);
                }
            }
            return params;
        }
        
        private void sendResponse(HttpExchange exchange, int code, String response) throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(code, response.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response.getBytes());
            }
        }
    }
}