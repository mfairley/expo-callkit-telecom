// Reexport the native module. On web, it will be resolved to ExpoCallKitTelecomModule.web.ts
// and on native platforms to ExpoCallKitTelecomModule.ts
export { default } from './ExpoCallKitTelecomModule';
export { default as ExpoCallKitTelecomView } from './ExpoCallKitTelecomView';
export * from  './ExpoCallKitTelecom.types';
