import React, { useState, useRef } from 'react';

export const Textbox = () => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const abortControllerRef = useRef(null);

    const extractResponse = (responseData) => {
        try {
            // Handle both string and object responses
            const responseText = typeof responseData === 'string' ? responseData : JSON.stringify(responseData);
            
            // First try to parse as JSON
            try {
                const jsonResponse = JSON.parse(responseText);
                
                // Handle backend error format
                if (jsonResponse.error) {
                    return `ERROR: ${jsonResponse.error}`;
                }
                
                // Handle success response format
                if (jsonResponse.text) {
                    return jsonResponse.text;
                }
                
                // Handle Gemini API success response
                if (jsonResponse.candidates?.[0]?.content?.parts) {
                    return jsonResponse.candidates[0].content.parts[0].text;
                }
                
                // Handle raw JSON responses
                return JSON.stringify(jsonResponse, null, 2);
            } catch (e) {
                // Not JSON, proceed with text processing
            }

            // Handle the new two-part response format
            if (responseText.includes("Thinking Space:") && responseText.includes(").*")) {
                const thinkingSpace = responseText.split("Thinking Space:")[1].split(").*")[0].trim();
                const decisionMatch = responseText.match(/\)\.\*\s*(YES|NO)/i);
                const decision = decisionMatch ? decisionMatch[1] : "UNDECIDED";
                
                return `THINKING PROCESS:\n${thinkingSpace}\n\nFINAL DECISION: ${decision}`;
            }

            // Clean up other responses
            return responseText
                .replace(/\\n/g, '\n')
                .replace(/\\"/g, '"')
                .replace(/\\u[\dA-F]{4}/gi, match => 
                    String.fromCharCode(parseInt(match.replace(/\\u/g, ''), 16))
                );

        } catch (e) {
            console.error("Response parsing error:", e);
            return "An error occurred while processing the response";
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (abortControllerRef.current) {
            abortControllerRef.current.abort();
        }
        
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

            // Clone the response to prevent "body already read" errors
            const responseClone = response.clone();
            const responseText = await response.text();

            if (!response.ok) {
                throw new Error(
                    responseText.startsWith('{') 
                        ? JSON.parse(responseText).error 
                        : `Server error: ${response.status}`
                );
            }

            setResponse(extractResponse(responseText));

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
                        onClick={() => {
                            if (abortControllerRef.current) {
                                abortControllerRef.current.abort();
                            }
                            setInput('');
                            setResponse('');
                        }}
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