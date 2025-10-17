# Student Management System

A full-stack web application for managing student records with CRUD operations.

## Features
- Add new students
- View all students
- Edit student information
- Delete students
- Responsive design
- Real-time updates

## Technology Stack
- **Frontend**: HTML, CSS, JavaScript
- **Backend**: Java Spring Boot
- **Database**: PostgreSQL

## Setup Instructions

### Prerequisites
- Java 11 or higher
- Maven
- PostgreSQL database

### Database Setup
1. Install PostgreSQL
2. Create a database named `student_db`
3. Update database credentials in `src/main/resources/application.properties`

### Running the Application
1. Navigate to project directory
2. Run: `mvn spring-boot:run`
3. Open browser and go to: `http://localhost:8080`

### API Endpoints
- GET `/api/students` - Get all students
- GET `/api/students/{id}` - Get student by ID
- POST `/api/students` - Create new student
- PUT `/api/students/{id}` - Update student
- DELETE `/api/students/{id}` - Delete student

## Usage
1. Fill in the form to add a new student
2. Click "Add Student" to save
3. Use "Edit" button to modify existing records
4. Use "Delete" button to remove records
5. All changes are automatically saved to PostgreSQL database