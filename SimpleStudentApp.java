import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.net.httpserver.*;

public class SimpleStudentApp {
    private static Map<Integer, Student> students = new ConcurrentHashMap<>();
    private static int nextId = 1;
    
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
        System.out.println("Open your browser and go to: http://localhost:8080");
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
                exchange.sendResponseHeaders(500, 0);
                exchange.getResponseBody().close();
            }
        }
        
        private void handleGetAll(HttpExchange exchange) throws IOException {
            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            for (Student student : students.values()) {
                if (!first) json.append(",");
                json.append(student.toJson());
                first = false;
            }
            json.append("]");
            
            sendResponse(exchange, 200, json.toString());
        }
        
        private void handleGetById(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            Student student = students.get(id);
            
            if (student != null) {
                sendResponse(exchange, 200, student.toJson());
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            }
        }
        
        private void handlePost(HttpExchange exchange) throws IOException {
            try {
                String body = readBody(exchange);
                System.out.println("Received POST data: " + body);
                
                Student student = parseStudent(body);
                student.id = nextId++;
                students.put(student.id, student);
                
                System.out.println("Created student: " + student.toJson());
                sendResponse(exchange, 201, student.toJson());
            } catch (Exception e) {
                System.err.println("Error in POST: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 400, "{\"error\":\"Invalid data\"}");
            }
        }
        
        private void handlePut(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            String body = readBody(exchange);
            Student updatedStudent = parseStudent(body);
            updatedStudent.id = id;
            
            if (students.containsKey(id)) {
                students.put(id, updatedStudent);
                sendResponse(exchange, 200, updatedStudent.toJson());
            } else {
                exchange.sendResponseHeaders(404, 0);
                exchange.getResponseBody().close();
            }
        }
        
        private void handleDelete(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            
            if (students.remove(id) != null) {
                exchange.sendResponseHeaders(200, 0);
            } else {
                exchange.sendResponseHeaders(404, 0);
            }
            exchange.getResponseBody().close();
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
        
        private Student parseStudent(String json) {
            try {
                String name = extractValue(json, "name");
                String email = extractValue(json, "email");
                String course = extractValue(json, "course");
                String ageStr = extractValue(json, "age");
                
                System.out.println("Parsed values - Name: " + name + ", Email: " + email + ", Course: " + course + ", Age: " + ageStr);
                
                int age = Integer.parseInt(ageStr);
                return new Student(0, name, email, course, age);
            } catch (Exception e) {
                System.err.println("Error parsing student: " + e.getMessage());
                throw new RuntimeException("Failed to parse student data", e);
            }
        }
        
        private String extractValue(String json, String key) {
            String stringPattern = "\"" + key + "\":\"";
            int stringStart = json.indexOf(stringPattern);
            
            if (stringStart != -1) {
                // String value
                int start = stringStart + stringPattern.length();
                int end = json.indexOf("\"", start);
                return json.substring(start, end);
            } else {
                // Numeric value
                String numPattern = "\"" + key + "\":";
                int numStart = json.indexOf(numPattern);
                if (numStart != -1) {
                    int start = numStart + numPattern.length();
                    int end = json.indexOf(",", start);
                    if (end == -1) end = json.indexOf("}", start);
                    return json.substring(start, end).trim();
                }
            }
            return "";
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