-- PostgreSQL Database Setup Script
-- Run this in PostgreSQL to create the database and user

-- Create database
CREATE DATABASE student_db;

-- Connect to the database
\c student_db;

-- Create students table
CREATE TABLE IF NOT EXISTS students (
    id SERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    course VARCHAR(50) NOT NULL,
    age INTEGER CHECK (age >= 16 AND age <= 100)
);

-- Insert sample data
INSERT INTO students (name, email, course, age) VALUES 
('John Doe', 'john@email.com', 'Computer Science', 20),
('Jane Smith', 'jane@email.com', 'Mathematics', 22)
ON CONFLICT (email) DO NOTHING;

-- Verify data
SELECT * FROM students;