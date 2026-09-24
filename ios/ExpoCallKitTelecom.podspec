require 'json'

package = JSON.parse(File.read(File.join(__dir__, '..', 'package.json')))

Pod::Spec.new do |s|
  s.name           = 'ExpoCallKitTelecom'
  s.version        = package['version']
  s.summary        = package['description']
  s.description    = package['description']
  s.license        = package['license']
  s.author         = package['author']
  s.homepage       = package['homepage']
  s.platforms      = {
    :ios => '16.0'
  }
  s.swift_version  = '5.9'
  s.source         = { git: package['repository']['url'] }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'
  s.dependency 'swift-collections'
  # No WebRTC pod dependency: Swift compiles against the declarations in WebRTCHeaders/, and the
  # classes resolve when the app links whichever WebRTC build it ships. See WebRTCHeaders/README.md.

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
    'SWIFT_INCLUDE_PATHS' => '$(PODS_TARGET_SRCROOT)/WebRTCHeaders',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
  s.exclude_files = "WebRTCHeaders/**"
  s.preserve_paths = "WebRTCHeaders/**"
end
