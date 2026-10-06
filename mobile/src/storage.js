import AsyncStorage from '@react-native-async-storage/async-storage';
import { requireNativeModule } from 'expo';
import { Platform } from 'react-native';
import { migrateLegacy, accounts } from './domain.mjs';
const KEY = 'caregov.records.v1';
export async function loadState() {
  const current = await AsyncStorage.getItem(KEY);
  if (current !== null) {
    const value = JSON.parse(current);
    if (value.schema !== 1 || !Array.isArray(value.records) || !accounts.some(a => a.id === value.accountId)) throw new Error('저장된 정보를 확인해 주세요. 원본은 보존되어 있습니다.');
    return value;
  }
  const legacy = Platform.OS === 'android' ? requireNativeModule('CaregovLegacyStore').read() : null;
  const state = migrateLegacy(legacy);
  await saveState(state);
  return state;
}
export const saveState = state => AsyncStorage.setItem(KEY, JSON.stringify(state));
