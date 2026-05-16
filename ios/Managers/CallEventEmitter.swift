import ExpoModulesCore
import Foundation

/// A queued event waiting to be sent to JS.
private struct QueuedEvent {
  let body: [String: Any]
  let timestamp: Date
}

/// Manages event delivery to the Expo module, buffering events until JS is ready.
///
/// CallKit events can fire before the JS layer has mounted its listeners.
/// This class queues those events and flushes them once JS starts observing.
///
/// Supports per-event configuration:
/// - Track which events are being observed independently
/// - Configure queue limits per event (0 = no queueing, nil = unlimited)
@MainActor
final class CallEventEmitter {
  static let shared = CallEventEmitter()

  /// The Expo module to send events to. Set this when the module initializes.
  weak var module: ExpoCallKitTelecomModule?

  /// Events currently being observed by JS.
  private var observingEvents: Set<String> = []

  /// Per-event queues for buffering events before JS is ready.
  private var eventQueues: [String: [QueuedEvent]] = [:]

  /// Per-event queue limits. `nil` = unlimited, `0` = no queueing.
  private var queueLimits: [String: Int] = [:]

  /// Default queue limit for events without explicit configuration.
  /// Set to `nil` for unlimited, `0` to disable queueing by default.
  var defaultQueueLimit: Int? = 0

  private init() {}

  // MARK: - Configuration

  /// Set the queue limit for a specific event.
  /// - Parameters:
  ///   - eventName: The event name to configure
  ///   - limit: Maximum queued events. `nil` = unlimited, `0` = no queueing.
  func setQueueLimit(for eventName: String, limit: Int?) {
    if let limit = limit {
      queueLimits[eventName] = limit
    } else {
      queueLimits.removeValue(forKey: eventName)
    }
  }

  /// Set the queue limit for a specific event type (type-safe).
  /// - Parameters:
  ///   - eventType: The event type to configure
  ///   - limit: Maximum queued events. `nil` = unlimited, `0` = no queueing.
  func setQueueLimit<E: CallEvent>(for eventType: E.Type, limit: Int?) {
    setQueueLimit(for: E.name, limit: limit)
  }

  /// Set queue limits for multiple events at once.
  func setQueueLimits(_ limits: [String: Int?]) {
    for (eventName, limit) in limits {
      setQueueLimit(for: eventName, limit: limit)
    }
  }

  /// Disable queueing for events that should only be delivered in real-time.
  /// Useful for events like mute/hold changes that are only relevant when observed.
  func disableQueueing<E: CallEvent>(for eventType: E.Type) {
    setQueueLimit(for: E.name, limit: 0)
  }

  // MARK: - Status

  /// Check if a specific event is currently being observed.
  func isObserving(eventName: String) -> Bool {
    observingEvents.contains(eventName)
  }

  /// Check if a specific event type is currently being observed.
  func isObserving<E: CallEvent>(_ eventType: E.Type) -> Bool {
    isObserving(eventName: E.name)
  }

  /// Get the current queue count for a specific event.
  func queueCount(for eventName: String) -> Int {
    eventQueues[eventName]?.count ?? 0
  }

  /// Get the total count of all queued events.
  var totalQueueCount: Int {
    eventQueues.values.reduce(0) { $0 + $1.count }
  }

  // MARK: - Public API

  /// Send a type-safe event to JS, or buffer it if JS isn't listening yet.
  func send<E: CallEvent>(_ event: E) {
    let eventName = E.name
    let timestamp = Date()

    if observingEvents.contains(eventName), let module = module {
      Log.call.debug("Sending event to JS - name: \(eventName)")
      let body = buildEventBody(
        event.body,
        flushed: false,
        timestamp: timestamp
      )
      module.sendEvent(eventName, body)
    } else {
      queueEvent(name: eventName, body: event.body, timestamp: timestamp)
    }
  }

  // MARK: - Expo Lifecycle

  /// Called when JS mounts a listener for a specific event.
  func startObserving(eventName: String) {
    let queueCount = eventQueues[eventName]?.count ?? 0
    Log.call.debug(
      "Start observing - event: \(eventName), queuedEvents: \(queueCount)"
    )
    observingEvents.insert(eventName)
    flushQueue(for: eventName)
  }

  /// Called when JS unmounts the listener for a specific event.
  func stopObserving(eventName: String) {
    Log.call.debug("Stop observing - event: \(eventName)")
    observingEvents.remove(eventName)
  }

  // MARK: - Private

  private func buildEventBody(
    _ body: [String: Any],
    flushed: Bool,
    timestamp: Date
  ) -> [String: Any] {
    var result = body
    result["meta"] = [
      "flushed": flushed,
      "timestamp": ISO8601DateFormatter().string(from: timestamp),
    ]
    return result
  }

  private func queueEvent(name: String, body: [String: Any], timestamp: Date) {
    let limit = queueLimits[name] ?? defaultQueueLimit

    // If limit is 0, don't queue at all
    if limit == 0 {
      Log.call.debug("Dropping event (queueing disabled) - name: \(name)")
      return
    }

    var queue = eventQueues[name] ?? []
    queue.append(QueuedEvent(body: body, timestamp: timestamp))

    // Enforce limit by dropping oldest events if necessary
    if let limit = limit, queue.count > limit {
      let dropCount = queue.count - limit
      queue.removeFirst(dropCount)
      Log.call.debug(
        "Queueing event (dropped \(dropCount) old) - name: \(name), queueSize: \(queue.count)"
      )
    } else {
      Log.call.debug(
        "Queueing event (JS not listening) - name: \(name), queueSize: \(queue.count)"
      )
    }

    eventQueues[name] = queue
  }

  private func flushQueue(for eventName: String) {
    guard let queue = eventQueues[eventName], !queue.isEmpty,
      let module = module
    else {
      return
    }

    Log.call.debug(
      "Flushing event queue - event: \(eventName), count: \(queue.count)"
    )
    for event in queue {
      let body = buildEventBody(
        event.body,
        flushed: true,
        timestamp: event.timestamp
      )
      module.sendEvent(eventName, body)
    }

    eventQueues.removeValue(forKey: eventName)
  }
}
