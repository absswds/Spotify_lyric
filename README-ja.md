# Spotify Lyrics Proxy

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**Spotify の再生状態を読み取り、システム通知とメディアカードに同期した歌詞を表示します — root 化不要。**

**English** · [简体中文](README-zh.md) · [繁體中文](README-zh-TW.md) · [日本語](README-ja.md)

[クイックスタート](#クイックスタート) · [機能](#機能) · [アーキテクチャ](#アーキテクチャ) · [コンテンツソースとコンプライアンス](#コンテンツソースとコンプライアンス) · [免責事項](#免責事項)

---

## 更新履歴

- **2026-07** — プロジェクト開始。コアパイプライン完了：App Remote 接続、LRCLIB 歌詞取得、Room キャッシュ、MediaSession 表示、通知、プレイリスト事前キャッシュ、歌詞修正。
- **2026-07** — 手動歌詞インポート（SAFによる.lrcファイルインポート）、翻訳対象言語セレクター（中/英/日）、日本語インターフェースローカライズを追加。

---

## 概要

Spotify Lyrics Proxy は、Spotify の再生状態とサードパーティの歌詞ソースを橋渡しする Android アプリケーションです。オーディオを再生せず、Spotify APK を改変せず、Spotify のプライベート API を呼び出しません。

このアプリは Spotify App Remote を介して現在再生中のトラックを読み取り、LRCLIB（またはローカルキャッシュ）から同期歌詞を取得し、以下の場所に現在の歌詞行を表示します：

- システム通知（フォアグラウンドサービス）
- MediaSession メディアカード（ロック画面、コントロールセンター）

---

## 機能

### 再生同期

| 機能 | |
|------|-|
| 状態取得 | Spotify から現在のトラック、再生位置、再生状態を取得 |
| 制御転送 | 再生/一時停止/スキップ/シーク操作を Spotify に転送 |
| 自動検出 | トラック変更と再生状態の変化に応答 |

### 歌詞表示

| 機能 | |
|------|-|
| 通知 | フォアグラウンドサービス通知に現在の歌詞行を表示 |
| メディアカード | ロック画面とコントロールセンターに同期歌詞を表示 |
| カスタマイズ | フォントサイズ、太字、薄暗モード、テキスト配置を調整可能 |

### キャッシュ

| 機能 | |
|------|-|
| 自動キャッシュ | 初回再生時に LRCLIB から歌詞を取得し Room に保存 |
| プレイリスト事前キャッシュ | Wi-Fi/充電時に WorkManager がバックグラウンドで歌詞を事前キャッシュ |
| オフライン | キャッシュ済み歌詞はオフラインでも表示可能 |

### 歌詞管理

| 機能 | |
|------|-|
| 修正 | 誤った一致をマークし、候補を再検索 |
| オフセット | 歌詞のタイミングを前後に調整 |
| インポート | システムファイルピッカーでローカルの `.lrc` ファイルを読み込み |
| 手動優先 | 手動インポートした歌詞は常にLRCLIBより優先 |
| クリア | 歌詞キャッシュとアルバムアートキャッシュを削除 |

### 歌詞翻訳

| 機能 | |
|------|-|
| 自動検出 | 歌詞言語を自動検出しリアルタイムで翻訳 |
| 対象言語 | 翻訳対象を選択：中国語、英語、日本語 |
| ML Kit | オンデバイス翻訳エンジン、モデルダウンロード後はネットワーク不要 |

### UI

| 機能 | |
|------|-|
| デザイン | Material 3、アルバムアートに基づく動的テーマ |
| テーマ | システム/ライト/ダークモード |
| 言語 | 中国語、英語、日本語 |

---

## クイックスタート

### 前提条件

- **JDK 17** 以上
- **Android SDK**（API 26+）
- **Spotify App** がインストール済みで、アカウントにログイン済み
- [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) で取得した Client ID

### Client ID を取得する

本アプリは Spotify Android SDK を使用して Spotify に接続するため、Spotify Developer Dashboard でアプリの登録が必要です。

1. [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) にアクセスし、Spotify アカウントでログイン
2. **Create App** をクリック
3. 以下の通りフォームを記入：

   | フィールド | 内容 |
   |-----------|------|
   | **App name** | 任意の名前（例："Lyrics Card"）。自分用の表示名です |
   | **App description** | 例："Personal lyrics display app" |
   | **Website** | 空欄のまま |
   | **Redirect URIs** | 以下の URI を追加（**完全に一致**にする必要があります）：`spotifylyricsproxy://callback` |
   | **Android packages** | `com.example.spotifylyricsproxy` を追加 |
   | **Android SHA-1 fingerprint** | 使用する APK 署名証明書の SHA-1 フィンガープリントを追加 |
   | **iOS app bundles** | 空欄 |
   | **Which API/SDKs are you planning to use?** | **Android** を選択 |

   > **Redirect URI** は認可後に OAuth トークンを受け取るコールバック URL です。末尾のスラッシュや余分な空白を含めず、完全に一致させてください。

   > **Android パッケージ名と SHA-1** — Spotify Android SDK の公式ドキュメントでは、Dashboard のアプリ設定に両方を登録するよう案内されています。パッケージ名は `com.example.spotifylyricsproxy` です。デバッグ版では debug 署名証明書、公開リリースでは release 署名証明書の SHA-1 も登録してください。Spotify はこれらで Android アプリの身元を確認します。

4. ページ下部の **Save** をクリック
5. ページ上部の **Client ID** をコピー（32文字の16進数文字列、例：`81a57006ff4a4d5d96cb72f180aa4ab5`）

> Client ID はシークレットではありません（APK に埋め込まれます）が、公開リポジトリにはコミットしないでください。

### ソースからビルド

リポジトリをクローン：

```bash
git clone https://github.com/absswds/Spotify_lyric.git
cd Spotify_lyric
```

Client ID を設定：

```bash
cp local.properties.example local.properties
```

`local.properties` を開き、Dashboard からコピーした値を設定：

```properties
# Android SDK のパス
# Windows: C:\Users\<ユーザー名>\AppData\Local\Android\Sdk
# macOS:   ~/Library/Android/sdk
# Linux:   ~/Android/Sdk
sdk.dir=C\:\\Users\\YourUser\\AppData\\Local\\Android\\Sdk

spotify.client.id=あなたの_SPOTIFY_CLIENT_ID
```

ビルドしてインストール：

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

> **Android Studio** を使用している場合は、プロジェクトを開くだけで OK です。`local.properties` は SDK 設定から自動生成されます。

### 初回起動

1. アプリを開く
2. **Connect Spotify** をタップ — Spotify App が開き、認証画面が表示されます
3. 再生状態の**アクセスを許可**
4. Spotify で任意の曲を再生 — 通知に現在の歌詞行が表示されます

> **Android 13+** では、**通知権限**の許可が必要です。許可しない場合、歌詞は通知バーに表示されません。

### バックグラウンドでアプリを維持する

このアプリはフォアグラウンドサービスを使用して通知に歌詞を表示します。一部の Android システム（特に OPPO ColorOS、Xiaomi MIUI、Huawei HarmonyOS、vivo OriginOS）は省電力のためにバックグラウンドアプリを積極的に停止します。通知の歌詞がしばらく後に消える場合は、手動でバックグラウンド実行を許可する必要があります：

1. **バッテリー最適化を無効にする** — 設定 → バッテリー → バッテリー最適化 → このアプリを探す → 「最適化しない」を選択
2. **自動起動を有効にする**（対応している場合）— 設定 → アプリ管理 → このアプリを探す → 「自動起動」を有効にする
3. **ロック** — 最近のタスクでアプリのカードを下にスワイプしてロックする（システムによる停止防止）

> 歌詞画面が空白になった場合（Spotify ネイティブプレーヤーが前面に出た場合）、最近のタスクからアプリをスワイプして閉じ、再度開くと復元します。

> 以上を設定しない場合、システムがフォアグラウンドサービスを停止し、歌詞の更新が止まります。

### トラブルシューティング

| 症状 | 原因 | 対処 |
|------|------|------|
| ログに `MISSING_CLIENT_ID` | `local.properties` 未設定 | `spotify.client.id` を設定 |
| Gradle ビルドで JDK エラー | JDK 17+ 未インストール | JDK 17 をインストールし `JAVA_HOME` を設定 |
| "Spotify not installed" | Spotify App 未インストール | Play Store からインストール |
| 接続後にすぐ閉じる | Spotify 未ログイン | Spotify App でログイン |
| 通知に歌詞が表示されない | 通知権限が拒否されている | システム設定で通知を許可 |
| 認証後にすぐ閉じる | Dashboard の Redirect URI が間違っている | `spotifylyricsproxy://callback` が設定されているか確認 |
| 通知の歌詞がしばらく後に消える | システムがバックグラウンドサービスを停止した | バッテリー最適化を無効にし、自動起動を有効にし、最近のタスクでロック（[バックグラウンドでアプリを維持する](#バックグラウンドでアプリを維持する)を参照） |
| 歌詞画面が空白／プレーヤーがフォーカスを失う | アプリがバックグラウンドに入り、Spotify UI が前面に出た | 最近のタスクからアプリをスワイプして閉じ、再度開く。プレーヤーが再接続され、歌詞が表示されます。 |

---

## アーキテクチャ

```
app/src/main/java/com/example/spotifylyricsproxy/
├── core/model/          データモデル
├── database/            Room データベースと DAO
├── lyrics/              歌詞の取得、解析、マッチング、同期
│   └── lrclib/         LRCLIB API 実装
├── mediasession/        MediaSession 制御
├── notification/        フォアグラウンドサービス通知
├── playback/clock/      再生位置推定
├── spotify/             Spotify 統合
│   ├── remote/         App Remote 接続と状態購読
│   └── webapi/         Web API（OAuth、プレイリスト）
├── ui/                  Compose 画面
│   ├── cache/          キャッシュ管理
│   ├── playback/       再生画面と ViewModel
│   ├── playlist/       プレイリスト事前キャッシュ
│   ├── precache/       事前キャッシュ管理
│   ├── settings/       設定画面
│   └── theme/          テーマとロケール設定
└── worker/              WorkManager バックグラウンドタスク
```

### データフロー

```
Spotify PlayerState
  → SpotifyRemoteRepository
  → LyricsRepository
  → Room cache / LRCLIB
  → LrcParser
  → PlaybackClock
  → LyricSyncEngine
  → Notification / MediaSession / UI
```

### 技術スタック

| コンポーネント | |
|--------------|-|
| UI | Jetpack Compose + Material 3 |
| アーキテクチャ | MVVM + Repository Pattern |
| データベース | Room（SQLite） |
| ネットワーク | Retrofit + OkHttp |
| 非同期 | Kotlin Coroutines + Flow |
| バックグラウンド | WorkManager |
| 画像読み込み | Coil |
| 歌詞翻訳 | ML Kit（デバイス上、オフライン） |
| Spotify 統合 | Spotify Android SDK（App Remote + Auth） |

---

## コンテンツソースとコンプライアンス

このリポジトリの [Apache-2.0](LICENSE) ライセンスは、本プロジェクトのコード、設定、文書だけを対象とします。歌詞、翻訳歌詞、アルバムアート、Spotify コンテンツ、第三者サービスの metadata を再配布、ミラー、学習、商用利用する権利は**付与しません**。

- Spotify 連携では [Spotify Developer Terms](https://developer.spotify.com/terms) と [Design Guidelines](https://developer.spotify.com/documentation/design) に従い、Spotify の承認・提携を示唆してはいけません。
- LRCLIB、NetEase Cloud Music、QQ Music は任意のオンデバイス歌詞ソースです。各サービスの利用規約、著作権規則、適用法は引き続き適用されます。エンドポイントにアクセスできることは、公開配布、プロキシ、ミラー、バルクダウンロード、公開 API の許可を意味しません。
- 歌詞コーパス、アートワークのアーカイブ、再生履歴、token、鍵、ローカルキャッシュをリポジトリや公開リリースに含めてはいけません。APK 配布、ストア公開、有償化、バックエンド機能の前に、各サービスの最新規約と法的要件を確認してください。
- NetEase adapter のプロトコル実装は [Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper)（Apache-2.0）を参考にしました。コードや歌詞データは同梱・複製していません。

詳細な境界、依存関係の帰属、公開前チェックリストは [Attribution, Content Sources, and Compliance](docs/ATTRIBUTION_AND_COMPLIANCE.md) を参照してください。

---

## 免責事項

**このプロジェクトは Spotify AB とは提携しておらず、Spotify 公式製品ではありません。**

- 本アプリはオーディオを再生せず、Spotify プレイヤーを代替しません
- すべてのオーディオ再生は Spotify App が処理します
- 本アプリは再生状態の読み取りと歌詞表示のみを行います
- 歌詞は LRCLIB（サードパーティ）またはユーザー提供のローカルファイルから取得されます
- 本アプリは Spotify のプライベート API を呼び出さず、Spotify APK を改変しません
- 本アプリはユーザーの再生履歴を収集、保存、送信しません
- 利用者は Spotify Developer Terms および LRCLIB 利用規約に従う必要があります

---

## コントリビューション

1. リポジトリを Fork
2. フィーチャーブランチを作成（`git checkout -b feature/amazing-feature`）
3. 変更をコミット（`git commit -m 'Add amazing feature'`）
4. ブランチにプッシュ（`git push origin feature/amazing-feature`）
5. Pull Request を作成

---

## ライセンス

このプロジェクトは [Apache License 2.0](LICENSE) の下でライセンスされています。
