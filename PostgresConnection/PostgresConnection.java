import java.sql.*;

public class PostgresConnection {
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/studentdb";
    private static final String DB_USER = "postgres";
    private static final String DB_PASSWORD = "sasql"; // Add your PostgreSQL password here
    
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
    }
    
    public static void testConnection() throws SQLException {
        try (Connection conn = getConnection()) {
            System.out.println("✅ Database connection successful!");
        }
    }
    
    public static void initDatabase() throws SQLException {
        try (Connection conn = getConnection()) {
            String createTable = """
                CREATE TABLE IF NOT EXISTS students (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(50) NOT NULL,
                    email VARCHAR(100) UNIQUE NOT NULL,
                    course VARCHAR(50) NOT NULL,
                    age INTEGER CHECK (age >= 16 AND age <= 100)
                )
                """;
            conn.createStatement().execute(createTable);
            
            // Add sample data if table is empty
            String countSql = "SELECT COUNT(*) FROM students";
            ResultSet rs = conn.createStatement().executeQuery(countSql);
            if (rs.next() && rs.getInt(1) == 0) {
                String insertSample = """
                    INSERT INTO students (name, email, course, age) VALUES 
                    ('John Doe', 'john@email.com', 'Computer Science', 20),
                    ('Jane Smith', 'jane@email.com', 'Mathematics', 22)
                    """;
                conn.createStatement().execute(insertSample);
                System.out.println("📊 Sample data added to database");
            }
            
            System.out.println("📊 Database table ready");
        }
    }
    
    public static void main(String[] args) {
        try {
            Class.forName("org.postgresql.Driver");
            testConnection();
            initDatabase();
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
        }
    }
}