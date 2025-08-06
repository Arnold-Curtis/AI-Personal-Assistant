
let isUserScrolling = false;
let scrollTimeout = null;
let activeScrollAnimations = new Set();


const scrollEvents = ['scroll', 'wheel', 'touchmove', 'keydown'];
const wheelEvents = ['wheel', 'mousewheel', 'DOMMouseScroll'];


const handleUserScroll = (event) => {
  
  if (event.isTrusted === false) return;
  
  
  if (event.type === 'keydown') {
    const scrollKeys = [32, 33, 34, 35, 36, 37, 38, 39, 40];
    if (!scrollKeys.includes(event.keyCode)) return;
  }
  
  
  isUserScrolling = true;
  
  
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
  
  
  if (scrollTimeout) {
    clearTimeout(scrollTimeout);
  }
  
  
  scrollTimeout = setTimeout(() => {
    isUserScrolling = false;
  }, 2000); 
};


export const setupScrollDetection = () => {
  
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


export const interruptibleScroll = (element, options = {}) => {
  if (!element) {
    return Promise.resolve();
  }
  
  
  if (isUserScrolling) {
    console.log('Scroll cancelled: user is actively scrolling');
    return Promise.resolve();
  }
  
  const {
    delay = 200, 
    timeout = 5000 
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
    
    
    activeScrollAnimations.add(cancelFn);
    
    timeoutId = setTimeout(() => {
      
      if (isUserScrolling) {
        console.log('Scroll cancelled during delay:', { isUserScrolling });
        cleanup();
        resolve();
        return;
      }
      
      try {
        isAnimating = true;
        
        
        const startY = window.pageYOffset;
        const elementRect = element.getBoundingClientRect();
        const targetY = startY + elementRect.top - 20; 
        const distance = targetY - startY;
        
        
        if (Math.abs(distance) < 100) {
          cleanup();
          resolve();
          return;
        }
        
        const duration = Math.min(300, Math.abs(distance) * 2); 
        let startTime = null;
        
        const animate = (currentTime) => {
          if (!isAnimating) return;
          
          if (startTime === null) startTime = currentTime;
          
          
          if (isUserScrolling) {
            cleanup();
            resolve();
            return;
          }
          
          const elapsed = currentTime - startTime;
          const progress = Math.min(elapsed / duration, 1);
          
          
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


export const gentleScrollAfterResponse = (element, options = {}) => {
  
  return new Promise((resolve) => {
    setTimeout(() => {
      if (!isUserScrolling) {
        console.log('Attempting gentle scroll after response...');
        resolve(interruptibleScroll(element, { 
          ...options, 
          delay: 50 
        }));
      } else {
        console.log('Skipping scroll - user is currently scrolling');
        resolve();
      }
    }, 500); 
  });
};


export const scrollAfterResponse = (element, options = {}) => {
  console.log('🎯 scrollAfterResponse called', { element: !!element, isUserScrolling });
  
  
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
  
  
  let scrollCancelled = false;
  
  
  const cancelScroll = () => {
    scrollCancelled = true;
    console.log('🛑 Scroll cancelled by user input');
  };
  
  
  const scrollEvents = ['wheel', 'touchstart', 'touchmove', 'keydown'];
  scrollEvents.forEach(event => {
    window.addEventListener(event, cancelScroll, { once: true, passive: true });
  });
  
  
  setTimeout(() => {
    scrollEvents.forEach(event => {
      window.removeEventListener(event, cancelScroll);
    });
  }, 1000);
  
  try {
    
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


export const isUserCurrentlyScrolling = () => isUserScrolling;


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


export const cancelActiveScroll = cancelAllScrollAnimations;

