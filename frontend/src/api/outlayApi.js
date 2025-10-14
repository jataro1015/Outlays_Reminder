export const getOutlays = async ({ sortBy, sortDirection, filterId, filterDate }) => {
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

  const response = await fetch(url);
  if (!response.ok) {
    const err = await response.json();
    throw new Error(err.message || 'Unknown API error');
  }

  return response.json();
};
