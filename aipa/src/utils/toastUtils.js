import { toast as reactToastify } from 'react-toastify';


const createToast = (type) => (message, options = {}) => {
  const notificationsEnabled = localStorage.getItem('notificationsEnabled') !== 'false';
  
  if (!notificationsEnabled) {
    
    console.log(`${type.toUpperCase()}: ${message}`);
    
    return { 
      id: 'dummy-toast-' + Date.now(),
      dismiss: () => {},
      update: () => {}
    };
  }
  
  
  const duration = options.autoClose !== undefined 
    ? options.autoClose 
    : parseInt(localStorage.getItem('toastDuration')) || 5000;
  
  return reactToastify[type](message, {
    ...options,
    autoClose: duration,
    containerId: 'main-toast-container'
  });
};


export const toast = {
  success: createToast('success'),
  error: createToast('error'),
  info: createToast('info'),
  warning: createToast('warning'),
  warn: createToast('warn'),
  dismiss: () => {
    
    reactToastify.dismiss();
  },
  clear: () => {
    reactToastify.dismiss();
  }
};

export default toast;

