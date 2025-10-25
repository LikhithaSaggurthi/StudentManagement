-- Updated PostgreSQL Database Setup Script
-- Run this in PostgreSQL to update the database schema

-- Connect to the database
\c student_db;

-- Create courses table
CREATE TABLE IF NOT EXISTS courses (
    id SERIAL PRIMARY KEY,
    course_name VARCHAR(100) NOT NULL UNIQUE
);

-- Insert predefined courses
INSERT INTO courses (course_name) VALUES 
('Computers'),
('Mathematics'),
('Science'),
('ECE'),
('EEE'),
('Mechanical'),
('Artificial Intelligence'),
('Data Science'),
('Machine Learning'),
('Cyber Security'),
('Block Chain')
ON CONFLICT (course_name) DO NOTHING;

-- Drop existing students table if exists and recreate with new structure
DROP TABLE IF EXISTS students;

-- Create updated students table with roll_number and is_deleted
CREATE TABLE students (
    id SERIAL PRIMARY KEY,
    roll_number VARCHAR(20) UNIQUE NOT NULL,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    course_id INTEGER REFERENCES courses(id),
    age INTEGER CHECK (age >= 16 AND age <= 25),
    is_deleted INTEGER DEFAULT 0 CHECK (is_deleted IN (0, 1)),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create index for better performance on soft delete queries
CREATE INDEX idx_students_is_deleted ON students(is_deleted);

-- Verify tables
SELECT * FROM courses;
SELECT * FROM students;