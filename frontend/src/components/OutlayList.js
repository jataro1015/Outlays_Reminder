import React from 'react';
import './OutlayList.css';

const OutlayList = ({ loading, error, outlays, onDelete, deletingId }) => {
  if (loading) {
    return <p>Loading outlays...</p>;
  }

  if (error) {
    return <p>Error: {error}</p>;
  }

  if (outlays.length === 0) {
    return <p>No outlays found.</p>;
  }

  return (
    <div className="outlay-list">
      <div className="outlay-header">
        <div>ID</div>
        <div>Item</div>
        <div>Amount</div>
        <div>Date</div>
        {onDelete && <div>Actions</div>}
      </div>
      {outlays.map((outlay) => (
        <div className="outlay-row" key={outlay.id}>
          <div>{outlay.id}</div>
          <div>{outlay.item}</div>
          <div>¥{outlay.amount}</div>
          <div>{new Date(outlay.createdAt).toLocaleDateString('ja-JP')}</div>
          {onDelete && (
            <div className="outlay-actions">
              <button
                type="button"
                className="delete-button"
                onClick={() => onDelete(outlay.id)}
                disabled={deletingId === outlay.id}
              >
                {deletingId === outlay.id ? 'Deleting…' : 'Delete'}
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

export default OutlayList;