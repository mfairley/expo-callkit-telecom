/*
 *  Declarations copied from WebRTC's sdk/objc/components/audio/RTCAudioSession.h.
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

#import "RTCAudioSessionConfiguration.h"

NS_ASSUME_NONNULL_BEGIN

/// The instance methods of WebRTC's RTCAudioSession.
@protocol ECKTRTCAudioSession <NSObject>

@property(nonatomic, readonly) BOOL isActive;
@property(nonatomic, assign) BOOL useManualAudio;
@property(nonatomic, assign) BOOL isAudioEnabled;

- (void)lockForConfiguration;
- (void)unlockForConfiguration;

- (void)audioSessionDidActivate:(AVAudioSession *)session;
- (void)audioSessionDidDeactivate:(AVAudioSession *)session;

- (BOOL)overrideOutputAudioPort:(AVAudioSessionPortOverride)portOverride
                          error:(NSError **)outError;
- (BOOL)setConfiguration:(id<ECKTRTCAudioSessionConfiguration>)configuration
                   error:(NSError **)outError;

@end

/// The class methods of WebRTC's RTCAudioSession.
@protocol ECKTRTCAudioSessionClass <NSObject>
+ (id<ECKTRTCAudioSession>)sharedInstance;
@end

/// `[RTCAudioSession sharedInstance]`, or nil if the app doesn't link WebRTC.
static inline id<ECKTRTCAudioSession> _Nullable ECKTRTCAudioSessionSharedInstance(void) {
  Class<ECKTRTCAudioSessionClass> cls = ECKTWebRTCClass(@"RTCAudioSession");
  return [cls sharedInstance];
}

NS_ASSUME_NONNULL_END
