import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;
import com.sun.net.httpserver.*;

public class PostgreSQLStudentApp {
    static {
        try {
            Class.forName("org.postgresql.Driver");
            PostgresConnection.testConnection();
            PostgresConnection.initDatabase();
            System.out.println("✅ Successfully connected to PostgreSQL database!");
        } catch (Exception e) {
            System.err.println("❌ Database connection failed: " + e.getMessage());
            System.err.println("Please ensure PostgreSQL is running and student_db exists");
        }
    }
    
    static class Student {
        int id;
        String name;
        String email;
        String course;
        int age;
        
        Student(int id, String name, String email, String course, int age) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.course = course;
            this.age = age;
        }
        
        String toJson() {
            return String.format("{\"id\":%d,\"name\":\"%s\",\"email\":\"%s\",\"course\":\"%s\",\"age\":%d}", 
                id, name, email, course, age);
        }
    }
    

    
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/", new StaticHandler());
        server.createContext("/api/students", new StudentHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("Server started at http://localhost:8080");
        System.out.println("Connected to PostgreSQL database: student_db");
    }
    
    static class StaticHandler implements HttpHandler {
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            
            File file = new File("src/main/resources/static" + path);
            if (file.exists()) {
                String contentType = getContentType(path);
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
        
        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            return "text/plain";
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
                        if (path.equals("/api/students")) {
                            handleGetAll(exchange);
                        } else {
                            handleGetById(exchange, path);
                        }
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
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\":\"Server error\"}");
            }
        }
        
        private void handleGetAll(HttpExchange exchange) throws IOException {
            try (Connection conn = PostgresConnection.getConnection()) {
                String sql = "SELECT * FROM students ORDER BY id";
                ResultSet rs = conn.createStatement().executeQuery(sql);
                
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    Student student = new Student(rs.getInt("id"), rs.getString("name"), 
                        rs.getString("email"), rs.getString("course"), rs.getInt("age"));
                    json.append(student.toJson());
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
            
            try (Connection conn = PostgresConnection.getConnection()) {
                String sql = "SELECT * FROM students WHERE id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, id);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next()) {
                    Student student = new Student(rs.getInt("id"), rs.getString("name"), 
                        rs.getString("email"), rs.getString("course"), rs.getInt("age"));
                    sendResponse(exchange, 200, student.toJson());
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
                }
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
            }
        }
        
        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            Map<String, String> params = parseFormData(body);
            
            String name = params.get("name");
            String email = params.get("email");
            String course = params.get("course");
            String ageStr = params.get("age");
            
            if (name != null && email != null && course != null && ageStr != null) {
                try (Connection conn = PostgresConnection.getConnection()) {
                    // Check for duplicates
                    String checkSql = "SELECT COUNT(*) FROM students WHERE LOWER(email) = LOWER(?) OR LOWER(name) = LOWER(?)";
                    PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                    checkStmt.setString(1, email);
                    checkStmt.setString(2, name);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next() && rs.getInt(1) > 0) {
                        sendResponse(exchange, 400, "{\"error\":\"Student with this name or email already exists\"}");
                        return;
                    }
                    
                    // Insert new student
                    String sql = "INSERT INTO students (name, email, course, age) VALUES (?, ?, ?, ?) RETURNING *";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, name);
                    stmt.setString(2, email);
                    stmt.setString(3, course);
                    stmt.setInt(4, Integer.parseInt(ageStr));
                    
                    ResultSet result = stmt.executeQuery();
                    if (result.next()) {
                        Student student = new Student(result.getInt("id"), result.getString("name"), 
                            result.getString("email"), result.getString("course"), result.getInt("age"));
                        sendResponse(exchange, 201, student.toJson());
                    }
                } catch (SQLException e) {
                    if (e.getMessage().contains("duplicate key")) {
                        sendResponse(exchange, 400, "{\"error\":\"Student with this email already exists\"}");
                    } else {
                        sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
                    }
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid age\"}");
                }
            } else {
                sendResponse(exchange, 400, "{\"error\":\"Missing required fields\"}");
            }
        }
        
        private void handlePut(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            String body = readBody(exchange);
            Map<String, String> params = parseFormData(body);
            
            String name = params.get("name");
            String email = params.get("email");
            String course = params.get("course");
            String ageStr = params.get("age");
            
            if (name != null && email != null && course != null && ageStr != null) {
                try (Connection conn = PostgresConnection.getConnection()) {
                    // Check for duplicates (excluding current student)
                    String checkSql = "SELECT COUNT(*) FROM students WHERE id != ? AND (LOWER(email) = LOWER(?) OR LOWER(name) = LOWER(?))";
                    PreparedStatement checkStmt = conn.prepareStatement(checkSql);
                    checkStmt.setInt(1, id);
                    checkStmt.setString(2, email);
                    checkStmt.setString(3, name);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next() && rs.getInt(1) > 0) {
                        sendResponse(exchange, 400, "{\"error\":\"Student with this name or email already exists\"}");
                        return;
                    }
                    
                    // Update student
                    String sql = "UPDATE students SET name = ?, email = ?, course = ?, age = ? WHERE id = ? RETURNING *";
                    PreparedStatement stmt = conn.prepareStatement(sql);
                    stmt.setString(1, name);
                    stmt.setString(2, email);
                    stmt.setString(3, course);
                    stmt.setInt(4, Integer.parseInt(ageStr));
                    stmt.setInt(5, id);
                    
                    ResultSet result = stmt.executeQuery();
                    if (result.next()) {
                        Student student = new Student(result.getInt("id"), result.getString("name"), 
                            result.getString("email"), result.getString("course"), result.getInt("age"));
                        sendResponse(exchange, 200, student.toJson());
                    } else {
                        sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
                    }
                } catch (SQLException e) {
                    sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
                } catch (NumberFormatException e) {
                    sendResponse(exchange, 400, "{\"error\":\"Invalid age\"}");
                }
            } else {
                sendResponse(exchange, 400, "{\"error\":\"Missing required fields\"}");
            }
        }
        
        private void handleDelete(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            
            try (Connection conn = PostgresConnection.getConnection()) {
                String sql = "DELETE FROM students WHERE id = ?";
                PreparedStatement stmt = conn.prepareStatement(sql);
                stmt.setInt(1, id);
                
                int rowsAffected = stmt.executeUpdate();
                if (rowsAffected > 0) {
                    sendResponse(exchange, 200, "{\"message\":\"Student deleted\"}");
                } else {
                    sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
                }
            } catch (SQLException e) {
                sendResponse(exchange, 500, "{\"error\":\"Database error\"}");
            }
        }
        
        private String readBody(HttpExchange exchange) throws IOException {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(exchange.getRequestBody()))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    body.append(line);
                }
                return body.toString();
            }
        }
        
        private Map<String, String> parseFormData(String data) {
            Map<String, String> params = new HashMap<>();
            
            if (data.startsWith("{")) {
                String[] pairs = data.replaceAll("[{}\"\\s]", "").split(",");
                for (String pair : pairs) {
                    String[] keyValue = pair.split(":");
                    if (keyValue.length == 2) {
                        params.put(keyValue[0], keyValue[1]);
                    }
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