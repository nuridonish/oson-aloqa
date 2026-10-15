import SwiftUI

@main
struct OsonAloqaApp: App {
    @StateObject private var store = Store.shared

    var body: some Scene {
        WindowGroup {
            if store.profile != nil {
                ChatsView()
            } else {
                RegisterView()
            }
        }
    }
}
