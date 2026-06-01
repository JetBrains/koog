import SwiftUI
import FoundationModelsSmoke

enum ContentViewState {
    case idle
    case running
    case success(String)
    case failure(Error)

    var displayText: String {
        switch self {
        case .idle:
            "Tap to generate response using the on-device foundation model"
        case .running:
            "Running…"
        case .success(let message):
            message
        case .failure(let error):
            String(describing: error)
        }
    }

    var isRunning: Bool {
        if case .running = self {
            return true
        }
        return false
    }
}

struct ContentView: View {
    @State private var state = ContentViewState.idle

    var body: some View {
        VStack {
            Text(state.displayText)
                .multilineTextAlignment(.center)
                .padding()

            Button(state.isRunning ? "Running…" : "Generate response") {
                guard !state.isRunning else {
                    return
                }

                state = .running

                Task {
                    do {
                        let result = try await FoundationModelsSmoke.SmokeKt.runFoundationModelsSmokeTest(
                            prompt: "Say hello in exactly three words"
                        )
                        state = .success(result)
                    } catch {
                        state = .failure(error)
                    }
                }
            }
            .disabled(state.isRunning)
        }
        .padding()
    }
}
