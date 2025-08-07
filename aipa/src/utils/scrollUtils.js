// Enhanced scroll utilities for better user experience
let isUserScrolling = false;
let scrollTimeout = null;
let activeScrollAnimations = new Set();

// Track various types of user interaction
const scrollEvents = ['scroll', 'wheel', 'touchmove', 'keydown'];
const wheelEvents = ['wheel', 'mousewheel', 'DOMMouseScroll'];

// Enhanced user scroll detection
const handleUserScroll = (event) => {
  // Only handle trusted user events
  if (event.isTrusted === false) return;
  
  // For keyboard events, only arrow keys, page up/down, etc.
  if (event.type === 'keydown') {
    const scrollKeys = [32, 33, 34, 35, 36, 37, 38, 39, 40];
    if (!scrollKeys.includes(event.keyCode)) return;
  }
  
  // Mark user as actively scrolling
  isUserScrolling = true;
  
  // Cancel ALL active programmatic scrolling immediately
  activeScrollAnimations.forEach(cancelFn => {
    if (typeof cancelFn === 'function') {
      try {
        cancelFn();
      } catch (e) {
        console.warn('Error cancelling scroll animation:', e);
      }
    }
  });
  activeScrollAnimations.clear();
  
  // Clear existing timeout
  if (scrollTimeout) {
    clearTimeout(scrollTimeout);
  }
  
  // Consider user done scrolling after delay
  scrollTimeout = setTimeout(() => {
    isUserScrolling = false;
  }, 2000); // Longer timeout for better detection
};

// Setup global scroll detection
export const setupScrollDetection = () => {
  // Add listeners for all scroll events
  scrollEvents.forEach(eventType => {
    window.addEventListener(eventType, handleUserScroll, { 
      passive: true, 
      capture: true 
    });
  });
  
  wheelEvents.forEach(eventType => {
    window.addEventListener(eventType, handleUserScroll, { 
      passive: true,
      capture: true
    });
  });
  
  return () => {
    scrollEvents.forEach(eventType => {
      window.removeEventListener(eventType, handleUserScroll, { capture: true });
    });
    wheelEvents.forEach(eventType => {
      window.removeEventListener(eventType, handleUserScroll, { capture: true });
    });
    
    if (scrollTimeout) {
      clearTimeout(scrollTimeout);
      scrollTimeout = null;
    }
    activeScrollAnimations.clear();
  };
};

// Enhanced interruptible scroll
export const interruptibleScroll = (element, options = {}) => {
  if (!element) {
    return Promise.resolve();
  }
  
  // If user is scrolling, don't auto-scroll
  if (isUserScrolling) {
    console.log('Scroll cancelled: user is actively scrolling');
    return Promise.resolve();
  }
  
  const {
    delay = 200, // Longer delay when not streaming
    timeout = 5000 // Max animation time
  } = options;
  
  return new Promise((resolve) => {
    let timeoutId;
    let animationId;
    let isAnimating = false;
    
    const cleanup = () => {
      if (timeoutId) clearTimeout(timeoutId);
      if (animationId) cancelAnimationFrame(animationId);
      activeScrollAnimations.delete(cancelFn);
      isAnimating = false;
    };
    
    const cancelFn = () => {
      cleanup();
      resolve();
    };
    
    // Add to active animations
    activeScrollAnimations.add(cancelFn);
    
    timeoutId = setTimeout(() => {
      // Final check before scrolling
      if (isUserScrolling) {
        console.log('Scroll cancelled during delay:', { isUserScrolling });
        cleanup();
        resolve();
        return;
      }
      
      try {
        isAnimating = true;
        
        // Use a much shorter, less intrusive scroll
        const startY = window.pageYOffset;
        const elementRect = element.getBoundingClientRect();
        const targetY = startY + elementRect.top - 20; // Small offset from top
        const distance = targetY - startY;
        
        // Only scroll if there's a significant distance
        if (Math.abs(distance) < 100) {
          cleanup();
          resolve();
          return;
        }
        
        const duration = Math.min(300, Math.abs(distance) * 2); // Faster for shorter distances
        let startTime = null;
        
        const animate = (currentTime) => {
          if (!isAnimating) return;
          
          if (startTime === null) startTime = currentTime;
          
          // Stop immediately if user starts scrolling
          if (isUserScrolling) {
            cleanup();
            resolve();
            return;
          }
          
          const elapsed = currentTime - startTime;
          const progress = Math.min(elapsed / duration, 1);
          
          // Ease out function for smooth deceleration
          const ease = 1 - Math.pow(1 - progress, 2);
          const currentY = startY + (distance * ease);
          
          window.scrollTo(0, currentY);
          
          if (progress < 1) {
            animationId = requestAnimationFrame(animate);
          } else {
            cleanup();
            resolve();
          }
        };
        
        animationId = requestAnimationFrame(animate);
        
        // Safety timeout
        setTimeout(() => {
          if (isAnimating) {
            cleanup();
            resolve();
          }
        }, timeout);
        
      } catch (error) {
        console.error('Scroll animation error:', error);
        cleanup();
        resolve();
      }
    }, delay);
  });
};

// Gentle scroll for when response is complete
export const gentleScrollAfterResponse = (element, options = {}) => {
  // Wait a bit after response completes before attempting scroll
  return new Promise((resolve) => {
    setTimeout(() => {
      if (!isUserScrolling) {
        console.log('Attempting gentle scroll after response...');
        resolve(interruptibleScroll(element, { 
          ...options, 
          delay: 50 // Very short delay since response is done
        }));
      } else {
        console.log('Skipping scroll - user is currently scrolling');
        resolve();
      }
    }, 500); // Wait 500ms after response completes
  });
};

// Simple, reliable scroll that respects user input immediately
export const scrollAfterResponse = (element, options = {}) => {
  console.log('🎯 scrollAfterResponse called', { element: !!element, isUserScrolling });
  
  // Don't scroll if user is actively scrolling
  if (isUserScrolling) {
    console.log('❌ Skipping scroll - user is scrolling');
    return Promise.resolve();
  }
  
  if (!element) {
    console.log('❌ Skipping scroll - no element');
    return Promise.resolve();
  }
  
  console.log('✅ Starting immediate scroll to element...');
  
  const {
    behavior = 'smooth',
    block = 'start',
    inline = 'nearest'
  } = options;
  
  // Create a cancellable scroll
  let scrollCancelled = false;
  
  // Listen for any user scroll input and cancel immediately
  const cancelScroll = () => {
    scrollCancelled = true;
    console.log('🛑 Scroll cancelled by user input');
  };
  
  // Add temporary listeners to cancel scroll
  const scrollEvents = ['wheel', 'touchstart', 'touchmove', 'keydown'];
  scrollEvents.forEach(event => {
    window.addEventListener(event, cancelScroll, { once: true, passive: true });
  });
  
  // Remove listeners after a short time
  setTimeout(() => {
    scrollEvents.forEach(event => {
      window.removeEventListener(event, cancelScroll);
    });
  }, 1000);
  
  try {
    // Immediate scroll - no delay
    if (!scrollCancelled && !isUserScrolling) {
      element.scrollIntoView({ behavior, block, inline });
      console.log('✅ Scroll executed');
    } else {
      console.log('❌ Scroll cancelled before execution');
    }
  } catch (error) {
    console.error('❌ Scroll error:', error);
  }
  
  return Promise.resolve();
};

// Check if user is currently scrolling
export const isUserCurrentlyScrolling = () => isUserScrolling;

// Force cancel all scroll animations
export const cancelAllScrollAnimations = () => {
  activeScrollAnimations.forEach(cancelFn => {
    try {
      cancelFn();
    } catch (e) {
      console.warn('Error cancelling scroll animation:', e);
    }
  });
  activeScrollAnimations.clear();
};

// Legacy function for backwards compatibility
export const cancelActiveScroll = cancelAllScrollAnimations;
