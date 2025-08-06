import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { toast } from 'react-toastify';
import ManageMemories from './ManageMemories';
import './MyAccount.css';

const MyAccount = ({ user, onBack, onUserUpdate, onLogout, darkMode }) => {
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    currentPassword: '',
    newPassword: '',
    confirmPassword: ''
  });
  const [profileImage, setProfileImage] = useState(null);
  const [loading, setLoading] = useState(false);
  const [showPasswordSection, setShowPasswordSection] = useState(false);
  const [memoryPassword, setMemoryPassword] = useState('');
  const [hasMemoryPassword, setHasMemoryPassword] = useState(false);
  const [showMemoryPasswordModal, setShowMemoryPasswordModal] = useState(false);
  const [showManageMemories, setShowManageMemories] = useState(false);

  useEffect(() => {
    if (user) {
      setFormData({
        fullName: user.fullName || '',
        email: user.email || '',
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      });
      
      checkMemoryPasswordStatus();
    }
  }, [user]);

  const checkMemoryPasswordStatus = async () => {
    try {
      const response = await axios.get('/api/auth/memory-password-status');
      setHasMemoryPassword(response.data.hasPassword);
    } catch (error) {
      console.error('Error checking memory password status:', error);
    }
  };

  const handleInputChange = (e) => {
    const { name, value } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: value
    }));
  };

  const handleImageChange = (e) => {
    const file = e.target.files[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) { 
        toast.error('Image size should be less than 5MB');
        return;
      }
      setProfileImage(file);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);

    try {
      const updateData = new FormData();
      updateData.append('fullName', formData.fullName);
      
      if (formData.newPassword) {
        if (formData.newPassword !== formData.confirmPassword) {
          toast.error('New passwords do not match');
          setLoading(false);
          return;
        }
        if (!formData.currentPassword) {
          toast.error('Current password is required to set a new password');
          setLoading(false);
          return;
        }
        updateData.append('currentPassword', formData.currentPassword);
        updateData.append('newPassword', formData.newPassword);
      }

      if (profileImage) {
        updateData.append('profileImage', profileImage);
      }

      const response = await axios.put('/api/auth/update-profile', updateData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      toast.success('Profile updated successfully!');
      onUserUpdate(response.data);
      setIsEditing(false);
      setFormData(prev => ({
        ...prev,
        currentPassword: '',
        newPassword: '',
        confirmPassword: ''
      }));
      setProfileImage(null);
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to update profile');
    } finally {
      setLoading(false);
    }
  };

  const handleSetMemoryPassword = async () => {
    if (!memoryPassword || memoryPassword.length < 6) {
      toast.error('Memory password must be at least 6 characters long');
      return;
    }

    try {
      await axios.post('/api/auth/set-memory-password', {
        password: memoryPassword
      });
      
      toast.success('Memory password set successfully!');
      setHasMemoryPassword(true);
      setMemoryPassword('');
      setShowMemoryPasswordModal(false);
    } catch (error) {
      toast.error(error.response?.data?.error || 'Failed to set memory password');
    }
  };

  const handleManageMemories = () => {
    if (hasMemoryPassword) {
      setShowMemoryPasswordModal(true);
    } else {
      setShowManageMemories(true);
    }
  };

  const handleMemoryPasswordVerification = async () => {
    try {
      const response = await axios.post('/api/auth/verify-memory-password', {
        password: memoryPassword
      });
      
      if (response.data.valid) {
        setShowMemoryPasswordModal(false);
        setMemoryPassword('');
        setShowManageMemories(true);
      } else {
        toast.error('Invalid memory password');
      }
    } catch (error) {
      toast.error('Invalid memory password');
    }
  };

  if (showManageMemories) {
    return (
      <ManageMemories 
        user={user}
        onBack={() => setShowManageMemories(false)}
        darkMode={darkMode}
      />
    );
  }

  return (
    <div className={`my-account-container ${darkMode ? 'dark-mode' : 'light-mode'}`}>
      <div className="my-account-header">
        <button 
          className="back-button"
          onClick={onBack}
          aria-label="Go back"
        >
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M19 12H5M12 19l-7-7 7-7" />
          </svg>
          Back
        </button>
        <h1>My Account</h1>
        <button 
          className="logout-button-account"
          onClick={onLogout}
          title="Log out"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/>
            <polyline points="16,17 21,12 16,7"/>
            <line x1="21" y1="12" x2="9" y2="12"/>
          </svg>
          Logout
        </button>
      </div>

      <div className="my-account-content">
        <div className="profile-section">
          <div className="profile-image-container">
            <img 
              src={user?.profileImageUrl} 
              alt="Profile" 
              className="profile-image-large"
              onError={(e) => {
                e.target.src = `https:
              }}
            />
            {isEditing && (
              <div className="image-upload-overlay">
                <input
                  type="file"
                  accept="image}
      {showMemoryPasswordModal && (
        <div className="modal-overlay">
          <div className="modal">
            <h3>{hasMemoryPassword ? 'Enter Memory Password' : 'Set Memory Password'}</h3>
            <p>
              {hasMemoryPassword 
                ? 'Please enter your memory password to access memory management.'
                : 'Set a password to secure your memory data. This password will be required to view or edit your memories.'
              }
            </p>
            <input
              type="password"
              value={memoryPassword}
              onChange={(e) => setMemoryPassword(e.target.value)}
              placeholder={hasMemoryPassword ? 'Enter memory password' : 'Create memory password (min 6 characters)'}
              className="modal-input"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  hasMemoryPassword ? handleMemoryPasswordVerification() : handleSetMemoryPassword();
                }
              }}
            />
            <div className="modal-actions">
              <button
                onClick={() => {
                  setShowMemoryPasswordModal(false);
                  setMemoryPassword('');
                }}
                className="modal-cancel-button"
              >
                Cancel
              </button>
              <button
                onClick={hasMemoryPassword ? handleMemoryPasswordVerification : handleSetMemoryPassword}
                className="modal-confirm-button"
                disabled={!memoryPassword}
              >
                {hasMemoryPassword ? 'Verify' : 'Set Password'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default MyAccount;

