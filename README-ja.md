# Spotify Lyrics Proxy

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**Spotify の再生状態を読み取り、システム通知とメディアカードに同期した歌詞を表示します — root 化不要。**

[English](README.md) · [简体中文](README-zh.md) · **日本語**

[クイックスタート](#クイックスタート) · [機能](#機能) · [アーキテクチャ](#アーキテクチャ) · [免責事項](#免責事項)

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

本アプリは Spotify Android SDK を使用して Spotify に接続するため、事前にアプリの登録が必要です。

1. [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) にアクセスし、Spotify アカウントでログイン
2. **Create App** をクリック
3. **App name** と **App description** を任意に入力（例："Spotify Lyrics Proxy"）
4. **Redirect URIs** に以下を追加：
   ```
   spotifylyricsproxy://callback
   ```
5. **Web API** にチェック（デフォルトのままで OK）
6. 利用規約に同意し **Save** をクリック
7. アプリ詳細ページから **Client ID**（32文字の16進数文字列）をコピー

> Android パッケージ名や SHA フィンガープリントの登録は不要です。このアプリは Spotify Auth SDK を使用するため、Redirect URI の設定のみで動作します。

### ソースからビルド

リポジトリをクローン：

```bash
git clone https://github.com/your-username/spotify-lyrics-proxy.git
cd spotify-lyrics-proxy
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

### トラブルシューティング

| 症状 | 原因 | 対処 |
|------|------|------|
| ログに `MISSING_CLIENT_ID` | `local.properties` 未設定 | `spotify.client.id` を設定 |
| Gradle ビルドで JDK エラー | JDK 17+ 未インストール | JDK 17 をインストールし `JAVA_HOME` を設定 |
| "Spotify not installed" | Spotify App 未インストール | Play Store からインストール |
| 接続後にすぐ閉じる | Spotify 未ログイン | Spotify App でログイン |
| 通知に歌詞が表示されない | 通知権限が拒否されている | システム設定で通知を許可 |
| 認証後にすぐ閉じる | Dashboard の Redirect URI が間違っている | `spotifylyricsproxy://callback` が設定されているか確認 |

ビルドしてインストール：

```bash
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 初回起動

1. アプリを開き、**Connect Spotify** をタップ
2. Spotify App で再生状態のアクセスを許可
3. 任意の曲を再生 — 通知に現在の歌詞行が表示されます

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
