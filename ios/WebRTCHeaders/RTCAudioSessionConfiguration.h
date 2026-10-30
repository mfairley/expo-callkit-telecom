/*
 *  Declarations copied from WebRTC's sdk/objc/components/audio/RTCAudioSessionConfiguration.h.
 *
 *  Copyright 2016 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

// Only the subset expo-callkit-telecom calls. The implementation comes from whichever
// WebRTC framework the app links; see README.md in this directory.

#import <AVFoundation/AVFoundation.h>
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// Looks up a WebRTC class by its unprefixed name. LiveKit's WebRTC build (LiveKitWebRTC, used by
/// @livekit/react-native-webrtc >= 144.2.0) prefixes its classes with `LK`; other builds don't.
static inline Class _Nullable ECKTWebRTCClass(NSString *name) {
  return NSClassFromString([@"LK" stringByAppendingString:name]) ?: NSClassFromString(name);
}

/// The instance methods of WebRTC's RTCAudioSessionConfiguration.
@protocol ECKTRTCAudioSessionConfiguration <NSObject>

@property(nonatomic, strong) NSString *category;
@property(nonatomic, assign) AVAudioSessionCategoryOptions categoryOptions;
@property(nonatomic, strong) NSString *mode;

@end

/// The class methods of WebRTC's RTCAudioSessionConfiguration.
@protocol ECKTRTCAudioSessionConfigurationClass <NSObject>
+ (void)setWebRTCConfiguration:(id<ECKTRTCAudioSessionConfiguration>)configuration;
@end

/// `[[RTCAudioSessionConfiguration alloc] init]`, or nil if the app doesn't link WebRTC.
static inline id<ECKTRTCAudioSessionConfiguration> _Nullable
ECKTRTCAudioSessionConfigurationCreate(void) {
  Class cls = ECKTWebRTCClass(@"RTCAudioSessionConfiguration");
  return [[cls alloc] init];
}

/// `[RTCAudioSessionConfiguration setWebRTCConfiguration:]`. Does nothing if the app doesn't link
/// WebRTC.
static inline void ECKTRTCAudioSessionConfigurationSetWebRTCConfiguration(
    id<ECKTRTCAudioSessionConfiguration> configuration) {
  Class<ECKTRTCAudioSessionConfigurationClass> cls =
      ECKTWebRTCClass(@"RTCAudioSessionConfiguration");
  [cls setWebRTCConfiguration:configuration];
}

NS_ASSUME_NONNULL_END
