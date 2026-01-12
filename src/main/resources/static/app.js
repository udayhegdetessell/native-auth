// API Base URL
const API_BASE = '';

// State
let currentUser = null;
let signupContinuationToken = null;
let resetContinuationToken = null;
let lookupUserEmail = null;

// Initialize
document.addEventListener('DOMContentLoaded', () => {
    initTabs();
    checkSession();
});

// Tab Navigation
function initTabs() {
    const tabs = document.querySelectorAll('.tab');
    tabs.forEach(tab => {
        tab.addEventListener('click', () => {
            const tabName = tab.dataset.tab;
            switchTab(tabName);
        });
    });
}

function switchTab(tabName) {
    // Update tab buttons
    document.querySelectorAll('.tab').forEach(t => t.classList.remove('active'));
    document.querySelector(`[data-tab="${tabName}"]`).classList.add('active');
    
    // Update panels
    document.querySelectorAll('.panel').forEach(p => p.classList.remove('active'));
    document.getElementById(`${tabName}-panel`).classList.add('active');
}

// Session Management
function checkSession() {
    const token = localStorage.getItem('accessToken');
    const email = localStorage.getItem('userEmail');
    
    if (token && email) {
        currentUser = { email, token };
        showUserInfo(email);
    }
}

function showUserInfo(email) {
    document.getElementById('userInfo').style.display = 'flex';
    document.getElementById('userEmail').textContent = email;
}

function logout() {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('userEmail');
    currentUser = null;
    document.getElementById('userInfo').style.display = 'none';
    showToast('Logged out successfully', 'success');
}

// Toast Notifications
function showToast(message, type = 'info') {
    const toast = document.getElementById('toast');
    toast.textContent = message;
    toast.className = `toast ${type} show`;
    
    setTimeout(() => {
        toast.classList.remove('show');
    }, 4000);
}

// Loading State
function setLoading(form, loading) {
    const btn = form.querySelector('button[type="submit"]');
    const span = btn.querySelector('span');
    const spinner = btn.querySelector('.spinner');
    
    if (loading) {
        span.style.display = 'none';
        spinner.style.display = 'block';
        btn.disabled = true;
    } else {
        span.style.display = 'inline';
        spinner.style.display = 'none';
        btn.disabled = false;
    }
}

// API Calls
async function apiCall(endpoint, method = 'GET', body = null) {
    const options = {
        method,
        headers: {
            'Content-Type': 'application/json',
        },
    };
    
    if (body) {
        options.body = JSON.stringify(body);
    }
    
    const response = await fetch(`${API_BASE}${endpoint}`, options);
    return response.json();
}

// Login Handler
async function handleLogin(event) {
    event.preventDefault();
    const form = event.target;

    const email = document.getElementById('loginEmail').value;
    const password = document.getElementById('loginPassword').value;

    setLoading(form, true);

    try {
        const result = await apiCall('/api/v1/hybrid-auth/signin', 'POST', { email, password });

        if (result.success) {
            localStorage.setItem('accessToken', result.data.access_token);
            localStorage.setItem('userEmail', email);
            currentUser = { email, token: result.data.access_token };
            showUserInfo(email);
            showToast('✅ Login successful!', 'success');
            form.reset();
        } else {
            showToast(result.message || 'Login failed', 'error');
        }
    } catch (error) {
        showToast('Login failed: ' + error.message, 'error');
    } finally {
        setLoading(form, false);
    }
}

// Sign Up Handlers
async function handleSignupStart(event) {
    event.preventDefault();
    const form = event.target;

    const email = document.getElementById('signupEmail').value;
    const displayName = document.getElementById('signupDisplayName').value;
    const password = document.getElementById('signupPassword').value;

    setLoading(form, true);

    try {
        const params = new URLSearchParams({
            email,
            password
        });

        const result = await apiCall(`/api/v1/hybrid-auth/signup/start?${params}`, 'POST');

        if (result.success && result.data) {
            signupContinuationToken = result.data.continuationToken;
            // Store displayName for later
            localStorage.setItem('signupDisplayName', displayName);
            document.getElementById('signupStep1').style.display = 'none';
            document.getElementById('signupStep2').style.display = 'block';
            showToast('📧 Verification code sent to your email!', 'success');
        } else {
            showToast(result.message || 'Sign up failed', 'error');
        }
    } catch (error) {
        showToast('Sign up failed: ' + error.message, 'error');
    } finally {
        setLoading(form, false);
    }
}

async function handleSignupComplete(event) {
    event.preventDefault();
    const form = event.target;

    const otp = document.getElementById('signupOtp').value;
    const displayName = localStorage.getItem('signupDisplayName') || 'User';

    setLoading(form, true);

    try {
        const params = new URLSearchParams({
            continuationToken: signupContinuationToken,
            otp,
            displayName
        });

        const result = await apiCall(`/api/v1/hybrid-auth/signup/complete?${params}`, 'POST');

        if (result.success) {
            showToast('🎉 Account created successfully!', 'success');
            // Reset form
            resetSignupForm();
            // Switch to login
            switchTab('login');
        } else {
            showToast(result.message || 'Sign up failed', 'error');
        }
    } catch (error) {
        showToast('Sign up failed: ' + error.message, 'error');
    } finally {
        setLoading(form, false);
    }
}

function resetSignupForm() {
    document.getElementById('signupStep1').style.display = 'block';
    document.getElementById('signupStep2').style.display = 'none';
    document.getElementById('signupStartForm').reset();
    document.getElementById('signupCompleteForm').reset();
    signupContinuationToken = null;
    localStorage.removeItem('signupDisplayName');
}

// Password Reset Handlers
async function handleResetStart(event) {
    event.preventDefault();
    const form = event.target;
    
    const email = document.getElementById('resetEmail').value;
    
    setLoading(form, true);
    
    try {
        const result = await apiCall(`/api/auth/password-reset/start?email=${encodeURIComponent(email)}`, 'POST');
        
        if (result.success && result.data) {
            resetContinuationToken = result.data.continuationToken;
            document.getElementById('resetStep1').style.display = 'none';
            document.getElementById('resetStep2').style.display = 'block';
            showToast('Reset code sent to your email', 'success');
        } else {
            showToast(result.message || 'Password reset failed', 'error');
        }
    } catch (error) {
        showToast('Password reset failed: ' + error.message, 'error');
    } finally {
        setLoading(form, false);
    }
}

async function handleResetComplete(event) {
    event.preventDefault();
    const form = event.target;
    
    const otp = document.getElementById('resetOtp').value;
    const newPassword = document.getElementById('newPassword').value;
    
    setLoading(form, true);
    
    try {
        const params = new URLSearchParams({
            continuationToken: resetContinuationToken,
            otp,
            newPassword
        });
        
        const result = await apiCall(`/api/auth/password-reset/complete?${params}`, 'POST');
        
        if (result.success) {
            showToast('Password reset successful!', 'success');
            // Reset form
            document.getElementById('resetStep1').style.display = 'block';
            document.getElementById('resetStep2').style.display = 'none';
            document.getElementById('resetStartForm').reset();
            document.getElementById('resetCompleteForm').reset();
            resetContinuationToken = null;
            // Switch to login
            switchTab('login');
        } else {
            showToast(result.message || 'Password reset failed', 'error');
        }
    } catch (error) {
        showToast('Password reset failed: ' + error.message, 'error');
    } finally {
        setLoading(form, false);
    }
}

// User Management Handlers
async function handleCreateUser(event) {
    event.preventDefault();
    const form = event.target;
    
    const email = document.getElementById('newUserEmail').value;
    const displayName = document.getElementById('newUserName').value;
    const password = document.getElementById('newUserPassword').value;
    
    setLoading(form, true);
    
    try {
        const result = await apiCall('/api/users', 'POST', { email, displayName, password });
        
        if (result.success) {
            showToast('User created successfully!', 'success');
            form.reset();
        } else {
            showToast(result.message || 'Failed to create user', 'error');
        }
    } catch (error) {
        showToast('Failed to create user: ' + error.message, 'error');
    } finally {
        setLoading(form, false);
    }
}

async function handleLookupUser(event) {
    event.preventDefault();
    
    const email = document.getElementById('lookupEmail').value;
    lookupUserEmail = email;
    
    try {
        const result = await apiCall(`/api/users/${encodeURIComponent(email)}`);
        
        if (result.success && result.data) {
            const user = result.data;
            displayUserDetails(user);
        } else {
            document.getElementById('userDetails').style.display = 'none';
            showToast('User not found', 'error');
        }
    } catch (error) {
        document.getElementById('userDetails').style.display = 'none';
        showToast('Failed to lookup user: ' + error.message, 'error');
    }
}

function displayUserDetails(user) {
    const displayName = user.displayName || 'Unknown';
    const email = user.userPrincipalName || lookupUserEmail;
    const isEnabled = user.accountEnabled !== false;
    
    // Set avatar initials
    const initials = displayName.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2);
    document.getElementById('userAvatar').textContent = initials;
    
    // Set user info
    document.getElementById('userName').textContent = displayName;
    document.getElementById('userEmailDisplay').textContent = email;
    
    // Set status
    const statusBadge = document.getElementById('userStatus');
    statusBadge.textContent = isEnabled ? 'Active' : 'Disabled';
    statusBadge.className = `status-badge ${isEnabled ? 'active' : 'disabled'}`;
    
    // Show details
    document.getElementById('userDetails').style.display = 'block';
}

async function handleDisableUser() {
    if (!lookupUserEmail) return;
    
    try {
        const result = await apiCall(`/api/users/${encodeURIComponent(lookupUserEmail)}/disable`, 'PATCH');
        
        if (result.success) {
            showToast('User disabled successfully', 'success');
            // Refresh user details
            document.getElementById('lookupUserForm').dispatchEvent(new Event('submit'));
        } else {
            showToast(result.message || 'Failed to disable user', 'error');
        }
    } catch (error) {
        showToast('Failed to disable user: ' + error.message, 'error');
    }
}

async function handleEnableUser() {
    if (!lookupUserEmail) return;
    
    try {
        const result = await apiCall(`/api/users/${encodeURIComponent(lookupUserEmail)}/enable`, 'PATCH');
        
        if (result.success) {
            showToast('User enabled successfully', 'success');
            // Refresh user details
            document.getElementById('lookupUserForm').dispatchEvent(new Event('submit'));
        } else {
            showToast(result.message || 'Failed to enable user', 'error');
        }
    } catch (error) {
        showToast('Failed to enable user: ' + error.message, 'error');
    }
}

async function handleForceReset() {
    if (!lookupUserEmail) return;
    
    try {
        const result = await apiCall(`/api/users/${encodeURIComponent(lookupUserEmail)}/force-password-reset`, 'POST');
        
        if (result.success) {
            showToast('Force password reset set successfully', 'success');
        } else {
            showToast(result.message || 'Failed to set force password reset', 'error');
        }
    } catch (error) {
        showToast('Failed to set force password reset: ' + error.message, 'error');
    }
}

async function handleDeleteUser() {
    if (!lookupUserEmail) return;
    
    if (!confirm(`Are you sure you want to delete user ${lookupUserEmail}? This action cannot be undone.`)) {
        return;
    }
    
    try {
        const result = await apiCall(`/api/users/${encodeURIComponent(lookupUserEmail)}`, 'DELETE');
        
        if (result.success) {
            showToast('User deleted successfully', 'success');
            document.getElementById('userDetails').style.display = 'none';
            document.getElementById('lookupEmail').value = '';
            lookupUserEmail = null;
        } else {
            showToast(result.message || 'Failed to delete user', 'error');
        }
    } catch (error) {
        showToast('Failed to delete user: ' + error.message, 'error');
    }
}
