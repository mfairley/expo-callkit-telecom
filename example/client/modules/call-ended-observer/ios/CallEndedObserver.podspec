Pod::Spec.new do |s|
  s.name           = 'CallEndedObserver'
  s.version        = '1.0.0'
  s.summary        = 'Example observer for call events that could not reach JS'
  s.description    = 'Observes the expo-callkit-telecom ACTION_CALL_EVENT notification and logs a killed-app decline.'
  s.author         = ''
  s.homepage       = 'https://github.com/mfairley/expo-callkit-telecom'
  s.license        = 'MIT'
  s.platforms      = {
    :ios => '16.0'
  }
  s.swift_version  = '5.9'
  s.source         = { git: '' }
  s.static_framework = true

  s.dependency 'ExpoModulesCore'

  # Swift/Objective-C compatibility
  s.pod_target_xcconfig = {
    'DEFINES_MODULE' => 'YES',
  }

  s.source_files = "**/*.{h,m,mm,swift,hpp,cpp}"
end
