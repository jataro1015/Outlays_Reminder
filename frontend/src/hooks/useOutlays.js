import { useState, useCallback, useEffect } from 'react';
import { getOutlays } from '../api/outlayApi';

export const useOutlays = ({ sortBy, sortDirection, filterId, filterDate }) => {
  const [outlays, setOutlays] = useState([]);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchOutlays = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getOutlays({ sortBy, sortDirection, filterId, filterDate });
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

  return { outlays, loading, error, refetch: fetchOutlays };
};
