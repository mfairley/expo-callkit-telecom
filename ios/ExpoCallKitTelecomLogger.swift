import os

/// A wrapper around os.Logger that automatically prepends "[ExpoCallKitTelecom] [Category]" to all messages.
struct ExpoCallKitTelecomLogger {
  private let logger: Logger
  private let prefix: String

  init(subsystem: String, category: String) {
    self.logger = Logger(subsystem: subsystem, category: category)
    self.prefix = "[ExpoCallKitTelecom] [\(category)]"
  }

  func debug(_ message: String) {
    logger.debug("\(prefix, privacy: .public) \(message, privacy: .public)")
  }

  func info(_ message: String) {
    logger.info("\(prefix, privacy: .public) \(message, privacy: .public)")
  }

  func notice(_ message: String) {
    logger.notice("\(prefix, privacy: .public) \(message, privacy: .public)")
  }

  func warning(_ message: String) {
    logger.warning("\(prefix, privacy: .public) \(message, privacy: .public)")
  }

  func error(_ message: String) {
    logger.error("\(prefix, privacy: .public) \(message, privacy: .public)")
  }

  func fault(_ message: String) {
    logger.fault("\(prefix, privacy: .public) \(message, privacy: .public)")
  }
}

/// Module-wide logging for ExpoCallKitTelecom.
/// Uses Apple's unified logging system (os.Logger) for efficient, privacy-aware logging.
/// All messages are automatically prefixed with "ExpoCallKitTelecom:".
enum Log {
  private static let subsystem = "expo-callkit-telecom"

  /// Logger for audio session management.
  static let audio = ExpoCallKitTelecomLogger(subsystem: subsystem, category: "Audio")

  /// Logger for call management and CallKit integration.
  static let call = ExpoCallKitTelecomLogger(subsystem: subsystem, category: "Call")

  /// Logger for VoIP push notifications.
  static let voipPush = ExpoCallKitTelecomLogger(subsystem: subsystem, category: "VoIPPush")

  /// Logger for the Expo module interface.
  static let module = ExpoCallKitTelecomLogger(subsystem: subsystem, category: "Module")
}
