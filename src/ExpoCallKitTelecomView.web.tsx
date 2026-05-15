import * as React from 'react';

import { ExpoCallKitTelecomViewProps } from './ExpoCallKitTelecom.types';

export default function ExpoCallKitTelecomView(props: ExpoCallKitTelecomViewProps) {
  return (
    <div>
      <iframe
        style={{ flex: 1 }}
        src={props.url}
        onLoad={() => props.onLoad({ nativeEvent: { url: props.url } })}
      />
    </div>
  );
}
