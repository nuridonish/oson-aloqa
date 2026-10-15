import Foundation
import Combine

struct Profile: Codable {
    var name: String
    var e164: String
}

final class Store: ObservableObject {
    static let shared = Store()

    @Published var profile: Profile? {
        didSet {
            if let p = profile, let d = try? JSONEncoder().encode(p) {
                UserDefaults.standard.set(d, forKey: "profile")
            } else {
                UserDefaults.standard.removeObject(forKey: "profile")
            }
        }
    }

    @Published var chats: [String] {
        didSet { UserDefaults.standard.set(chats, forKey: "chats") }
    }

    init() {
        if let d = UserDefaults.standard.data(forKey: "profile"),
           let p = try? JSONDecoder().decode(Profile.self, from: d) {
            profile = p
        }
        chats = UserDefaults.standard.stringArray(forKey: "chats") ?? []
    }

    func addChat(_ address: String) {
        if !chats.contains(address) {
            chats.insert(address, at: 0)
        }
    }

    func logout() {
        profile = nil
        chats = []
    }
}
