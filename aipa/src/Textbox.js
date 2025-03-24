import React, { useState } from 'react';

export const Textbox = () => {
  const [input, setInput] = useState('');
  const [response, setResponse] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setLoading(true);
    setResponse('');

    try {
      const res = await fetch('https://2e7f-41-90-184-126.ngrok-free.app/api/generate', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ 
          model: "phi3:3.8b",
          prompt: input 
        })
      });

      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        
        buffer += decoder.decode(value, { stream: true });
        
        // Process complete lines
        const lines = buffer.split('\n');
        buffer = lines.pop() || ''; // Keep incomplete line in buffer
        
        for (const line of lines) {
          try {
            const parsed = JSON.parse(line);
            if (parsed.response) {
              setResponse(prev => prev + parsed.response);
            }
            if (parsed.done) break;
          } catch (err) {
            console.error('Error parsing JSON:', err);
          }
        }
      }
    } catch (error) {
      setResponse("Error connecting to the AI server");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ padding: '20px', maxWidth: '600px' }}>
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder="Ask me anything..."
          disabled={loading}
          style={{ 
            width: '100%', 
            padding: '10px',
            marginBottom: '10px' 
          }}
        />
        <button 
          type="submit" 
          disabled={loading}
          style={{
            padding: '10px 15px',
            background: loading ? '#ccc' : '#007bff',
            color: 'white',
            border: 'none',
            borderRadius: '4px'
          }}
        >
          {loading ? 'Processing...' : 'Ask'}
        </button>
      </form>
      
      {response && (
        <div style={{ 
          marginTop: '20px',
          padding: '15px',
          border: '1px solid #ddd',
          borderRadius: '4px',
          whiteSpace: 'pre-wrap',
          minHeight: '100px',
          background: '#f8f9fa'
        }}>
          {response}
        </div>
      )}
    </div>
  );
};