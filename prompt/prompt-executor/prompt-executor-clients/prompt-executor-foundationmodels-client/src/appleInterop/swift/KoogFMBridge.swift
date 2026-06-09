import Foundation
import FoundationModels

/// Un-gated `@objc` surface over the iOS-26-only FoundationModels framework.
///
/// The class itself carries no `@available` attribute and the framework is weak-linked
/// (see the `.def` linkerOpts), so loading and constructing it is safe on any OS the
/// consuming app supports. Every entry point gates internally with `#available` and
/// reports pre-26 systems via the stable `"osVersionTooOld"` token instead of crashing.
@objc public final class KoogFMBridge: NSObject {
    /// Returns nil when the on-device model is available, else a stable token consumed
    /// by the Kotlin side (`foundationModelsAvailabilityFromToken`):
    /// `"deviceNotEligible"`, `"appleIntelligenceNotEnabled"`, `"modelNotReady"`,
    /// `"osVersionTooOld"`, or `"unknown:<detail>"` for future framework cases.
    @objc public func availabilityToken() -> String? {
        guard #available(iOS 26.0, *) else {
            return "osVersionTooOld"
        }

        switch SystemLanguageModel.default.availability {
        case .available:
            return nil
        case .unavailable(.deviceNotEligible):
            return "deviceNotEligible"
        case .unavailable(.appleIntelligenceNotEnabled):
            return "appleIntelligenceNotEnabled"
        case .unavailable(.modelNotReady):
            return "modelNotReady"
        case .unavailable(let reason):
            return "unknown:\(reason)"
        }
    }

    /// Runs one-shot generation. Invokes completion with (content, errorDetail);
    /// exactly one argument is non-nil.
    @objc public func respond(
        _ prompt: String,
        instructions: String?,
        completion: @escaping @Sendable (String?, String?) -> Void
    ) {
        guard #available(iOS 26.0, *) else {
            completion(nil, "Foundation Models requires iOS 26 (osVersionTooOld)")
            return
        }

        Task {
            do {
                let session =
                    instructions.map { LanguageModelSession(instructions: $0) }
                    ?? LanguageModelSession()
                let response = try await session.respond(to: prompt)
                completion(response.content, nil)
            } catch {
                completion(nil, "\(error)")
            }
        }
    }
}
