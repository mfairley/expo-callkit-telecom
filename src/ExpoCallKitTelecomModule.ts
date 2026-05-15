import { NativeModule, requireNativeModule } from 'expo';

import { ExpoCallKitTelecomModuleEvents } from './ExpoCallKitTelecom.types';

declare class ExpoCallKitTelecomModule extends NativeModule<ExpoCallKitTelecomModuleEvents> {
  PI: number;
  hello(): string;
  setValueAsync(value: string): Promise<void>;
}

// This call loads the native module object from the JSI.
export default requireNativeModule<ExpoCallKitTelecomModule>('ExpoCallKitTelecom');
