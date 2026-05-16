import Collections
import Foundation

/// Thread-safe store for active call sessions.
actor CallStore {
  private var sessions: OrderedDictionary<UUID, CallSession> = [:]

  private struct SessionObserver: Sendable {
    let continuation: AsyncStream<CallSession>.Continuation
    let filter: @Sendable (CallSession) -> Bool
  }
  private var sessionObservers: [UUID: [UUID: SessionObserver]] = [:]

  /// The first session, if any.
  var firstSession: CallSession? {
    sessions.values.first
  }

  /// All sessions in insertion order.
  var allSessions: [CallSession] {
    Array(sessions.values)
  }

  /// Get a session by its system call ID.
  func session(for id: UUID) -> CallSession? {
    sessions[id]
  }

  /// Add a new session. No-op if session with same ID already exists.
  func add(_ session: CallSession) {
    guard sessions[session.id] == nil else { return }
    sessions[session.id] = session
    Task { @MainActor in
      CallEventEmitter.shared.send(CallSessionAddedEvent(session: session))
    }
  }

  /// Remove a session by its system call ID. No-op if session doesn't exist.
  func remove(for id: UUID) {
    guard sessions.removeValue(forKey: id) != nil else { return }
    Task { @MainActor in
      CallEventEmitter.shared.send(CallSessionRemovedEvent(id: id))
    }
  }

  /// Remove all sessions.
  func removeAll() {
    let ids = Array(sessions.keys)
    sessions.removeAll()
    Task { @MainActor in
      for id in ids {
        CallEventEmitter.shared.send(CallSessionRemovedEvent(id: id))
      }
    }
  }

  // MARK: - Updates

  /// Update a session using a closure.
  /// Only sends update events if the session actually changed.
  func update(for id: UUID, _ transform: (inout CallSession) -> Void) {
    guard var session = sessions[id] else { return }
    let previousSession = session
    transform(&session)

    // Skip if nothing changed
    guard session != previousSession else { return }

    sessions[id] = session

    // Notify observers whose filter matches
    sessionObservers[id]?.values.forEach { observer in
      if observer.filter(session) {
        observer.continuation.yield(session)
      }
    }

    Task { @MainActor in
      CallEventEmitter.shared.send(CallSessionUpdatedEvent(session: session))
    }
  }

  /// Update the status of a session.
  func updateStatus(for id: UUID, status: CallSession.Status) {
    update(for: id) { session in
      session.status = status
    }
  }

  /// Update the connectedAt timestamp of a session.
  func updateConnectedAt(for id: UUID, connectedAt: Date?) {
    update(for: id) { session in
      session.connectedAt = connectedAt
    }
  }

  /// Update the muted state of a session.
  func updateMuted(for id: UUID, isMuted: Bool) {
    update(for: id) { session in
      session.isMuted = isMuted
    }
  }

  /// Update the held state of a session.
  func updateHeld(for id: UUID, isOnHold: Bool) {
    update(for: id) { session in
      session.isOnHold = isOnHold
    }
  }

  // MARK: - Session Observation

  /// Returns an AsyncStream that emits session updates matching the filter.
  /// Immediately emits the current session if it exists and matches the filter.
  func sessionUpdates(
    for callId: UUID,
    where filter: @escaping @Sendable (CallSession) -> Bool = { _ in true }
  ) -> AsyncStream<CallSession> {
    AsyncStream { continuation in
      let observerId = UUID()

      // Emit current session if it matches
      if let session = sessions[callId], filter(session) {
        continuation.yield(session)
      }

      // Register observer
      if sessionObservers[callId] == nil {
        sessionObservers[callId] = [:]
      }
      sessionObservers[callId]?[observerId] = SessionObserver(
        continuation: continuation,
        filter: filter
      )

      continuation.onTermination = { @Sendable [weak self] _ in
        guard let self else { return }
        Task { await self.removeSessionObserver(callId: callId, observerId: observerId) }
      }
    }
  }

  private func removeSessionObserver(callId: UUID, observerId: UUID) {
    sessionObservers[callId]?[observerId] = nil
    if sessionObservers[callId]?.isEmpty == true {
      sessionObservers[callId] = nil
    }
  }
}
