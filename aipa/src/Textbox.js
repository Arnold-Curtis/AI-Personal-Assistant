import React, { useState, useRef, useEffect } from 'react';
import axios from 'axios';
import { toast } from 'react-toastify';

export const Textbox = ({ onCalendarEventDetected }) => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const [hasCalendarEvent, setHasCalendarEvent] = useState(false);
    const abortControllerRef = useRef(null);
    const intervalRef = useRef(null);
    const responseBuilder = useRef('');
    const responseEndRef = useRef(null);
    const responseContainerRef = useRef(null);

    // Cleanup intervals on unmount
    useEffect(() => {
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, []);

    // Auto-scroll to bottom only if user hasn't manually scrolled up
    useEffect(() => {
        if (responseEndRef.current && responseContainerRef.current) {
            const container = responseContainerRef.current;
            const isNearBottom = container.scrollHeight - container.scrollTop <= container.clientHeight + 100;
            
            if (isNearBottom || loading) {
                responseEndRef.current.scrollIntoView({ 
                    behavior: 'auto',
                    block: 'nearest'
                });
            }
        }
    }, [response, loading]);

    const extractCalendarEvents = (text) => {
        const calendarRegex = /Calendar: (\d+) days (?:from today|a) (.+?)\.!\./g;
        const events = [];
        let match;
        
        while ((match = calendarRegex.exec(text)) !== null) {
            const days = parseInt(match[1]);
            const title = match[2].trim();
            const startDate = new Date();
            startDate.setDate(startDate.getDate() + days);
            
            events.push({
                title,
                start: startDate.toISOString().split('T')[0]
            });
        }
        
        setHasCalendarEvent(events.length > 0);
        return events;
    };

    const extractResponse = (responseData) => {
        try {
            const responseText = typeof responseData === 'string' 
                ? responseData 
                : JSON.stringify(responseData, null, 2);

            const calendarEvents = extractCalendarEvents(responseText);
            if (calendarEvents.length > 0 && onCalendarEventDetected) {
                onCalendarEventDetected(calendarEvents);
            }

            const part2Match = responseText.match(/\*\*Part 2: Response\*\*([\s\S]*?)(?:\*\*Part 3:|$)/i);
            return part2Match && part2Match[1] 
                ? cleanResponseText(part2Match[1]) 
                : cleanResponseText(responseText);

        } catch (e) {
            console.error("Response parsing error:", e);
            return "An error occurred while processing the response";
        }
    };

    const cleanResponseText = (text) => {
        return text
            .replace(/\*\*Part \d+:.*?\*\*/g, '')
            .replace(/\)\*!/g, '')
            .replace(/[\*■#!→\-]/g, '')
            .replace(/\\n/g, '\n')
            .replace(/\\"/g, '"')
            .replace(/\\u[\dA-F]{4}/gi, m => 
                String.fromCharCode(parseInt(m.replace(/\\u/g, ''), 16)))
            .split('\n')
            .map(line => line.trim())
            .filter(line => line && !line.match(/^[0-9.]+/) && !line.startsWith('-'))
            .join('\n')
            .trim();
    };

    const simulateStreaming = (text) => {
        responseBuilder.current = '';
        setResponse('');
        
        if (intervalRef.current) clearInterval(intervalRef.current);
        
        let index = 0;
        intervalRef.current = setInterval(() => {
            if (index < text.length) {
                responseBuilder.current = text.substring(0, index + 1);
                setResponse(responseBuilder.current);
                index++;
            } else {
                clearInterval(intervalRef.current);
            }
        }, 20);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (abortControllerRef.current) abortControllerRef.current.abort();
        
        const controller = new AbortController();
        abortControllerRef.current = controller;
        setLoading(true);
        setResponse('');
        setHasCalendarEvent(false);

        try {
            const response = await fetch('http://localhost:8080/api/generate', {
                method: 'POST',
                headers: { 
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify({ prompt: input }),
                signal: controller.signal
            });

            const responseText = await response.text();

            if (!response.ok) {
                throw new Error(
                    responseText.startsWith('{') 
                        ? JSON.parse(responseText).error 
                        : `Server error: ${response.status}`
                );
            }

            const processed = extractResponse(responseText);
            simulateStreaming(processed);

        } catch (error) {
            if (error.name === 'AbortError') {
                setResponse("Request cancelled by user");
            } else {
                console.error('API Error:', error);
                setResponse(`ERROR: ${error.message}`);
                toast.error(`Error: ${error.message}`, {
                    position: 'bottom-right',
                    autoClose: 5000
                });
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null;
        }
    };

    const handleReset = () => {
        if (abortControllerRef.current) abortControllerRef.current.abort();
        if (intervalRef.current) clearInterval(intervalRef.current);
        setInput('');
        setResponse('');
        setHasCalendarEvent(false);
        responseBuilder.current = '';
    };

    return (
        <div style={{ 
            padding: '20px', 
            margin: '0 auto',
            fontFamily: 'Arial, sans-serif'
        }}>
            <form onSubmit={handleSubmit} style={{ marginBottom: '20px' }}>
                <textarea 
                    value={input} 
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="What's on your mind? I'm here to help..."
                    style={{ 
                        width: '100%',
                        padding: '12px',
                        fontSize: '16px',
                        marginBottom: '15px',
                        border: '1px solid #ddd',
                        borderRadius: '4px',
                        minHeight: '120px',
                        resize: 'vertical',
                        fontFamily: 'inherit'
                    }}
                    disabled={loading}
                />
                <div style={{ display: 'flex', gap: '10px' }}>
                    <button 
                        type="submit" 
                        disabled={loading}
                        style={{
                            padding: '12px 24px',
                            background: loading ? '#6c757d' : '#007bff',
                            color: 'white',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '16px',
                            transition: 'background 0.3s ease',
                            flex: 1,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center'
                        }}
                    >
                        {loading ? (
                            <>
                                <span 
                                    style={{
                                        display: 'inline-block',
                                        width: '18px',
                                        height: '18px',
                                        border: '2px solid rgba(255,255,255,0.3)',
                                        borderRadius: '50%',
                                        borderTopColor: 'white',
                                        animation: 'spin 1s linear infinite',
                                        marginRight: '10px'
                                    }}
                                />
                                Processing...
                            </>
                        ) : "Get Help"}
                    </button>
                    <button
                        type="button"
                        onClick={handleReset}
                        style={{
                            padding: '12px 24px',
                            background: '#dc3545',
                            color: 'white',
                            border: 'none',
                            borderRadius: '4px',
                            cursor: 'pointer',
                            fontSize: '16px'
                        }}
                    >
                        Reset
                    </button>
                </div>
            </form>
            
            <div 
                ref={responseContainerRef}
                style={{
                    padding: '20px',
                    background: '#f8f9fa',
                    borderRadius: '8px',
                    minHeight: '200px',
                    maxHeight: '60vh',
                    overflowY: 'auto',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                    border: '1px solid #dee2e6',
                    textAlign: 'right',
                    direction: 'rtl'
                }}
            >
                <div style={{ 
                    whiteSpace: 'pre-wrap',
                    lineHeight: '1.6',
                    fontSize: '16px',
                    color: '#212529',
                    fontFamily: 'monospace',
                    direction: 'ltr',
                    unicodeBidi: 'bidi-override',
                    textAlign: 'left'
                }}>
                    {response || (loading ? "Analyzing your request and crafting response..." : "Your response will appear here...")}
                    <div ref={responseEndRef} />
                </div>
            </div>
            
            <style>{`
                @keyframes spin {
                    0% { transform: rotate(0deg); }
                    100% { transform: rotate(360deg); }
                }
            `}</style>
        </div>
    );
};