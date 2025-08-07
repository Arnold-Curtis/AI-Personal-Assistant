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
      // Check if user has a memory password set
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
      if (file.size > 5 * 1024 * 1024) { // 5MB limit
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
                e.target.src = `https://ui-avatars.com/api/?name=${user?.email?.charAt(0)?.toUpperCase()}&background=3b82f6&color=fff`;
              }}
            />
            {isEditing && (
              <div className="image-upload-overlay">
                <input
                  type="file"
                  accept="image/*"
                  onChange={handleImageChange}
                  className="image-upload-input"
                  id="profile-image-upload"
                />
                <label htmlFor="profile-image-upload" className="image-upload-label">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                    <rect x="3" y="3" width="18" height="18" rx="2" ry="2"/>
                    <circle cx="8.5" cy="8.5" r="1.5"/>
                    <polyline points="21,15 16,10 5,21"/>
                  </svg>
                  Change Photo
                </label>
              </div>
            )}
          </div>
        </div>

        <form onSubmit={handleSubmit} className="account-form">
          <div className="form-group">
            <label htmlFor="fullName">Full Name</label>
            <input
              type="text"
              id="fullName"
              name="fullName"
              value={formData.fullName}
              onChange={handleInputChange}
              disabled={!isEditing}
              className="form-input"
              placeholder="Enter your full name"
            />
          </div>

          <div className="form-group">
            <label htmlFor="email">Email</label>
            <input
              type="email"
              id="email"
              name="email"
              value={formData.email}
              disabled={true}
              className="form-input disabled"
              title="Email cannot be changed"
            />
            <small className="form-hint">Email cannot be changed for security reasons</small>
          </div>

          {isEditing && (
            <>
              <div className="password-section">
                <button
                  type="button"
                  className="toggle-password-button"
                  onClick={() => setShowPasswordSection(!showPasswordSection)}
                >
                  {showPasswordSection ? 'Hide' : 'Change'} Password
                  <svg 
                    width="16" 
                    height="16" 
                    viewBox="0 0 24 24" 
                    fill="none" 
                    stroke="currentColor" 
                    strokeWidth="2"
                    className={showPasswordSection ? 'rotated' : ''}
                  >
                    <polyline points="6,9 12,15 18,9"></polyline>
                  </svg>
                </button>

                {showPasswordSection && (
                  <div className="password-fields">
                    <div className="form-group">
                      <label htmlFor="currentPassword">Current Password</label>
                      <input
                        type="password"
                        id="currentPassword"
                        name="currentPassword"
                        value={formData.currentPassword}
                        onChange={handleInputChange}
                        className="form-input"
                        placeholder="Enter current password"
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="newPassword">New Password</label>
                      <input
                        type="password"
                        id="newPassword"
                        name="newPassword"
                        value={formData.newPassword}
                        onChange={handleInputChange}
                        className="form-input"
                        placeholder="Enter new password"
                      />
                    </div>

                    <div className="form-group">
                      <label htmlFor="confirmPassword">Confirm New Password</label>
                      <input
                        type="password"
                        id="confirmPassword"
                        name="confirmPassword"
                        value={formData.confirmPassword}
                        onChange={handleInputChange}
                        className="form-input"
                        placeholder="Confirm new password"
                      />
                    </div>
                  </div>
                )}
              </div>
            </>
          )}

          <div className="form-actions">
            {!isEditing ? (
              <button
                type="button"
                onClick={() => setIsEditing(true)}
                className="edit-button"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/>
                  <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/>
                </svg>
                Edit Profile
              </button>
            ) : (
              <div className="edit-actions">
                <button
                  type="button"
                  onClick={() => {
                    setIsEditing(false);
                    setFormData({
                      fullName: user.fullName || '',
                      email: user.email || '',
                      currentPassword: '',
                      newPassword: '',
                      confirmPassword: ''
                    });
                    setProfileImage(null);
                    setShowPasswordSection(false);
                  }}
                  className="cancel-button"
                  disabled={loading}
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="save-button"
                  disabled={loading}
                >
                  {loading ? 'Saving...' : 'Save Changes'}
                </button>
              </div>
            )}
          </div>
        </form>

        <div className="memory-management-section">
          <h2>Memory Management</h2>
          <p>Manage the information AI knows about you</p>
          
          {!hasMemoryPassword && (
            <div className="memory-password-setup">
              <div className="warning-card">
                <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
                  <line x1="12" y1="9" x2="12" y2="13"/>
                  <line x1="12" y1="17" x2="12.01" y2="17"/>
                </svg>
                <div>
                  <h3>Security Recommendation</h3>
                  <p>Set up a password for memory management to protect your sensitive information.</p>
                  <button
                    className="set-password-button"
                    onClick={() => setShowMemoryPasswordModal(true)}
                  >
                    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                      <rect x="3" y="11" width="18" height="11" rx="2" ry="2"/>
                      <circle cx="12" cy="16" r="1"/>
                      <path d="M7 11V7a5 5 0 0 1 10 0v4"/>
                    </svg>
                    Set Up Password
                  </button>
                </div>
              </div>
            </div>
          )}

          <button
            className="manage-memories-button"
            onClick={handleManageMemories}
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <path d="M9 12l2 2 4-4"/>
              <circle cx="12" cy="12" r="10"/>
            </svg>
            Manage Memories
          </button>
        </div>
      </div>

      {/* Memory Password Modal */}
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
