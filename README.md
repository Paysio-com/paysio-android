# Paysio Mobile SDK for Android

Take a card or a US bank account inside your app, run 3-D Secure, and complete a Paysio
checkout session without leaving the app. Card numbers go straight from the VGS Compose
fields to the VGS vault over TLS; your servers and Paysio only ever see aliases.

- minSdk 23, compileSdk 37, Kotlin 2.3, Jetpack Compose (Material3).
- Depends on `com.verygoodsecurity:vgscollect:2.1.0` (Compose widgets), OkHttp 5, coroutines,
  kotlinx.serialization.
- Your app needs compileSdk 37, Kotlin 2.3 or newer and Compose BOM 2026.09.00 or newer.
  VGS Collect 2.1.0 is built against them and leaves Compose to the host app; an older
  Compose compiles but crashes with `NoSuchMethodError: rememberSaveable` when the card
  fields appear.
- Same API shape as `@paysio/react-native` and the `Paysio` Swift package.

## Quickstart

### 1. Install

```kotlin
dependencies {
    implementation("com.paysio:paysio-android:0.1.0")
}
```

Maven Central. If your build minifies, keep the VGS rule
`-keep public class com.google.android.material** { *; }` (shipped as a consumer rule).

### 2. Create a checkout session on your server

Never put a secret key in the app. On your server:

```sh
curl -X POST https://api.paysio.com/v1/checkout-sessions \
  -H "Authorization: Bearer sk_live_..." \
  -H "Content-Type: application/json" \
  -d '{"line_items":[{"product_id":"prod_...","quantity":1}],"customer_email":"a@b.co"}'
```

A line item is either `{ "product_id": "...", "quantity": 1 }` or an ad-hoc
`{ "name": "...", "amount": 1999, "currency": "USD", "quantity": 1 }`. Hand the returned
`slug` (`api-...`) to the app. A payment link slug works too. A session is single-use:
after one successful charge it answers 404 (`session_expired`); create a new one.

### 3. Render the sheet

```kotlin
import com.paysio.sdk.ui.PaysioPaymentSheet

setContent {
    PaysioPaymentSheet(
        sessionSlug = slug,
        onSuccess = { result -> Log.i("pay", "${result.transactionId} ${result.orderLabel}") },
        onError = { error -> Log.w("pay", "${error.code.wire}: ${error.message}") },
    )
}
```

The sheet adds `FLAG_SECURE` to the window while it is shown (`secureScreen = false` to
opt out). Custom layout: use `PaysioCardFields`, `PaysioBankFields`, `PaysioBillingAddress`,
`PaymentMethodPicker` and `ContactFields` yourself, then call
`PaysioPay(client, session, WebViewThreeDsHost(activity)).pay(...)` with the alias from
`PaysioCardFieldsController.tokenize()`.

### 4. Handle the result

`onSuccess` receives a `PaysioPayResult` with `status == PENDING_SETTLEMENT`,
`transactionId`, `orderLabel` and `confirmationToken`. Confirm fulfilment from your
webhook, as on the web. `onError` receives a `PaysioError` with one of the codes:
`invalid_fields`, `billing_address_required`, `contact_required`, `three_ds_failed`,
`three_ds_cancelled`, `three_ds_timeout`, `declined` (with `declineCode`; the declined
transaction row exists, no money moved), `session_expired` (the session is inactive,
expired or already paid: create a new one), `session_invalid`, `rate_limited`, `network`,
`unknown` (with the `idempotencyKey`; retrying with it never double charges),
`vault_unavailable`, `not_configured`.

### 5. Test cards (sandbox)

| Card | Scenario |
|---|---|
| 4111 1111 1111 1111, 12/30, 123 | Plain purchase (with 3DS on, the sandbox ACS challenges this card) |
| 4147 4630 1111 0134 | 3DS frictionless |
| 4016 3600 0000 0493 | 3DS challenge (choose approve or deny) |
| Routing 490000018, account 24413815 | Bank account (NMI sandbox test account; any other account is refused with decline code 300) |

Sandbox sessions relax the Luhn check because the 3DS test cards are not Luhn-valid.
A 3DS challenge needs a public API host: VGS rejects a redirect URL on a local address.

### 6. Go live

Create sessions with your live secret key. Live sessions use the live VGS vault
automatically (`vaultConfig` in the session). Keep the SDK up to date.

## Google Play rules

Physical goods and real-world services may be paid with card entry in the app (Play
Payments policy). Digital goods consumed in the app must use Play Billing. Do not use this
SDK for digital goods.

## 3-D Secure

When the workspace has 3DS on, the SDK fingerprints the device in a hidden `WebView`
(3 seconds) and, if the issuer asks, shows a full-screen white "Verify your card" dialog
with the challenge. JavaScript is enabled on those two web views only; they allow https
navigation only and are destroyed afterwards. Cancelling reports `three_ds_cancelled` and
charges nothing.

## Theming

`PaysioTheme(scheme = AUTO | LIGHT | DARK, colors, darkColors, fontFamily, radius)` with the
tokens `primary`, `foreground`, `mutedForeground`, `card`, `background`, `secondary`,
`border`, `input`, `rowHover`, `ring`, `destructive`. Wrap custom layouts in
`PaysioThemeProvider(theme) { ... }` or pass `theme` to the sheet.

## Known differences from the web picker

- The CVC is not masked, as on the hosted checkout: `VgsCvcTextField` fixes its visual
  transformation to none. The sheet sets `FLAG_SECURE`, so screenshots and screen
  recordings of it come out black (`secureScreen = false` turns this off, for tests only).
- Remote bank logos are not shown; banks always use the bank badge.

## PCI and privacy

Card data is entered into VGS-provided fields and transmitted directly to VGS's PCI DSS
Level 1 environment. It is not transmitted to or stored by your servers or Paysio's. The
app remains the point of entry: keep the SDK current and keep `FLAG_SECURE` on.

Analytics: VGS collects integration analytics by default. Opt out with
`controller.vault.collect.setAnalyticsEnabled(false)` (VGS `VGSCollect.setAnalyticsEnabled`).

## Example app

`example/` is a minimal Compose app: API base presets, "New sandbox session" (calls
`POST /api/dev/mobile-session`, which answers `201 { slug, url, sandbox, expires_at }` and
exists only when the backend runs with `MOBILE_SDK_DEV_WORKSPACE_ID`), the test card list
and the sheet.

```sh
cd sdks/android
./gradlew :paysio:assembleRelease
./gradlew :example:installDebug   # on an emulator or a phone
```

Maestro UI flows for the test scenarios live in `example/maestro/`
(`maestro test -e SLUG=<payment link or session slug> example/maestro/d1_plain_purchase.yaml`).

## Publishing

Maven Central through the Sonatype Central Portal: verify the `com.paysio` namespace (DNS
TXT on paysio.com), set `centralUsername` / `centralPassword` and the `signing.*`
properties in `~/.gradle/gradle.properties`, then `./gradlew :paysio:publish`.

## Changelog

- **0.1.0**: first release. Card and US bank account entry with VGS Compose fields,
  3-D Secure, the drop-in sheet, street address suggestions (no Google key in the app),
  the focused field kept above the keyboard in step with its animation, searchable
  country and state sheets, and the Paysio card brand marks.

## License

MIT.
