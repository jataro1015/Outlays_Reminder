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
          <ul>
            {outlays.map(outlay => (
              <li key={outlay.id}>
                ID: {outlay.id}, Item: {outlay.item}, Amount: {outlay.amount}, Date: {new Date(outlay.createdAt).toLocaleDateString()}
              </li>
            ))}
          </ul>
        ) : (
          <p>No outlays found.</p>
        )}
      </header>
    </div>
  );
}

export default App;