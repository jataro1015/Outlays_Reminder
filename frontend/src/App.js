import React, { useEffect, useState, useCallback } from 'react';
import './App.css';

import { FaSun, FaMoon } from 'react-icons/fa';

function App() {
  const [outlays, setOutlays] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);
  const [sortBy, setSortBy] = useState('');
  const [sortDirection, setSortDirection] = useState('asc');
  const [filterId, setFilterId] = useState('');
  const [filterDate, setFilterDate] = useState('');
  const [theme, setTheme] = useState('dark'); // 'dark' or 'light'

  const [newItem, setNewItem] = useState('');
  const [newAmount, setNewAmount] = useState('');
  const [createError, setCreateError] = useState(null);


  const toggleTheme = () => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  };

  const fetchOutlays = useCallback(async () => {
    setLoading(true);
    setError(null);
    let url = '/api/v1/outlays';
    const params = new URLSearchParams();

    if (sortBy) {
      params.append('sortBy', sortBy);
      params.append('sortDirection', sortDirection);
    }

    if (filterId) {
      url = `/api/v1/outlays/${filterId}`;
    } else if (filterDate) {
      url = '/api/v1/outlays/on-date';
      params.append('date', filterDate);
    }

    if (params.toString()) {
      url += `?${params.toString()}`;
    }

    try {
      const response = await fetch(url);
      if (!response.ok) {
        const err = await response.json();
        throw new Error(err.message || 'Unknown error');
      }
      const data = await response.json();
      // ID検索の場合、単一のオブジェクトが返される可能性があるため、配列に変換
      setOutlays(Array.isArray(data) ? data : [data]);
    } catch (error) {
      console.error('Error fetching outlays:', error);
      setError(error.message);
      setOutlays([]); // エラー時はデータをクリア
    } finally {
      setLoading(false);
    }
  }, [sortBy, sortDirection, filterId, filterDate]);

  useEffect(() => {
    fetchOutlays();
  }, [fetchOutlays]);

  const handleSearch = () => {
    fetchOutlays();
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    setCreateError(null);

    if (!newItem || !newAmount) {
      setCreateError('Item and amount are required.');
      return;
    }

    try {
      const response = await fetch('/api/v1/outlays', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ item: newItem, amount: parseFloat(newAmount) }),
      });

      if (!response.ok) {
        const err = await response.json();
        let errorMessage = 'Failed to create outlay';
        if (err) {
          if (err.message) {
            errorMessage = err.message;
          } else {
            // Handle validation errors like { item: ["must not be blank"], amount: ["must be greater than 0"] }
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

      setNewItem('');
      setNewAmount('');
      fetchOutlays(); // Refresh the list
    } catch (error) {
      console.error('Error creating outlay:', error);
      setCreateError(error.message);
    }
  };

  return (
    <div className={`App ${theme}`}>
      <header className="App-header">
        <div className="toggle-container">
          <FaSun />
          <label className="toggle-switch">
            <input type="checkbox" checked={theme === 'dark'} onChange={toggleTheme} />
            <span className="slider round"></span>
          </label>
          <FaMoon />
        </div>
        <h1>Outlays Reminder</h1>

        <div className="create-form">
          <h3>Add New Outlay</h3>
          <form onSubmit={handleCreate}>
            <div>
              <label>Item:</label>
              <input
                type="text"
                value={newItem}
                onChange={(e) => setNewItem(e.target.value)}
                placeholder="Enter item"
              />
            </div>
            <div>
              <label>Amount:</label>
              <input
                type="number"
                value={newAmount}
                onChange={(e) => setNewAmount(e.target.value)}
                placeholder="Enter amount"
              />
            </div>
            <button type="submit">Add Outlay</button>
            {createError && <p className="error-message">{createError}</p>}
          </form>
        </div>

        <div className="controls">
          <h3>Filter & Sort</h3>
          <div>
            <label>Sort By:</label>
            <select value={sortBy} onChange={(e) => setSortBy(e.target.value)}>
              <option value="">None</option>
              <option value="item">Item</option>
              <option value="amount">Amount</option>
              <option value="createdAt">Date</option>
              <option value="id">ID</option>
            </select>
            <select value={sortDirection} onChange={(e) => setSortDirection(e.target.value)}>
              <option value="asc">Ascending</option>
              <option value="desc">Descending</option>
            </select>
          </div>
          <div>
            <label>Filter by ID:</label>
            <input
              type="number"
              value={filterId}
              onChange={(e) => setFilterId(e.target.value)}
              placeholder="Enter ID"
            />
          </div>
          <div>
            <label>Filter by Date:</label>
            <input
              type="date"
              value={filterDate}
              onChange={(e) => setFilterDate(e.target.value)}
            />
          </div>
          <button onClick={handleSearch}>Apply Filters/Sort</button>
        </div>

        <h2>Your Outlays</h2>
        {loading ? (
          <p>Loading outlays...</p>
        ) : error ? (
          <p>Error: {error}</p>
        ) : outlays.length > 0 ? (
          <div className="outlay-list">
            <div className="outlay-header">
              <div>ID</div>
              <div>Item</div>
              <div>Amount</div>
              <div>Date</div>
            </div>
            {outlays.map(outlay => (
              <div className="outlay-row" key={outlay.id}>
                <div>{outlay.id}</div>
                <div>{outlay.item}</div>
                <div>¥{outlay.amount}</div>
                <div>{new Date(outlay.createdAt).toLocaleDateString('ja-JP')}</div>
              </div>
            ))}
          </div>
        ) : (
          <p>No outlays found.</p>
        )}
      </header>
    </div>
  );
}

export default App;