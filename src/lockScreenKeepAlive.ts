import { AppRegistry, Platform } from "react-native";

/**
 * Key of the headless task the native side holds for a call answered on the
 * Android lock screen (`androidAnswerOnLockScreen`). Registered here so apps
 * need no code of their own; do not register this key yourself.
 */
export const LOCK_SCREEN_CALL_KEEP_ALIVE_TASK = "ExpoCallKitTelecomCallKeepAlive";

// React Native pauses JS timers while the host activity is paused and keeps
// them running while a headless task is active. The native side starts this
// task when a call is answered on the lock screen and finishes it when the
// call ends; the task itself never resolves. Android only: iOS has no headless
// tasks, and CallKit draws its own lock-screen call UI.
if (Platform.OS === "android") {
  AppRegistry.registerHeadlessTask(
    LOCK_SCREEN_CALL_KEEP_ALIVE_TASK,
    () => () => new Promise<void>(() => {}),
  );
}
