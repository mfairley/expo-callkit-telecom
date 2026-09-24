/*
 *  Declarations copied from react-native-webrtc's ios/RCTWebRTC/WebRTCModuleOptions.h.
 *
 *  Copyright (c) 2015 react-native-webrtc contributors. MIT License.
 */

// Only the subset expo-callkit-telecom calls. The implementation comes from whichever
// react-native-webrtc build the app links; see README.md in this directory.

#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

@interface WebRTCModuleOptions : NSObject

/// Added in react-native-webrtc 124.0.5, the minimum supported version.
@property(nonatomic, assign) BOOL enableMultitaskingCameraAccess;

+ (instancetype)sharedInstance;

@end

NS_ASSUME_NONNULL_END
