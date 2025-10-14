import React, { useState } from 'react';

import { useOutlays } from '../hooks/useOutlays';

import OutlayForm from './OutlayForm';
import Controls from './Controls';
import OutlayList from './OutlayList';

import './OutlayForm.css';


export const OutlayManager = () => {
  const [sortBy, setSortBy] = useState('');
  const [sortDirection, setSortDirection] = useState('asc');
  const [filterId, setFilterId] = useState('');
  const [filterDate, setFilterDate] = useState('');

  const { outlays, loading, error, refetch } = useOutlays({
    sortBy,
    sortDirection,
    filterId,
    filterDate,
  });

  const handleSearch = () => {
    refetch();
  };

  return (
    <>
      <OutlayForm onOutlayCreated={refetch} />

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
      <OutlayList loading={loading} error={error} outlays={outlays} />
    </>
  );
};
