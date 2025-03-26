import React, { useState, useRef } from 'react';

export const Textbox = () => {
    const [input, setInput] = useState('');
    const [response, setResponse] = useState('');
    const [loading, setLoading] = useState(false);
    const abortControllerRef = useRef(null);

    const handleSubmit = async (e) => {
        e.preventDefault();
        // Abort previous request
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
                signal: controller.signal // Attach abort signal
            });

            const reader = response.body.getReader();
            const decoder = new TextDecoder();

            while (true) {
                const { done, value } = await reader.read();
                if (done) break;
                const chunk = decoder.decode(value);
                setResponse(prev => prev + chunk);
            }
        } catch (error) {
            if (error.name === 'AbortError') {
                console.log('Request aborted');
            } else {
                setResponse("Error connecting to server");
            }
        } finally {
            setLoading(false);
            abortControllerRef.current = null;
        }
    };

    return (
        <div>
            <form onSubmit={handleSubmit}>
                <input 
                    type="text" 
                    value={input} 
                    onChange={(e) => setInput(e.target.value)}
                    placeholder="What's up? Tell me Anything. I promise to try and help. I'm Here for you"
                />
                <button type="submit" disabled={loading}>
                    {loading ? "Loading..." : "Submit"}
                </button>
            </form>
            <div>
                <strong>Response:</strong>
                <p>{response}</p>
            </div>
        </div>
    );
};