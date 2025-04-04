import React, { useState, useRef } from 'react';
import axios from 'axios';
import { toast } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';
import './App.css';

export const Login = ({ onLogin }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [fullName, setFullName] = useState('');
    const [profileImage, setProfileImage] = useState(null);
    const [isSignup, setIsSignup] = useState(false);
    const [isLoading, setIsLoading] = useState(false);
    const fileInputRef = useRef(null);

    const handleFileChange = (e) => {
        const file = e.target.files[0];
        if (file) {
            // Validate file type and size
            if (!file.type.match('image.*')) {
                toast.error('Please select an image file');
                return;
            }
            if (file.size > 2 * 1024 * 1024) { // 2MB limit
                toast.error('Image size should be less than 2MB');
                return;
            }
            setProfileImage(file);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsLoading(true);

        try {
            let profileImageName = null;
            
            // Handle file upload if in signup mode and image selected
            if (isSignup && profileImage) {
                const formData = new FormData();
                formData.append('file', profileImage);
                
                const uploadResponse = await axios.post('/api/upload', formData, {
                    headers: {
                        'Content-Type': 'multipart/form-data'
                    }
                });
                profileImageName = uploadResponse.data.filename;
            }

            // Prepare authentication payload
            const payload = {
                email,
                password,
                ...(isSignup && { fullName }),
                ...(isSignup && profileImageName && { profileImage: profileImageName })
            };

            const endpoint = isSignup ? '/api/auth/signup' : '/api/auth/login';
            const response = await axios.post(endpoint, payload);
            
            onLogin(response.data);
            toast.success(isSignup ? 'Account created successfully!' : 'Login successful');
            
        } catch (error) {
            const errorMessage = error.response?.data?.error || 
                               error.response?.data?.message || 
                               'Authentication failed';
            toast.error(errorMessage);
        } finally {
            setIsLoading(false);
        }
    };

    const resetForm = () => {
        setEmail('');
        setPassword('');
        setFullName('');
        setProfileImage(null);
        if (fileInputRef.current) {
            fileInputRef.current.value = '';
        }
    };

    const toggleAuthMode = () => {
        setIsSignup(!isSignup);
        resetForm();
    };

    return (
        <div className="auth-container">
            <div className="auth-card">
                <h2>{isSignup ? 'Create Account' : 'Welcome Back'}</h2>
                <p className="auth-subtitle">
                    {isSignup ? 'Join us to get started' : 'Sign in to continue'}
                </p>

                <form onSubmit={handleSubmit}>
                    <div className="form-group">
                        <label htmlFor="email">Email Address</label>
                        <input
                            id="email"
                            type="email"
                            placeholder="Enter your email"
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="password">Password</label>
                        <input
                            id="password"
                            type="password"
                            placeholder="Enter your password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                            minLength="6"
                        />
                    </div>

                    {isSignup && (
                        <>
                            <div className="form-group">
                                <label htmlFor="fullName">Full Name (Optional)</label>
                                <input
                                    id="fullName"
                                    type="text"
                                    placeholder="Enter your full name"
                                    value={fullName}
                                    onChange={(e) => setFullName(e.target.value)}
                                />
                            </div>

                            <div className="form-group">
                                <label htmlFor="profileImage">Profile Photo (Optional)</label>
                                <input
                                    id="profileImage"
                                    type="file"
                                    accept="image/*"
                                    ref={fileInputRef}
                                    onChange={handleFileChange}
                                    style={{ display: 'none' }}
                                />
                                <button
                                    type="button"
                                    className="file-upload-button"
                                    onClick={() => fileInputRef.current.click()}
                                >
                                    {profileImage ? 'Change Photo' : 'Choose Photo'}
                                </button>
                                {profileImage && (
                                    <div className="image-preview">
                                        <img 
                                            src={URL.createObjectURL(profileImage)} 
                                            alt="Preview" 
                                            onLoad={() => URL.revokeObjectURL(profileImage)}
                                        />
                                        <button
                                            type="button"
                                            className="remove-image-button"
                                            onClick={() => {
                                                setProfileImage(null);
                                                fileInputRef.current.value = '';
                                            }}
                                        >
                                            Remove
                                        </button>
                                    </div>
                                )}
                            </div>
                        </>
                    )}

                    <button 
                        type="submit" 
                        className="auth-button"
                        disabled={isLoading}
                    >
                        {isLoading ? (
                            <>
                                <span className="spinner"></span>
                                {isSignup ? 'Creating Account...' : 'Logging In...'}
                            </>
                        ) : (
                            isSignup ? 'Sign Up' : 'Login'
                        )}
                    </button>

                    <div className="auth-footer">
                        <p>
                            {isSignup ? 'Already have an account?' : "Don't have an account?"}
                            <button 
                                type="button" 
                                className="toggle-mode-button"
                                onClick={toggleAuthMode}
                            >
                                {isSignup ? 'Login' : 'Sign Up'}
                            </button>
                        </p>
                    </div>
                </form>
            </div>
        </div>
    );
};