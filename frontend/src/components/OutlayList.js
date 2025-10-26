import React from 'react';
import './OutlayList.css';

const OutlayList = ({
  loading,
  error,
  outlays,
  onDelete,
  deletingId,
  onUpdate,
  updatingId,
}) => {
  const handleUpdateClick = (outlay) => {
    if (!onUpdate) {
      return;
    }

    const nextItem = window.prompt('更新後の品目を入力してください', outlay.item);
    if (nextItem === null || nextItem.trim() === '') {
      return;
    }

    const nextAmountRaw = window.prompt('更新後の金額を入力してください', outlay.amount);
    if (nextAmountRaw === null) {
      return;
    }

    const nextAmount = Number(nextAmountRaw);
    if (Number.isNaN(nextAmount)) {
      alert('金額には数値を入力してください。');
      return;
    }

    onUpdate(outlay.id, { item: nextItem.trim(), amount: nextAmount });
  };

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
        {(onDelete || onUpdate) && <div>Actions</div>}
      </div>
      {outlays.map((outlay) => (
        <div className="outlay-row" key={outlay.id}>
          <div>{outlay.id}</div>
          <div>{outlay.item}</div>
          <div>¥{outlay.amount}</div>
          <div>{new Date(outlay.createdAt).toLocaleDateString('ja-JP')}</div>
          {(onDelete || onUpdate) && (
            <div className="outlay-actions">
              {onUpdate && (
                <button
                  type="button"
                  className="update-button"
                  onClick={() => handleUpdateClick(outlay)}
                  disabled={updatingId === outlay.id}
                >
                  {updatingId === outlay.id ? 'Updating…' : 'Update'}
                </button>
              )}
              {onDelete && (
                <button
                  type="button"
                  className="delete-button"
                  onClick={() => onDelete(outlay.id)}
                  disabled={deletingId === outlay.id}
                >
                  {deletingId === outlay.id ? 'Deleting…' : 'Delete'}
                </button>
              )}
            </div>
          )}
        </div>
      ))}
    </div>
  );
};

export default OutlayList;
