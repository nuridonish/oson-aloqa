import SwiftUI

struct RegisterView: View {
    @State private var name = ""
    @State private var phone = ""
    @State private var country: Country = Countries.all.first!
    @State private var showPicker = false
    @State private var error = ""

    private var e164: String? { Countries.normalize(national: phone, code: country.code) }

    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "message.circle.fill")
                .font(.system(size: 96))
                .foregroundStyle(Color.teal)
                .padding(.top, 32)

            Text("Осон Алоқа")
                .font(.largeTitle.bold())

            Text("Паёмҳо ҳамчун SMS аз ҳисоби симкарта фиристода мешаванд. Интернет лозим нест.")
                .font(.footnote)
                .foregroundStyle(.secondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)

            Form {
                Section("Регистратсия") {
                    TextField("Номи шумо", text: $name)
                    Button {
                        showPicker = true
                    } label: {
                        HStack {
                            Text("Кишвар")
                            Spacer()
                            Text("\(country.flag) +\(country.code)").foregroundStyle(.secondary)
                        }
                    }
                    TextField("Рақами телефон", text: $phone)
                        .keyboardType(.phonePad)
                    Text(e164 ?? "Рақами пурра бо формати байналмилалӣ")
                        .foregroundStyle(e164 == nil ? .secondary : .teal)
                }

                Section {
                    Button {
                        if name.trimmingCharacters(in: .whitespaces).isEmpty {
                            error = "Номро нависед"
                        } else if let e = e164 {
                            error = ""
                            Store.shared.profile = Profile(name: name.trimmingCharacters(in: .whitespaces), e164: e)
                        } else {
                            error = "Рақами нодуруст"
                        }
                    } label: {
                        Text("Сабти ном").frame(maxWidth: .infinity).bold()
                    }
                    if !error.isEmpty {
                        Text(error).foregroundStyle(.red).font(.footnote)
                    }
                }
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

struct CountryPickerView: View {
    @State private var query = ""
    let onPick: (Country) -> Void

    private var items: [Country] { Countries.search(query) }

    var body: some View {
        NavigationStack {
            List(items, id: \.code) { c in
                Button {
                    onPick(c)
                } label: {
                    HStack {
                        Text(c.flag)
                        Text(c.name)
                        Spacer()
                        Text("+\(c.code)").foregroundStyle(.secondary)
                    }
                }
            }
            .searchable(text: $query, prompt: "Ҷустуҷӯ…")
            .navigationTitle("Интихоби кишвар")
        }
    }
}
