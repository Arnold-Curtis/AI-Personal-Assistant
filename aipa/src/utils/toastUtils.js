import { toast as reactToastify } from 'react-toastify';

// Custom toast wrapper that respects notification settings
const createToast = (type) => (message, options = {}) => {
  const notificationsEnabled = localStorage.getItem('notificationsEnabled') !== 'false';
  
  if (!notificationsEnabled) {
    // If notifications are disabled, log to console instead
    console.log(`${type.toUpperCase()}: ${message}`);
    // Return a dummy toast reference to prevent null errors
    return { 
      id: 'dummy-toast-' + Date.now(),
      dismiss: () => {},
      update: () => {}
    };
  }
  
  // Apply user's duration setting if not explicitly provided
  const duration = options.autoClose !== undefined 
    ? options.autoClose 
    : parseInt(localStorage.getItem('toastDuration')) || 5000;
  
  return reactToastify[type](message, {
    ...options,
    autoClose: duration,
    containerId: 'main-toast-container'
  });
};

// Create custom toast object
export const toast = {
  success: createToast('success'),
  error: createToast('error'),
  info: createToast('info'),
  warning: createToast('warning'),
  warn: createToast('warn'),
  dismiss: () => {
    // Always dismiss toasts regardless of notification settings
    reactToastify.dismiss();
  },
  clear: () => {
    reactToastify.dismiss();
  }
};

export default toast;
