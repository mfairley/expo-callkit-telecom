import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoCallKitTelecomViewProps } from './ExpoCallKitTelecom.types';

const NativeView: React.ComponentType<ExpoCallKitTelecomViewProps> =
  requireNativeView('ExpoCallKitTelecom');

export default function ExpoCallKitTelecomView(props: ExpoCallKitTelecomViewProps) {
  return <NativeView {...props} />;
}
