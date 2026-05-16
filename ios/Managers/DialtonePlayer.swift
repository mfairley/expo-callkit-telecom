import AVFoundation
import os

/// Plays the dialtone sound during outgoing call connection.
///
/// Reads the dialtone filename from Info.plist (ExpoCallKitTelecomDefaultDialtone) and plays
/// it in a loop until stopped.
final class DialtonePlayer {
  static let shared = DialtonePlayer()

  private var player: AVAudioPlayer?
  private let dialtoneFilename: String?
  private var fadeTimer: Timer?

  /// Delay before starting playback to let audio session settle (in seconds)
  private let startDelay: TimeInterval = 0.05
  /// Duration of volume fade-in (in seconds)
  private let fadeInDuration: TimeInterval = 0.1

  private init() {
    dialtoneFilename =
      Bundle.main.object(
        forInfoDictionaryKey: "ExpoCallKitTelecomDefaultDialtone"
      ) as? String
  }

  /// Whether a dialtone is configured in Info.plist.
  var hasDialtone: Bool {
    dialtoneFilename != nil
  }

  /// Starts playing the dialtone sound in a loop.
  ///
  /// Does nothing if no dialtone is configured or if already playing.
  func play() {
    guard let filename = dialtoneFilename else {
      Log.call.debug("No dialtone configured, skipping playback")
      return
    }

    guard player == nil else {
      Log.call.debug("Dialtone already playing")
      return
    }

    guard
      let url = Bundle.main.url(forResource: filename, withExtension: nil)
        ?? Bundle.main.url(
          forResource: (filename as NSString).deletingPathExtension,
          withExtension: (filename as NSString).pathExtension
        )
    else {
      Log.call.error("Dialtone sound file not found: \(filename)")
      return
    }

    // Ensure audio session is active before playing
    guard AudioManager.shared.isActive else {
      Log.call.debug("Audio session not active, skipping dialtone")
      return
    }

    do {
      player = try AVAudioPlayer(contentsOf: url)
      player?.numberOfLoops = -1  // Loop indefinitely
      player?.volume = 0  // Start silent for fade-in

      // Brief delay to let audio session settle, then play with fade-in
      DispatchQueue.main.asyncAfter(deadline: .now() + startDelay) { [weak self] in
        guard let self = self, let player = self.player else { return }

        player.play()
        self.fadeIn()
        Log.call.debug("Started playing dialtone: \(filename)")
      }
    } catch {
      Log.call.error("Failed to play dialtone: \(error.localizedDescription)")
      player = nil
    }
  }

  /// Fades in the volume from 0 to 1 over fadeInDuration.
  private func fadeIn() {
    fadeTimer?.invalidate()

    let steps = 10
    let stepDuration = fadeInDuration / Double(steps)
    let volumeStep: Float = 1.0 / Float(steps)
    var currentStep = 0

    fadeTimer = Timer.scheduledTimer(withTimeInterval: stepDuration, repeats: true) {
      [weak self] timer in
      guard let self = self, let player = self.player else {
        timer.invalidate()
        return
      }

      currentStep += 1
      player.volume = volumeStep * Float(currentStep)

      if currentStep >= steps {
        timer.invalidate()
        self.fadeTimer = nil
      }
    }
  }

  /// Stops playing the dialtone sound.
  func stop() {
    fadeTimer?.invalidate()
    fadeTimer = nil

    guard player != nil else {
      return
    }

    player?.stop()
    player = nil
    Log.call.debug("Stopped playing dialtone")
  }

  /// Whether the dialtone is currently playing.
  var isPlaying: Bool {
    player?.isPlaying ?? false
  }
}
