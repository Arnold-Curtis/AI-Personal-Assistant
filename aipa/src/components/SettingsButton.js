import React from 'react';

const SettingsButton = ({ onOpenSettings }) => {
  return (
    <div className="settings-gear">
      <button 
        onClick={onOpenSettings} 
        aria-label="Open Settings"
        title="Open Settings"
        className="settings-gear-button"
      >
        ⚙️
      </button>
    </div>
  );
};

export default SettingsButton;

