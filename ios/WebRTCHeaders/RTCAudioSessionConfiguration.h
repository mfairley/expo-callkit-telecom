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

@interface RTCAudioSessionConfiguration : NSObject

@property(nonatomic, strong) NSString *category;
@property(nonatomic, assign) AVAudioSessionCategoryOptions categoryOptions;
@property(nonatomic, strong) NSString *mode;

- (instancetype)init NS_DESIGNATED_INITIALIZER;

+ (void)setWebRTCConfiguration:(RTCAudioSessionConfiguration *)configuration;

@end

NS_ASSUME_NONNULL_END
