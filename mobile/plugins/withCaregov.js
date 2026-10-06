const { withAppBuildGradle, withAndroidManifest, withDangerousMod } = require('expo/config-plugins');
const { copyFileSync } = require('node:fs');
const { join } = require('node:path');
module.exports = config => {
  config = withAndroidManifest(config, value => {
    value.modResults.manifest.application[0].$['android:icon'] = '@drawable/caregov_launcher';
    value.modResults.manifest.application[0].$['android:roundIcon'] = '@drawable/caregov_launcher';
    return value;
  });
  config = withDangerousMod(config, ['android', async value => {
    copyFileSync(join(value.modRequest.projectRoot, '../app/src/main/res/drawable/ic_launcher.xml'),
      join(value.modRequest.platformProjectRoot, 'app/src/main/res/drawable/caregov_launcher.xml'));
    return value;
  }]);
  return withAppBuildGradle(config, value => {
  // Reuse the installed Compose app's certificate, never the generated Expo key.
  value.modResults.contents = value.modResults.contents.replace(
    "storeFile file('debug.keystore')",
    "storeFile new File(System.getProperty('user.home'), '.android/debug.keystore')",
  );
  return value;
  });
};
