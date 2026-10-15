import SwiftUI
import MessageUI

struct ChatView: View {
    let address: String
    @State private var text = ""
    @State private var showComposer = false

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Image(systemName: "info.circle")
                    .foregroundStyle(.orange)
                Text("Дар iOS паёмҳои воридшуда дар барномаи асосии Messages нишон дода мешаванд (маҳдудияти Apple). Барои фиристодан тугмаи поёнро пахш кунед ва SMS-ро тасдиқ намоед.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .padding(12)
            .background(Color.yellow.opacity(0.15))

            Spacer()

            Image(systemName: "bubble.left.and.bubble.right")
                .font(.system(size: 64))
                .foregroundStyle(.teal)
            Text(address)
                .font(.title2.bold())
            Text("SMS • аз ҳисоби симкарта")
                .font(.footnote)
                .foregroundStyle(.secondary)

            Spacer()

            HStack(spacing: 12) {
                TextField("Паём нависед…", text: $text, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(1...4)

                Button {
                    showComposer = true
                } label: {
                    Image(systemName: "arrow.up.circle.fill")
                        .font(.system(size: 44))
                        .foregroundStyle(.teal)
                }
                .disabled(text.trimmingCharacters(in: .whitespaces).isEmpty || !MFMessageComposeViewController.canSendText())
            }
            .padding(12)

            Button {
                call()
            } label: {
                Label("Занг задан (SIM)", systemImage: "phone.fill")
                    .frame(maxWidth: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .padding(.horizontal, 12)
            .padding(.bottom, 12)
        }
        .navigationTitle(address)
        .navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showComposer) {
            MessageComposer(recipients: [address], body: text)
                .onDisappear { text = "" }
        }
    }

    private func call() {
        let digits = address.filter { $0.isNumber || $0 == "+" }
        if let url = URL(string: "tel:\(digits)") {
            UIApplication.shared.open(url)
        }
    }
}

struct MessageComposer: UIViewControllerRepresentable {
    let recipients: [String]
    let body: String

    func makeUIViewController(context: Context) -> MFMessageComposeViewController {
        let vc = MFMessageComposeViewController()
        vc.recipients = recipients
        vc.body = body
        vc.messageComposeDelegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: MFMessageComposeViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator() }

    final class Coordinator: NSObject, MFMessageComposeViewControllerDelegate {
        func messageComposeViewController(_ controller: MFMessageComposeViewController,
                                          didFinishWith result: MessageComposeResult) {
            controller.dismiss(animated: true)
        }
    }
}
