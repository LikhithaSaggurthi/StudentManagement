const API_URL = 'http://localhost:8080/api/students';
let isEditing = false;

document.addEventListener('DOMContentLoaded', function() {
    loadStudents();
    
    document.getElementById('student-form').addEventListener('submit', function(e) {
        e.preventDefault();
        if (isEditing) {
            updateStudent();
        } else {
            addStudent();
        }
    });
});

async function loadStudents() {
    try {
        const response = await fetch(API_URL);
        const students = await response.json();
        displayStudents(students);
    } catch (error) {
        console.error('Error loading students:', error);
        alert('Error loading students. Please check if the server is running.');
    }
}

function displayStudents(students) {
    const tbody = document.getElementById('students-tbody');
    tbody.innerHTML = '';
    
    students.forEach(student => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${student.id}</td>
            <td>${student.name}</td>
            <td>${student.email}</td>
            <td>${student.course}</td>
            <td>${student.age}</td>
            <td>
                <button class="action-btn edit-btn" onclick="editStudent(${student.id})">Edit</button>
                <button class="action-btn delete-btn" onclick="deleteStudent(${student.id})">Delete</button>
            </td>
        `;
        tbody.appendChild(row);
    });
}

async function addStudent() {
    if (!validateForm()) {
        return;
    }
    
    const student = getFormData();
    
    // Check for duplicates on client side
    try {
        const response = await fetch(API_URL);
        const existingStudents = await response.json();
        
        const duplicateEmail = existingStudents.find(s => s.email.toLowerCase() === student.email.toLowerCase());
        if (duplicateEmail) {
            alert('Student with this email already exists!');
            return;
        }
        
        const duplicateName = existingStudents.find(s => s.name.toLowerCase() === student.name.toLowerCase());
        if (duplicateName) {
            alert('Student with this name already exists!');
            return;
        }
    } catch (error) {
        console.error('Error checking duplicates:', error);
    }
    
    try {
        const response = await fetch(API_URL, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(student)
        });
        
        if (response.ok) {
            alert('Student added successfully!');
            resetForm();
            loadStudents();
        } else {
            try {
                const errorData = await response.json();
                alert(errorData.error);
            } catch (e) {
                alert('Error adding student. Please check the data.');
            }
        }
    } catch (error) {
        console.error('Error adding student:', error);
        alert('Error adding student. Please check if the server is running.');
    }
}

async function editStudent(id) {
    try {
        const response = await fetch(`${API_URL}/${id}`);
        const student = await response.json();
        
        document.getElementById('student-id').value = student.id;
        document.getElementById('name').value = student.name;
        document.getElementById('email').value = student.email;
        document.getElementById('course').value = student.course;
        document.getElementById('age').value = student.age;
        
        document.getElementById('form-title').textContent = 'Edit Student';
        document.getElementById('submit-btn').textContent = 'Update Student';
        isEditing = true;
        
        document.getElementById('name').focus();
    } catch (error) {
        console.error('Error loading student:', error);
        alert('Error loading student data.');
    }
}

async function updateStudent() {
    if (!validateForm()) {
        return;
    }
    
    const id = document.getElementById('student-id').value;
    const student = getFormData();
    
    // Check for duplicates on client side (excluding current student)
    try {
        const response = await fetch(API_URL);
        const existingStudents = await response.json();
        
        const duplicateEmail = existingStudents.find(s => s.id != id && s.email.toLowerCase() === student.email.toLowerCase());
        if (duplicateEmail) {
            alert('Student with this email already exists!');
            return;
        }
        
        const duplicateName = existingStudents.find(s => s.id != id && s.name.toLowerCase() === student.name.toLowerCase());
        if (duplicateName) {
            alert('Student with this name already exists!');
            return;
        }
    } catch (error) {
        console.error('Error checking duplicates:', error);
    }
    
    try {
        const response = await fetch(`${API_URL}/${id}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
            },
            body: JSON.stringify(student)
        });
        
        if (response.ok) {
            alert('Student updated successfully!');
            resetForm();
            loadStudents();
        } else {
            try {
                const errorData = await response.json();
                alert(errorData.error);
            } catch (e) {
                alert('Error updating student. Please check the data.');
            }
        }
    } catch (error) {
        console.error('Error updating student:', error);
        alert('Error updating student. Please check if the server is running.');
    }
}

async function deleteStudent(id) {
    if (confirm('Are you sure you want to delete this student?')) {
        try {
            const response = await fetch(`${API_URL}/${id}`, {
                method: 'DELETE'
            });
            
            if (response.ok) {
                alert('Student deleted successfully!');
                loadStudents();
            } else {
                alert('Error deleting student.');
            }
        } catch (error) {
            console.error('Error deleting student:', error);
            alert('Error deleting student. Please check if the server is running.');
        }
    }
}

function validateForm() {
    const name = document.getElementById('name').value.trim();
    const email = document.getElementById('email').value.trim();
    const course = document.getElementById('course').value.trim();
    const age = document.getElementById('age').value;
    
    // Name validation
    if (!name || name.length < 2) {
        alert('Name must be at least 2 characters long');
        return false;
    }
    if (!/^[A-Za-z\s]+$/.test(name)) {
        alert('Name should contain only letters and spaces');
        return false;
    }
    
    // Email validation
    if (!email) {
        alert('Email is required');
        return false;
    }
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
        alert('Please enter a valid email address');
        return false;
    }
    
    // Course validation
    if (!course || course.length < 2) {
        alert('Course name must be at least 2 characters long');
        return false;
    }
    
    // Age validation
    if (!age || age < 16 || age > 100) {
        alert('Age must be between 16 and 100');
        return false;
    }
    
    return true;
}

function getFormData() {
    return {
        name: document.getElementById('name').value.trim(),
        email: document.getElementById('email').value.trim(),
        course: document.getElementById('course').value.trim(),
        age: parseInt(document.getElementById('age').value)
    };
}

function resetForm() {
    document.getElementById('student-form').reset();
    document.getElementById('student-id').value = '';
    document.getElementById('form-title').textContent = 'Add New Student';
    document.getElementById('submit-btn').textContent = 'Add Student';
    isEditing = false;
}