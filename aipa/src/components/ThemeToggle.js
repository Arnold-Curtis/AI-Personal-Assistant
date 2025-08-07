import React from 'react';

const ThemeToggle = ({ isDarkMode, toggleTheme }) => {
  return (
    <div className="theme-toggle">
      <button 
        onClick={toggleTheme} 
        aria-label={isDarkMode ? "Switch to light mode" : "Switch to dark mode"}
        title={isDarkMode ? "Switch to light mode" : "Switch to dark mode"}
        className="theme-toggle-button"
      >
        {isDarkMode ? '☀️' : '🌙'}
      </button>
    </div>
  );
};

export default ThemeToggle;
