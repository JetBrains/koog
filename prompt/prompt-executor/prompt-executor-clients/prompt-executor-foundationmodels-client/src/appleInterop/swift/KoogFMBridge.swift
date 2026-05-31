import Foundation
import FoundationModels

@available(iOS 26.0, *)
@objc public final class KoogFMBridge: NSObject {
    /// Returns nil when the on-device model is available, else a human-readable reason.
    @objc public func availabilityReason() -> String? {
        switch SystemLanguageModel.default.availability {
        case .available:
            return nil
        case .unavailable(let reason):
            return "\(reason)"
        }
    }

    /// Runs one-shot generation. Invokes completion with (content, errorDetail);
    /// exactly one argument is non-nil.
    @objc public func respond(
        _ prompt: String,
        instructions: String?,
        completion: @escaping @Sendable (String?, String?) -> Void
    ) {
        Task {
            do {
                let session = instructions.map { LanguageModelSession(instructions: $0) }
                    ?? LanguageModelSession()
                let response = try await session.respond(to: prompt)
                completion(response.content, nil)
            } catch {
                completion(nil, "\(error)")
            }
        }
    }
}
