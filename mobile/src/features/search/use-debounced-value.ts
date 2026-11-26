import { useEffect, useState } from 'react';

/** 값이 delayMilliseconds 동안 바뀌지 않으면 그때 반영 (입력을 멈춘 뒤 검색) */
export function useDebouncedValue<Value>(value: Value, delayMilliseconds: number): Value {
  const [debouncedValue, setDebouncedValue] = useState(value);
  useEffect(() => {
    const timeoutHandle = setTimeout(() => setDebouncedValue(value), delayMilliseconds);
    return () => clearTimeout(timeoutHandle);
  }, [delayMilliseconds, value]);
  return debouncedValue;
}
