import { registerWebModule, NativeModule } from 'expo';

import { ExpoCallKitTelecomModuleEvents } from './ExpoCallKitTelecom.types';

class ExpoCallKitTelecomModule extends NativeModule<ExpoCallKitTelecomModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
}

export default registerWebModule(ExpoCallKitTelecomModule, 'ExpoCallKitTelecomModule');
