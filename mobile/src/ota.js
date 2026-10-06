import * as Updates from 'expo-updates';
import { AppState } from 'react-native';
let checking = false;
let downloaded = false;
let blocked = false;
let listener = () => {};
export function setUpdateBlocked(value) { blocked = value; }
export function subscribeUpdates(fn) { listener = fn; return () => { listener = () => {}; }; }
export async function checkUpdate(manual = false) {
  if (__DEV__ || !Updates.isEnabled) { if (manual) listener('개발 환경에서는 업데이트를 확인하지 않습니다.'); return; }
  if (checking) return;
  checking = true;
  try {
    if (!downloaded) {
      if (manual) listener('업데이트를 확인하고 있습니다.');
      const update = await Updates.checkForUpdateAsync();
      if (!update.isAvailable) { if (manual) listener('최신 버전입니다.'); return; }
      const result = await Updates.fetchUpdateAsync();
      downloaded = result.isNew || result.isRollBackToEmbedded;
    }
    if (downloaded && !blocked && AppState.currentState === 'active') {
      listener('업데이트를 적용하고 있습니다.');
      await Updates.reloadAsync();
    } else if (downloaded) listener('업데이트를 받았습니다. 작성 완료 후 적용됩니다.');
  } catch {
    if (manual) listener('업데이트를 확인하지 못했습니다. 잠시 후 다시 시도해 주세요.');
  } finally { checking = false; }
}
export const releaseInfo = () => ({ runtime: Updates.runtimeVersion || '개발', id: Updates.updateId || '기본', embedded: Updates.isEmbeddedLaunch });
