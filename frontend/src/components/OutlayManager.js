import React, { useState } from 'react';

import { useOutlays } from '../hooks/useOutlays';
import { deleteOutlay, updateOutlay } from '../api/outlayApi';

import OutlayForm from './OutlayForm';
import Controls from './Controls';
import OutlayList from './OutlayList';

import './OutlayForm.css';

export const OutlayManager = () => {
  const [sortBy, setSortBy] = useState('');
  const [sortDirection, setSortDirection] = useState('asc');
  const [filterId, setFilterId] = useState('');
  const [filterDate, setFilterDate] = useState('');
  const [actionError, setActionError] = useState(null);
  const [deletingId, setDeletingId] = useState(null);
  const [updatingId, setUpdatingId] = useState(null);

  const { outlays, loading, error, refetch } = useOutlays({
    sortBy,
    sortDirection,
    filterId,
    filterDate,
  });

  const handleSearch = () => {
    refetch();
  };

  const handleDelete = async (id) => {
    const confirmed = window.confirm('選択した出費データを削除しますか？');
    if (!confirmed) {
      return;
    }

    setActionError(null);
    setDeletingId(id);
    try {
      await deleteOutlay(id);
      await refetch();
    } catch (deleteError) {
      console.error('Error deleting outlay:', deleteError);
      setActionError(deleteError.message);
    } finally {
      setDeletingId(null);
    }
  };

  const handleUpdate = async (id, payload) => {
    setActionError(null);
    setUpdatingId(id);
    try {
      await updateOutlay(id, payload);
      await refetch();
    } catch (updateError) {
      console.error('Error updating outlay:', updateError);
      setActionError(updateError.message);
    } finally {
      setUpdatingId(null);
    }
  };

  return (
    <>
      <OutlayForm onOutlayCreated={refetch} />
      {actionError && <p className="error-message">{actionError}</p>}
      {updatingId && (
        <p className="info-message">{`Updating outlay #${updatingId}...`}</p>
      )}

      <Controls
        sortBy={sortBy}
        setSortBy={setSortBy}
        sortDirection={sortDirection}
        setSortDirection={setSortDirection}
        filterId={filterId}
        setFilterId={setFilterId}
        filterDate={filterDate}
        setFilterDate={setFilterDate}
        onSearch={handleSearch}
      />

      <h2>Your Outlays</h2>
      <OutlayList
        loading={loading}
        error={error}
        outlays={outlays}
        onDelete={handleDelete}
        deletingId={deletingId}
        onUpdate={handleUpdate}
        updatingId={updatingId}
      />
    </>
  );
};
