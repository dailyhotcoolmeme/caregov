module.exports = {
  name: '케어동행', slug: 'caregov', version: '0.6.0',
  orientation: 'portrait', userInterfaceStyle: 'light',
  runtimeVersion: '1.0.0',
  updates: {
    url: 'https://caregov-ota.dailyhotcoolmeme.workers.dev/manifest',
    fallbackToCacheTimeout: 0, checkAutomatically: 'NEVER',
  },
  android: { package: 'com.ourmine.caregov.demo', versionCode: 10, softwareKeyboardLayoutMode: 'resize' },
  experiments: { reactCompiler: false },
  plugins: [
    '@react-native-community/datetimepicker',
    ['expo-build-properties', { android: { buildArchs: ['arm64-v8a', 'x86_64'], enableMinifyInReleaseBuilds: true, enableShrinkResourcesInReleaseBuilds: true } }],
    './plugins/withCaregov.js',
  ],
};
