import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import com.sun.net.httpserver.*;

public class WorkingStudentApp {
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
        // Add sample data
        students.put(1, new Student(1, "John Doe", "john@email.com", "Computer Science", 20));
        students.put(2, new Student(2, "Jane Smith", "jane@email.com", "Mathematics", 22));
        nextId = 3;
        
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        
        server.createContext("/", new StaticHandler());
        server.createContext("/api/students", new StudentHandler());
        
        server.setExecutor(null);
        server.start();
        
        System.out.println("Server started at http://localhost:8080");
        System.out.println("Sample students added. Open browser to test!");
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
                sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
            }
        }
        
        private void handlePost(HttpExchange exchange) throws IOException {
            String body = readBody(exchange);
            System.out.println("POST body: " + body);
            
            // Simple form data parsing
            Map<String, String> params = parseFormData(body);
            
            String name = params.get("name");
            String email = params.get("email");
            String course = params.get("course");
            String ageStr = params.get("age");
            
            if (name != null && email != null && course != null && ageStr != null) {
                // Check for duplicate email
                for (Student existing : students.values()) {
                    if (existing.email.equalsIgnoreCase(email)) {
                        System.out.println("Duplicate email found: " + email);
                        sendResponse(exchange, 400, "{\"error\":\"Student with this email already exists\"}");
                        return;
                    }
                    if (existing.name.equalsIgnoreCase(name)) {
                        System.out.println("Duplicate name found: " + name);
                        sendResponse(exchange, 400, "{\"error\":\"Student with this name already exists\"}");
                        return;
                    }
                }
                
                try {
                    int age = Integer.parseInt(ageStr);
                    Student student = new Student(nextId++, name, email, course, age);
                    students.put(student.id, student);
                    
                    System.out.println("Added student: " + student.toJson());
                    sendResponse(exchange, 201, student.toJson());
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
            
            if (students.containsKey(id)) {
                String name = params.get("name");
                String email = params.get("email");
                String course = params.get("course");
                String ageStr = params.get("age");
                
                if (name != null && email != null && course != null && ageStr != null) {
                    // Check for duplicate email/name (excluding current student)
                    for (Student existing : students.values()) {
                        if (existing.id != id) {
                            if (existing.email.equalsIgnoreCase(email)) {
                                sendResponse(exchange, 400, "{\"error\":\"Student with this email already exists\"}");
                                return;
                            }
                            if (existing.name.equalsIgnoreCase(name)) {
                                sendResponse(exchange, 400, "{\"error\":\"Student with this name already exists\"}");
                                return;
                            }
                        }
                    }
                    
                    try {
                        int age = Integer.parseInt(ageStr);
                        Student student = new Student(id, name, email, course, age);
                        students.put(id, student);
                        sendResponse(exchange, 200, student.toJson());
                    } catch (NumberFormatException e) {
                        sendResponse(exchange, 400, "{\"error\":\"Invalid age\"}");
                    }
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Missing required fields\"}");
                }
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
            }
        }
        
        private void handleDelete(HttpExchange exchange, String path) throws IOException {
            int id = Integer.parseInt(path.substring(path.lastIndexOf("/") + 1));
            
            if (students.remove(id) != null) {
                sendResponse(exchange, 200, "{\"message\":\"Student deleted\"}");
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Student not found\"}");
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
            
            // Handle JSON format
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