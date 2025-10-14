import React from 'react';
import './Controls.css';

const Controls = ({
  sortBy,
  setSortBy,
  sortDirection,
  setSortDirection,
  filterId,
  setFilterId,
  filterDate,
  setFilterDate,
  onSearch,
}) => {
  return (
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
      <button onClick={onSearch}>Apply Filters/Sort</button>
    </div>
  );
};

export default Controls;
