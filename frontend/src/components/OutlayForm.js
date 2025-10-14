import React, { useState } from 'react';
import './OutlayForm.css';

const OutlayForm = ({ onOutlayCreated }) => {
  const [item, setItem] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState(null);

  const handleCreate = async (e) => {
    e.preventDefault();
    setError(null);

    if (!item || !amount) {
      setError('Item and amount are required.');
      return;
    }

    try {
      const response = await fetch('/api/v1/outlays', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ item: item, amount: parseFloat(amount) }),
      });

      if (!response.ok) {
        const err = await response.json();
        let errorMessage = 'Failed to create outlay';
        if (err) {
          if (err.message) {
            errorMessage = err.message;
          } else {
            const errorFields = Object.keys(err).map(field => {
              return `${field}: ${err[field].join(', ')}`;
            });
            if (errorFields.length > 0) {
              errorMessage = errorFields.join('\n');
            }
          }
        }
        throw new Error(errorMessage);
      }

      setItem('');
      setAmount('');
      onOutlayCreated(); // Callback to refresh the list in the parent component
    } catch (error) {
      console.error('Error creating outlay:', error);
      setError(error.message);
    }
  };

  return (
    <div className="create-form">
      <h3>Add New Outlay</h3>
      <form onSubmit={handleCreate}>
        <div>
          <label>Item:</label>
          <input
            type="text"
            value={item}
            onChange={(e) => setItem(e.target.value)}
            placeholder="Enter item"
          />
        </div>
        <div>
          <label>Amount:</label>
          <input
            type="number"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            placeholder="Enter amount"
          />
        </div>
        <button type="submit">Add Outlay</button>
        {error && <p className="error-message">{error}</p>}
      </form>
    </div>
  );
};

export default OutlayForm;
