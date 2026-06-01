import SwiftUI
import FoundationModelsSmoke

private let brand = Color.indigo

// MARK: Generation state

private enum GenerationState: Equatable {
    case idle
    case running(prompt: String)
    case success(prompt: String, answer: String)
    case failure(prompt: String, message: String)

    var isRunning: Bool {
        switch self {
        case .running: true
        default: false
        }
    }
}

// MARK: Sample prompts

private struct SamplePrompt: Identifiable {
    let id = UUID()
    let title: String
    let symbol: String
    let prompt: String
}

private let samplePrompts: [SamplePrompt] = [
    SamplePrompt(title: "Haiku", symbol: "leaf.fill", prompt: "Write a haiku about the sea."),
    SamplePrompt(title: "Dinner ideas", symbol: "fork.knife", prompt: "Give me three quick dinner ideas with chicken."),
    SamplePrompt(title: "Explain simply", symbol: "sparkles", prompt: "Explain on-device AI in one simple sentence."),
    SamplePrompt(title: "Name a café", symbol: "cup.and.saucer.fill", prompt: "Suggest a cozy name for a new coffee shop."),
    SamplePrompt(title: "Space fact", symbol: "moon.stars.fill", prompt: "Tell me a surprising fact about space."),
]

// MARK: Root

struct ContentView: View {
    @State private var input = ""
    @State private var state: GenerationState = .idle
    @FocusState private var inputFocused: Bool

    var body: some View {
        ZStack(alignment: .bottom) {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    header
                    responseCard
                }
                .padding(.horizontal)
                .padding(.top)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollDismissesKeyboard(.interactively)
            inputDock
        }
        .background(Color(.systemBackground))
        .sensoryFeedback(.impact(weight: .light), trigger: state.isRunning)
    }

    // MARK: Content layer

    private var header: some View {
        VStack(alignment: .leading) {
            Text("On-device\nIntelligence")
                .font(.system(size: 42, weight: .bold, design: .rounded))
                .lineSpacing(1)
            Text("Apple Foundation Models, running locally through Koog — private, offline, no network.")
                .font(.callout)
                .foregroundStyle(.secondary)
        }
        .padding(.top)
    }

    private var responseCard: some View {
        VStack(alignment: .leading) {
            switch state {
            case .idle:
                emptyState
            case .running(let prompt):
                promptHeader(prompt)
                HStack {
                    ProgressView()
                    Text("Thinking on-device…")
                        .foregroundStyle(.secondary)
                }
            case .success(let prompt, let answer):
                promptHeader(prompt)
                Text(answer)
                    .font(.title3)
                    .textSelection(.enabled)
            case .failure(let prompt, let message):
                promptHeader(prompt)
                Label {
                    Text(message).foregroundStyle(.secondary)
                } icon: {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .foregroundStyle(.orange)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(Color(.secondarySystemBackground), in: .rect(cornerRadius: 28, style: .continuous))
    }

    private var emptyState: some View {
        VStack(alignment: .leading) {
            Image(systemName: "sparkles")
                .font(.largeTitle)
                .foregroundStyle(brand)
            Text("Ask anything")
                .font(.title2.weight(.bold))
            Text("Type a prompt or tap a suggestion. Every word is generated right here on your iPhone.")
                .font(.subheadline)
                .foregroundStyle(.secondary)
        }
    }

    private func promptHeader(_ prompt: String) -> some View {
        Text(prompt)
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(.secondary)
    }

    private var inputDock: some View {
        VStack(spacing: 12) {
            suggestionRow
            inputBar
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 8)
    }

    private var suggestionRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            GlassEffectContainer {
                HStack {
                    ForEach(samplePrompts) { sample in
                        Button {
                            run(sample.prompt)
                        } label: {
                            Label(sample.title, systemImage: sample.symbol)
                                .font(.subheadline.weight(.medium))
                        }
                        .buttonStyle(.glass)
                        .disabled(state.isRunning)
                    }
                }
                .padding(.horizontal, 4)
            }
        }
        .scrollClipDisabled()
    }

    private var inputBar: some View {
        GlassEffectContainer {
            HStack {
                HStack {
                    Image(systemName: "sparkles")
                        .foregroundStyle(brand)
                    TextField("Ask the on-device model…", text: $input, axis: .vertical)
                        .lineLimit(1...4)
                        .focused($inputFocused)
                        .submitLabel(.send)
                        .onSubmit(submit)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .glassEffect(.regular, in: .capsule)

                Button(action: submit) {
                    Image(systemName: "arrow.up")
                        .font(.headline)
                        .frame(width: 26, height: 26)
                }
                .buttonStyle(.glassProminent)
                .tint(brand)
                .disabled(!canSubmit)
            }
        }
    }

    // MARK: Actions

    private var canSubmit: Bool {
        !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !state.isRunning
    }

    private func submit() {
        let trimmed = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return
        }

        run(trimmed)
    }

    private func run(_ prompt: String) {
        guard !state.isRunning else {
            return
        }

        inputFocused = false
        input = ""

        withAnimation(.smooth) {
            state = .running(prompt: prompt)
        }

        Task {
            do {
                let answer = try await FoundationModelsSmoke.SmokeKt.runFoundationModelsSmokeTest(prompt: prompt)
                let trimmed = answer.trimmingCharacters(in: .whitespacesAndNewlines)

                withAnimation(.smooth) {
                    state = .success(
                        prompt: prompt,
                        answer: trimmed.isEmpty ? "(The model returned an empty response.)" : trimmed
                    )
                }
            } catch {
                withAnimation(.smooth) {
                    state = .failure(prompt: prompt, message: (error as NSError).localizedDescription)
                }
            }
        }
    }
}

#Preview {
    ContentView()
}
