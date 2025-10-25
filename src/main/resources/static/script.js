const API_URL = 'http://localhost:7000/api/students';
const COURSES_URL = 'http://localhost:7000/api/courses';
let isEditing = false;

document.addEventListener('DOMContentLoaded', function() {
    loadCourses();
    loadStudents();
    
    document.getElementById('student-form').addEventListener('submit', function(e) {
        e.preventDefault();
        if (isEditing) {
            updateStudent();
        } else {
            addStudent();
        }
    });
    
    // Add input validation for name field (no numbers)
    document.getElementById('name').addEventListener('input', function(e) {
        const value = e.target.value;
        // Remove any numbers from the name field
        e.target.value = value.replace(/[0-9]/g, '');
    });
    
    // Add input validation for phone number field (only numbers, no leading 0)
    document.getElementById('phoneNumber').addEventListener('input', function(e) {
        let value = e.target.value.replace(/[^0-9]/g, '');
        // Prevent starting with 0
        if (value.startsWith('0')) {
            value = value.substring(1);
        }
        // Limit to 10 digits
        e.target.value = value.substring(0, 10);
    });
    
    // Set max date for date of birth (today's date)
    const today = new Date().toISOString().split('T')[0];
    document.getElementById('dateOfBirth').setAttribute('max', today);
    
    // Set min date (30 years ago)
    const minDate = new Date();
    minDate.setFullYear(minDate.getFullYear() - 30);
    document.getElementById('dateOfBirth').setAttribute('min', minDate.toISOString().split('T')[0]);
});

async function loadCourses() {
    try {
        const response = await fetch(COURSES_URL);
        const courses = await response.json();
        const courseSelect = document.getElementById('course');
        
        // Clear existing options except the first one
        courseSelect.innerHTML = '<option value="">Select Course</option>';
        
        courses.forEach(course => {
            const option = document.createElement('option');
            option.value = course.id;
            option.textContent = course.courseName;
            courseSelect.appendChild(option);
        });
    } catch (error) {
        console.error('Error loading courses:', error);
        alert('Error loading courses. Please check if the server is running.');
    }
}

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
            <td>${student.rollNumber}</td>
            <td>${student.name}</td>
            <td>${student.email}</td>
            <td>${student.course}</td>
            <td>${student.dateOfBirth}</td>
            <td>${student.phoneNumber}</td>
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
    
    // Check for duplicate roll number and phone number on client side
    try {
        const response = await fetch(API_URL);
        const existingStudents = await response.json();
        
        const duplicateRollNumber = existingStudents.find(s => s.rollNumber.toLowerCase() === student.rollNumber.toLowerCase());
        if (duplicateRollNumber) {
            alert('Roll number already exists. Please use a different roll number.');
            return;
        }
        
        const duplicateEmail = existingStudents.find(s => s.email.toLowerCase() === student.email.toLowerCase());
        if (duplicateEmail) {
            alert('Email already exists. Please use a different email address.');
            return;
        }
        
        const duplicatePhoneNumber = existingStudents.find(s => s.phoneNumber === student.phoneNumber);
        if (duplicatePhoneNumber) {
            alert('Phone number already exists. Please use a different phone number.');
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
        document.getElementById('rollNumber').value = student.rollNumber;
        document.getElementById('name').value = student.name;
        document.getElementById('email').value = student.email;
        document.getElementById('course').value = student.courseId;
        // Convert dd-mm-yyyy to yyyy-mm-dd for date input
        const [day, month, year] = student.dateOfBirth.split('-');
        document.getElementById('dateOfBirth').value = `${year}-${month}-${day}`;
        document.getElementById('phoneNumber').value = student.phoneNumber;
        
        document.getElementById('form-title').textContent = 'Edit Student';
        document.getElementById('submit-btn').textContent = 'Update Student';
        isEditing = true;
        
        document.getElementById('rollNumber').focus();
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
    
    // Check for duplicate roll number and phone number on client side (excluding current student)
    try {
        const response = await fetch(API_URL);
        const existingStudents = await response.json();
        
        const duplicateRollNumber = existingStudents.find(s => s.id != id && s.rollNumber.toLowerCase() === student.rollNumber.toLowerCase());
        if (duplicateRollNumber) {
            alert('Roll number already exists. Please use a different roll number.');
            return;
        }
        
        const duplicateEmail = existingStudents.find(s => s.id != id && s.email.toLowerCase() === student.email.toLowerCase());
        if (duplicateEmail) {
            alert('Email already exists. Please use a different email address.');
            return;
        }
        
        const duplicatePhoneNumber = existingStudents.find(s => s.id != id && s.phoneNumber === student.phoneNumber);
        if (duplicatePhoneNumber) {
            alert('Phone number already exists. Please use a different phone number.');
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
    const rollNumber = document.getElementById('rollNumber').value.trim();
    const name = document.getElementById('name').value.trim();
    const email = document.getElementById('email').value.trim();
    const courseId = document.getElementById('course').value;
    const dateOfBirth = document.getElementById('dateOfBirth').value.trim();
    const phoneNumber = document.getElementById('phoneNumber').value.trim();
    
    // Roll number validation
    if (!rollNumber || rollNumber.length < 3) {
        alert('Roll number must be at least 3 characters long');
        return false;
    }
    
    // Name validation - no numbers allowed
    if (!name || name.length < 2) {
        alert('Name must be at least 2 characters long');
        return false;
    }
    if (/[0-9]/.test(name)) {
        alert('Name should not contain numbers');
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
    if (!courseId) {
        alert('Please select a course');
        return false;
    }
    
    // Date of birth validation
    if (!dateOfBirth) {
        alert('Date of birth is required');
        return false;
    }
    
    // Check if age is reasonable (between 16 and 30)
    const selectedDate = new Date(dateOfBirth);
    const today = new Date();
    const age = today.getFullYear() - selectedDate.getFullYear();
    const monthDiff = today.getMonth() - selectedDate.getMonth();
    
    if (monthDiff < 0 || (monthDiff === 0 && today.getDate() < selectedDate.getDate())) {
        age--;
    }
    
    if (age < 16 || age > 30) {
        alert('Student age must be between 16 and 30 years');
        return false;
    }
    
    // Phone number validation
    if (!phoneNumber) {
        alert('Phone number is required');
        return false;
    }
    if (!/^[1-9][0-9]{9}$/.test(phoneNumber)) {
        alert('Phone number must be exactly 10 digits and cannot start with 0');
        return false;
    }
    
    return true;
}

function getFormData() {
    return {
        rollNumber: document.getElementById('rollNumber').value.trim(),
        name: document.getElementById('name').value.trim(),
        email: document.getElementById('email').value.trim(),
        courseId: parseInt(document.getElementById('course').value),
        dateOfBirth: document.getElementById('dateOfBirth').value, // yyyy-mm-dd format for database
        phoneNumber: document.getElementById('phoneNumber').value.trim()
    };
}

function resetForm() {
    document.getElementById('student-form').reset();
    document.getElementById('student-id').value = '';
    document.getElementById('form-title').textContent = 'Add New Student';
    document.getElementById('submit-btn').textContent = 'Add Student';
    isEditing = false;
}

function searchStudents() {
    const searchTerm = document.getElementById('search-input').value.toLowerCase();
    const table = document.getElementById('students-table');
    const rows = table.getElementsByTagName('tr');
    
    // Start from index 1 to skip the header row
    for (let i = 1; i < rows.length; i++) {
        const row = rows[i];
        const cells = row.getElementsByTagName('td');
        let found = false;
        
        // Search through all cells in the row
        for (let j = 0; j < cells.length - 1; j++) { // -1 to exclude Actions column
            const cellText = cells[j].textContent.toLowerCase();
            if (cellText.includes(searchTerm)) {
                found = true;
                break;
            }
        }
        
        // Show or hide the row based on search result
        row.style.display = found ? '' : 'none';
    }
}