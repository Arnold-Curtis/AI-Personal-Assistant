import React, { useState, useRef } from 'react';

export const Textbox = () => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const abortControllerRef = useRef(null);

    const extractResponse = (fullResponse) => {
        try {
            const cleaned = fullResponse
                .replace(/\\n/g, '\n')
                .replace(/\\"/g, '"')
                .replace(/\\u[\dA-F]{4}/gi, m => 
                    String.fromCharCode(parseInt(m.replace(/\\u/g, ''), 16))
                ); // <-- Fixed closing parenthesis

            // Enhanced parsing logic with multiple delimiters
            const decisionSplit = cleaned.split(/■■■\s*(RESPONSE|Decision:|USER OUTPUT)/i);
            if (decisionSplit.length > 1) {
                return decisionSplit.pop()
                    .replace(/(\)\*!|■■■).*/gs, '')
                    .trim();
            }

            // Fallback to return full cleaned response if no delimiters found
            return cleaned;

        } catch (e) {
            console.error("Parsing error:", e);
            return fullResponse;
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
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: input }),
                signal: controller.signal
            });

            const reader = response.body.getReader();
            const decoder = new TextDecoder();
            let fullResponse = '';

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                fullResponse += decoder.decode(value, { stream: true });
            }

            setResponse(extractResponse(fullResponse));
        } catch (error) {
            if (error.name === 'AbortError') {
                setResponse("Request cancelled");
            } else {
                console.error('API Error:', error);
                setResponse("Error: " + error.message);
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null;
        }
    };

    return (
        <div style={{ padding: '20px', maxWidth: '800px', margin: '0 auto' }}>
            <form onSubmit={handleSubmit} style={{ marginBottom: '20px' }}>
                <input 
                    type="text" 
                    value={input} 
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="What's up? Tell me Anything. I promise to try and help. I'm Here for you"
                    style={{ 
                        width: '100%',
                        padding: '12px',
                        fontSize: '16px',
                        marginBottom: '15px',
                        border: '1px solid #ddd',
                        borderRadius: '4px'
                    }}
                />
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
                        transition: 'background 0.3s ease'
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
            </form>
            <div style={{
                padding: '20px',
                background: '#fff',
                borderRadius: '8px',
                minHeight: '120px',
                maxHeight: '60vh',
                overflowY: 'auto',
                boxShadow: '0 2px 8px rgba(0,0,0,0.1)',
                border: '1px solid #eee'
            }}>
                <div style={{ 
                    whiteSpace: 'pre-wrap',
                    lineHeight: '1.6',
                    fontSize: '16px',
                    color: '#333'
                }}>
                    {response || (loading ? "Crafting your personalized response..." : "")}
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
