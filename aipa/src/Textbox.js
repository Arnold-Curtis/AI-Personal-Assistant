import React, { useState, useRef, useEffect } from 'react';

export const Textbox = () => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const abortControllerRef = useRef(null);
    const intervalRef = useRef(null);
    const responseBuilder = useRef('');

    // Cleanup intervals on unmount
    useEffect(() => {
        return () => {
            if (intervalRef.current) clearInterval(intervalRef.current);
        };
    }, []);

    const extractResponse = (responseData) => {
        try {
            const responseText = typeof responseData === 'string' 
                ? responseData 
                : JSON.stringify(responseData, null, 2);

            // Improved regex to better handle response parts
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
                String.fromCharCode(parseInt(m.replace(/\\u/g, ''), 16))
            )
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
        
        // Start with first character immediately
        if (text.length > 0) {
            responseBuilder.current = text.charAt(0);
            setResponse(responseBuilder.current);
        }
        
        // Stream remaining characters
        let index = 1;
        intervalRef.current = setInterval(() => {
            if (index < text.length) {
                responseBuilder.current += text.charAt(index);
                setResponse(responseBuilder.current);
                index++;
            } else {
                clearInterval(intervalRef.current);
            }
        }, 20); // Adjust speed (20ms per character)
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (abortControllerRef.current) abortControllerRef.current.abort();
        
        const controller = new AbortController();
        abortControllerRef.current = controller;
        setLoading(true);
        setResponse('');

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
        responseBuilder.current = '';
    };

    return (
        <div style={{ 
            padding: '20px', 
            maxWidth: '800px', 
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
            
            <div style={{
                padding: '20px',
                background: '#f8f9fa',
                borderRadius: '8px',
                minHeight: '200px',
                maxHeight: '60vh',
                overflowY: 'auto',
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                border: '1px solid #dee2e6'
            }}>
                <div style={{ 
                    whiteSpace: 'pre-wrap',
                    lineHeight: '1.6',
                    fontSize: '16px',
                    color: '#212529',
                    fontFamily: 'monospace'
                }}>
                    {response || (loading ? "Analyzing your request and crafting response..." : "Your response will appear here...")}
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