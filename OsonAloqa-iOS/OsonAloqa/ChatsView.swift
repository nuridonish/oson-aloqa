import SwiftUI

struct ChatsView: View {
    @StateObject private var store = Store.shared
    @State private var showAdd = false
    @State private var showProfile = false

    var body: some View {
        NavigationStack {
            Group {
                if store.chats.isEmpty {
                    ContentUnavailableView(
                        "Ҳанӯз чат нест",
                        systemImage: "bubble.left.and.bubble.right",
                        description: Text("Тугмаи + -ро пахш кунед")
                    )
                } else {
                    List(store.chats, id: \.self) { address in
                        NavigationLink {
                            ChatView(address: address)
                        } label: {
                            HStack {
                                Circle()
                                    .fill(Color.teal)
                                    .frame(width: 44, height: 44)
                                    .overlay(Text(String(address.suffix(1))).foregroundStyle(.white).bold())
                                VStack(alignment: .leading) {
                                    Text(address).bold()
                                    Text("SMS • аз ҳисоби симкарта").font(.footnote).foregroundStyle(.secondary)
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("Чатҳо")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showProfile = true
                    } label: {
                        Image(systemName: "person.circle")
                    }
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showAdd = true
                    } label: {
                        Image(systemName: "plus.circle.fill")
                    }
                }
            }
            .sheet(isPresented: $showAdd) {
                AddChatView()
            }
            .alert("Профил", isPresented: $showProfile) {
                Button("Баромадан", role: .destructive) {
                    store.logout()
                }
                Button("Бекор", role: .cancel) {}
            } message: {
                Text("\(store.profile?.name ?? "")\n\(store.profile?.e164 ?? "")")
            }
        }
    }
}

struct AddChatView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var country: Country = Countries.all.first!
    @State private var phone = ""
    @State private var showPicker = false

    var body: some View {
        NavigationStack {
            Form {
                Section("Рақами қабулкунанда") {
                    Button {
                        showPicker = true
                    } label: {
                        HStack {
                            Text("Кишвар")
                            Spacer()
                            Text("\(country.flag) +\(country.code)").foregroundStyle(.secondary)
                        }
                    }
                    TextField("Рақами телефон", text: $phone).keyboardType(.phonePad)
                }
                Section {
                    Button("Кушодани чат") {
                        let e164 = phone.hasPrefix("+")
                            ? String(phone.filter { $0.isNumber || $0 == "+" })
                            : Countries.normalize(national: phone, code: country.code)
                        if let e = e164, e.count >= 8 {
                            Store.shared.addChat(e)
                            dismiss()
                        }
                    }
                }
            }
            .navigationTitle("Чати нав")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Бекор") { dismiss() }
                }
            }
            .sheet(isPresented: $showPicker) {
                CountryPickerView { c in
                    country = c
                    showPicker = false
                }
            }
        }
    }
}
