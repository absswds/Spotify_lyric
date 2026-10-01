# Lyrics Card

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![API](https://img.shields.io/badge/API-26%2B-brightgreen.svg)](https://developer.android.com/about/versions/oreo)
[![Spotify](https://img.shields.io/badge/Spotify-Android%20Remote-green.svg)](https://developer.spotify.com/documentation/android)

**Android の Spotify 専用の歌詞アプリ。標準の Android メディアセッション（MediaSession）と MediaStyle 通知を使い、リアルタイム同期歌詞をシステムのライブメディア領域（メディア通知／コントロールセンターのカード、ロック画面、各メーカー ROM の「アイランド」／カプセル型ライブアクティビティ）に表示し、Apple Music／Lyricify 風の美しい歌詞プレーヤーも備えています。root 不要、Spotify の改変なし。**

[English](README.md) · [简体中文](README-zh.md) · [繁體中文](README-zh-TW.md) · **日本語**

> ⚠️ **著作権について**：歌詞の著作権は作詞者・出版社に帰属します。NetEase・QQ Music・Kugou は非公開の API で、その歌詞の利用やキャッシュは**著作権や利用規約上の問題になる可能性があります**。自分のスマホで聴きながら見るだけならリスクは一般に低いですが、**キャッシュした歌詞の共有・エクスポート・公開・商用利用はしないでください**。詳しくは下部のコンテンツソースとコンプライアンスを参照（法的助言ではありません）。

> 💳 **再生操作には Spotify Premium が必要です**：Spotify の公式ドキュメントでは、再生操作の API（一時停止・スキップ・シークなど）は [Premium ユーザーのみ有効](https://developer.spotify.com/documentation/web-api/reference/skip-users-playback-to-next-track)とされ、無料プランのスマホでは[スマートシャッフルが常にオン](https://support.spotify.com/us/article/shuffle-play/)で[スキップ回数も制限](https://support.spotify.com/us/article/your-premium-benefits/)されます。そのため無料アカウントでは本アプリの再生・スキップ・シーク・シャッフル・リピートのボタンが効かないことがありますが、歌詞の表示には影響しません。

[機能](#機能) · [クイックスタート](#クイックスタート) · [権限について](#権限について) · [更新履歴](#更新履歴) · [アーキテクチャ](#アーキテクチャ) · [クレジット](#クレジット) · [コンテンツソースとコンプライアンス](#コンテンツソースとコンプライアンス) · [免責事項](#免責事項)

---

## 機能

### システムのライブメディア領域

- 現在の歌詞行を、ネイティブの MediaStyle 通知、コントロールセンターのメディアカード、ロック画面、そしてメディアセッションを読み取るメーカー ROM の「アイランド」／カプセル型ライブアクティビティに表示します。
- Spotify が自分のセッションを最上位に戻したとき（曲の切り替え、再生再開、他デバイスからの再生の戻り、他アプリの音声再生後、Spotify の再起動後）、自動で最上位を取り戻します。

主に OPPO ColorOS でテストしており、ロック画面アイランド（锁屏岛）と流体雲（流体云、Fluid Cloud）で最もよく動作します。

<!-- TODO: 以下の画像を docs/images/ に置いてからコメントを外す：
![プレーヤー](docs/images/player.gif)
![ColorOS Fluid Cloud](docs/images/coloros-fluid-cloud.png)
![ロック画面とメディアカード](docs/images/lockscreen-media-card.png)
-->

> 作者の手元には限られた（ColorOS）端末しかなく、多くの ROM ではテストできません。興味がある方や、他メーカーの OS（MIUI/HyperOS、OriginOS、MagicOS、One UI など）で正しく動かない場合は、[Issue を作成](https://github.com/absswds/Spotify_lyric/issues)するか PR を送ってください。

### プレーヤー

- カバーから色を抽出したメッシュグラデーション背景。
- 実際の単語単位タイミング（TTML／YRC／QRC／KRC）がある場合は単語ごとのスイープと浮き上がり、ない場合は行全体をハイライト。
- 距離に応じたぼかしと減光、スプリングスクロール。
- イントロのカウントダウンドット、曲中の間奏ドット（6 秒以上の間隔）。
- デュエット行を左右に配置。各行の下に翻訳を表示。
- ぼかし付きで回転する時間表示。長いタイトル／アルバム名はマーキー表示。
- レイアウト：スマホ縦向き、横向き（Lyricify 風の左ブロック：カバー、タイトル、翻訳・メニューボタン、進捗、再生操作）、タブレット縦向き、分割画面。

### 歌詞ソース

| ソース | 説明 |
|--------|------|
| AMLL TTML DB | Spotify のトラック ID で完全一致。単語タイミング、デュエット |
| NetEase Cloud Music | YRC 単語タイミング + 翻訳 |
| QQ Music | QRC 単語タイミング、LRC にフォールバック |
| Kugou | KRC 単語タイミング |
| LRCLIB | デフォルトのソース |

- すべてのソースを並列検索し、十分な結果が揃えば早めに終了。
- 候補はタイトル／アーティスト／長さの一致度で採点し、ソース間でタイミングの一致をクロスチェックします。オンボーディングと設定で単語単位または行単位を優先でき、選択したタイミング形式を候補選択で優先します。
- 信頼度の低い一致は表示しません（同名の別アーティストの歌詞は、歌詞なしより悪いため）。
- 手動検索／修正、`.lrc` ファイルの手動インポート。

### オフセット

- 曲ごと・歌詞ソースごとに保存（ソースごとにタイミングを個別に調整）。
- 設定でグローバルオフセットも指定可能。

### キャッシュとオフライン

- LRCLIB、AMLL、手動歌詞はオフライン用にキャッシュ。
- 非公式ソース（NetEase、QQ Music、Kugou）は既定で有効です。オンボーディングに利用方法と責任の説明があり、オンボーディングまたは設定で無効にして LRCLIB と AMLL のみにできます。歌詞は既定ではメモリ上のみで表示され、オンボーディングガイドで同意した場合に端末へキャッシュします（設定で変更可能）。キャッシュは自分で見るためのもので、共有やエクスポートはしないでください。
- オフラインモード：App Remote に接続できないとき、[通知へのアクセス](#権限について)を通じて Spotify 自身の MediaSession を読み取ります。

### 他デバイスでの再生（Spotify Connect）

- 他デバイスで再生中、スマホ側の Spotify は古い一時停止状態を報告し続けることが多いため、Spotify Web API（`GET /v1/me/player`、スコープ `user-read-playback-state`、[追加の認可](#権限について)が 1 回必要）に追従します。
- 他デバイスで再生中は 5 秒ごとにポーリング。何も再生していないときは最大 2 分まで間隔を延ばします。
- メニューに「他のデバイスと同期中」と表示。
- トークンの有効期限は 1 時間で、アプリを開いたときに更新されます。

### 翻訳と繁体字／簡体字

- ML Kit によるオンデバイス翻訳。付属の翻訳（NetEase）は行ごとに表示。
- 中国語 UI では、繁体字／簡体字変換の方式をオンボーディングと設定で選択：歌詞をその場で変換するか、変換後のテキストを下に表示するか。

### バッテリー

- 一時停止中はウェイクロックを解放。10 分間一時停止が続くとフォアグラウンドサービスを停止し、Android に Spotify の停止も依頼します。
- Web API のポーリング間隔を延長。UI 側はバックグラウンドでポーリングを停止。

### その他

- オンボーディングガイド。
- 4 つの UI 言語（簡体字中国語、繁体字中国語、英語、日本語）とテーマ。

### 既知の制限

- ColorOS 固有：「Hans」が Spotify の一時停止から数秒後にアプリをフリーズさせることがあります。フリーズ中は再生再開を受け取れず、メディアカードのボタンにも反応しません。アプリを開くと復帰します。
- App Remote による再生操作（スキップなど）には Spotify Premium が必要です。
- Web API トークンは 1 時間ごとの更新が必要で、更新にはアプリを開く必要があります。

---

## クイックスタート

### 前提条件

- **JDK 17**
- **Android SDK**（compileSdk 35、minSdk 26）
- **Spotify アプリ**がインストール済みでログイン済み
- [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) で登録したアプリの Client ID

### Client ID の取得

1. [Spotify Developer Dashboard](https://developer.spotify.com/dashboard) を開き、Spotify アカウントでログイン。
2. **Create App** をクリックし、以下を入力：

   | 項目 | 入力内容 |
   |------|----------|
   | **App name** | 任意（例: "Lyrics Card"） |
   | **App description** | 例: "Personal lyrics display app" |
   | **Website** | 空欄 |
   | **Redirect URIs** | `spotifylyricsproxy://callback`（完全一致。末尾のスラッシュや空白は不可） |
   | **Android packages** | `com.example.spotifylyricsproxy` |
   | **Android SHA-1 fingerprint** | APK に署名する証明書の SHA-1（`./gradlew :app:signingReport`） |
   | **Which API/SDKs** | **Android** と **Web API** にチェック（Web API は他デバイス同期とプレイリストに使用） |

3. **Save** をクリックし、ページ上部の **Client ID** をコピー。

> Spotify はパッケージ名と署名 SHA-1 を検証します。別のマシンでビルドした debug APK は鍵が異なるため、上書きインストールは `INSTALL_FAILED_UPDATE_INCOMPATIBLE` で失敗し、Spotify も接続を拒否します。公開前には release 証明書の SHA-1 も登録してください。

> Client ID は APK に埋め込まれるため秘密情報ではありませんが、公開リポジトリにはコミットしないでください。

### ソースからビルド

```bash
git clone https://github.com/absswds/Spotify_lyric.git
cd Spotify_lyric
cp local.properties.example local.properties
```

`local.properties` を編集（Windows では `sdk.dir` にスラッシュを使用）：

```properties
sdk.dir=C:/Users/YourUser/AppData/Local/Android/Sdk
spotify.client.id=YOUR_SPOTIFY_CLIENT_ID
```

ビルドとインストール：

```bash
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

> `spotify.client.id` がなくてもビルドは成功しますが、`MISSING_CLIENT_ID` が埋め込まれ、App Remote と認可が何も言わずに失敗します。コマンドラインでビルドする前に Android Studio を閉じてください。

### 初回起動

1. アプリを開き、オンボーディングガイドに従って設定（[通知権限](#権限について)、[通知へのアクセス](#権限について)、繁体字／簡体字オプションなど）。
2. **Spotify に接続**をタップし、Spotify で[認可](#権限について)。
3. 任意の曲を再生すると、プレーヤー、通知、メディアカードに歌詞が表示されます。

各権限の用途は[権限について](#権限について)を参照してください。

### 権限について

| 権限／認可 | 用途 | 必須か |
|------------|------|--------|
| 通知権限（Android 13 以降） | 通知、メディアカード、ロック画面、カプセルに歌詞を表示 | 必須（Android 13 以降）。ないと通知に歌詞が表示されません |
| Spotify の認可（App Remote） | Spotify に接続し、現在の曲と再生位置の取得、再生操作を行う | 必須 |
| 通知へのアクセス | オフラインモード：App Remote に接続できないとき Spotify 自身のメディアセッション（曲、アートワーク、操作）を読み取る。通知の内容は読み取りません | 任意 |
| Spotify Web API の認可（`user-read-playback-state`） | 他デバイスでの再生（Spotify Connect）を同期。初めて他デバイスで再生するときに 1 回求められ、トークンは 1 時間有効でアプリを開くと更新 | 任意 |
| バッテリー最適化の除外 | システムにバックグラウンドで終了されにくくする。[バックグラウンドで動かし続ける](#バックグラウンドで動かし続けるメーカー製rom)を参照 | 任意（推奨） |
| Spotify の自動起動／関聯起動（システム設定） | 強制停止された Spotify を起動できるようにする。[バックグラウンドで動かし続ける](#バックグラウンドで動かし続けるメーカー製rom)を参照 | 任意、一部のメーカー ROM のみ |

### バックグラウンドで動かし続ける（メーカー製ROM）

多くのメーカー ROM（ColorOS、MIUI/HyperOS、OriginOS、MagicOS など）はバックグラウンドアプリを積極的に終了させます。しばらくすると歌詞が更新されなくなる場合：

1. **[バッテリー最適化を無効化](#権限について)**：設定 → バッテリー → 本アプリ → 最適化しない／バックグラウンド動作を完全に許可（名称は ROM により異なります）。
2. **[Spotify の自動起動／関聯起動（关联启动）を許可](#権限について)**：許可しないと強制停止された Spotify を起動できません。メーカーの設定画面は署名で保護されているため、アプリからはアプリ情報画面にしか移動できず、手動で有効にする必要があります。
3. **アプリをロック**：最近使ったアプリ画面で本アプリをロック。

> **ColorOS 固有**：上記をすべて行っても、ColorOS の「Hans」が Spotify の一時停止から 5〜20 秒後にアプリをフリーズさせることがあります。`adb logcat | grep OplusHans` で確認できます。アプリを開くと解除されます。

### FAQ／トラブルシューティング

| 症状 | 原因 | 対処 |
|------|------|------|
| ログに `MISSING_CLIENT_ID` | `local.properties` 未設定 | `spotify.client.id` を設定 |
| 認可直後に閉じる | Redirect URI の不一致 | Dashboard に `spotifylyricsproxy://callback` があるか確認 |
| Spotify が接続を拒否／`INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 署名 SHA-1 が Dashboard と不一致 | 現在の SHA-1 を登録するか、旧版をアンインストール |
| 通知に歌詞が出ない | [通知権限](#権限について)がない | システム設定で有効化 |
| しばらくすると歌詞が止まる | バックグラウンド終了（ColorOS では Hans によるフリーズも） | [バックグラウンドで動かし続ける](#バックグラウンドで動かし続けるメーカー製rom)を参照。アプリを開く |
| 他デバイス再生中に歌詞が動かない | [Web API 未認可](#権限について)、またはトークン期限切れ | アプリを開いて認可／更新 |
| スキップなどの操作が効かない | Premium アカウントではない | App Remote の再生操作には Premium が必要 |
| オフラインで歌詞が出ない | [通知へのアクセス](#権限について)がない、または未キャッシュの曲 | 通知へのアクセスを許可。オフラインではキャッシュ済みの歌詞のみ |
| 「信頼度が低い」と表示され歌詞が出ない | 十分に一致する候補がない | メニューから手動検索、または `.lrc` をインポート |

---

## 更新履歴

- **2026-07**：初期リリース。App Remote 接続、LRCLIB 歌詞、Room キャッシュ、MediaSession、フォアグラウンド通知、プレイリストの事前キャッシュ、歌詞修正。`.lrc` の手動インポート、翻訳先言語、日本語 UI。
- **2026-08**：オフラインモード（通知へのアクセスで Spotify の MediaSession を読み取り）。デフォルトのソースを LRCLIB に変更。Apple Music 風プレーヤー、単語単位の歌詞、オンボーディング、メディアカードの優先度。
- **2026-10**：初回ガイドを複数ページに分割（歌詞ソースの説明、単語単位／行単位の優先設定とデモ、キャッシュ設定）。非公式ソース（NetEase、QQ Music、Kugou）は既定で有効で、無効化も可能。キャッシュ関連画面を開く前に著作権の注意を表示。
  - 設定に単語単位／行単位の優先切替を追加。
  - 修正：モバイルデータを許可しない場合に前の曲の歌詞が残る、歌詞修正画面に候補が一つしか表示されない、ソース切替後にオフセットが残る、Spotify 認可エラー表示、Kugou の検索結果が早期キャンセルされる、複合クレジット行、単語単位歌詞の折り返し時に文字が二重表示される問題。

---

## アーキテクチャ

```
app/src/main/java/com/example/spotifylyricsproxy/
├── core/                設定とデータモデル
├── database/            Room データベース、DAO、エンティティ
├── lyrics/              検索、解析、照合、コンセンサス、翻訳
│   ├── amll/  lrclib/  netease/  qqmusic/   各歌詞ソース
├── mediasession/        MediaSession とメディアボタン
├── notification/        フォアグラウンドサービスと通知
├── playback/clock/      再生位置の推定
├── spotify/
│   ├── remote/          App Remote、システム MediaSession へのフォールバック、他デバイス同期
│   └── webapi/          OAuth トークン、Web API
├── ui/                  Compose UI（プレーヤー、ナビゲーション、オンボーディング、設定、プレイリスト、キャッシュ、テーマ）
├── util/                ネットワーク状態
└── worker/              プレイリスト歌詞の事前キャッシュ
```

- **1 つのパイプライン、2 つの利用者**：`LyricsForegroundService` が歌詞の同期（クロック、`updatePosition()`、曲切り替え時の検索）を担当し、通知とメディアセッションを発行します。`PlaybackViewModel` は同じ `LyricsRepository` を UI 用に読むだけで、同期は駆動しません。
- **再生状態**：`SpotifyRemoteRepository` が App Remote をラップし、接続できないときは Spotify のシステム MediaSession にフォールバック、他デバイス再生中は Web API に追従します。
- **歌詞フォーマット**：TTML、YRC、LRC などは同じテキストフィールドに保存され、`LrcParser` が内容から判別します。新しいフォーマットにデータベース変更は不要です。
- **メディアカードの優先度**：システムは直近に再生状態へ遷移したセッションを最上位に置きます。Spotify が曲を切り替えたり再開したりした約 1.2 秒後、`MediaSessionController` が歌詞セッションを一瞬一時停止にしてから再生に戻し、最上位に復帰させます。

### 技術スタック

| コンポーネント | |
|----------------|-|
| UI | Jetpack Compose + Material 3 |
| アーキテクチャ | MVVM + Repository |
| データベース | Room |
| ネットワーク | Retrofit + OkHttp + Gson |
| 非同期 | Kotlin Coroutines + Flow |
| バックグラウンド | フォアグラウンドサービス + WorkManager |
| 画像 | Coil |
| 翻訳 | ML Kit（オンデバイス翻訳と言語識別） |
| Spotify | Spotify Android SDK（App Remote + Auth）+ Web API |

---

## クレジット

このプロジェクトは以下のプロジェクトを利用・参考にしています。作者の皆さんに感謝します。

| プロジェクト | 用途 | ライセンス |
|---|---|---|
| [Lyricify-Lyrics-Helper](https://github.com/WXRIW/Lyricify-Lyrics-Helper) | NetEase・QQ Music・Kugou のリクエストパラメータと QRC/KRC の復号・解析。`QrcDecrypter.kt`、`QrcConverter.kt`、`KrcDecoder.kt` はここから移植し、各ファイルに出典を記載 | Apache-2.0 |
| [AMLL TTML DB](https://github.com/amll-dev/amll-ttml-db) | Spotify のトラック ID で単語タイミング付き TTML 歌詞を検索 | 投稿者自身の部分は CC0-1.0。歌詞テキストは権利者に帰属 |
| [LRCLIB](https://lrclib.net) | 既定の行同期歌詞ソース | 無料の公開 API |
| Apple Music、Lyricify | プレーヤーの見た目と動きのデザイン参考（カウントダウンの点、ぼかし、回転する時間表示など）。コードは使用していません | — |
| [Spotify Android SDK](https://developer.spotify.com/documentation/android) / [Web API](https://developer.spotify.com/documentation/web-api) | 再生状態の取得、再生操作、プレイリスト | Spotify 開発者規約 |
| [Google ML Kit](https://developers.google.com/ml-kit) | 端末上の言語識別と翻訳 | ML Kit 規約 |
| AndroidX / Jetpack Compose、Room、WorkManager、OkHttp、Retrofit、Gson、Coil | UI、データベース、ネットワークなど | 各自のオープンソースライセンス（主に Apache-2.0） |

詳細は [`docs/ATTRIBUTION_AND_COMPLIANCE.md`](docs/ATTRIBUTION_AND_COMPLIANCE.md) を参照してください。

## コンテンツソースとコンプライアンス

> プロジェクトの説明であり、**法的助言ではありません**。

本リポジトリの [Apache-2.0](LICENSE) ライセンスはコード、設定、ドキュメントのみを対象とします。歌詞（翻訳を含む）、アートワーク、メタデータは各権利者に帰属します。

| ソース | 性質 | キャッシュ方針 |
|--------|------|----------------|
| AMLL TTML DB | GitHub 上の公開コミュニティデータベース。貢献者のタイミング作業は CC0-1.0、歌詞テキストは権利者に帰属 | キャッシュする |
| LRCLIB | 公開の無料 API、キー不要 | キャッシュする |
| NetEase／QQ Music／Kugou | 文書化されていない非公開エンドポイント。アクセスできることは許諾を意味しない | ローカルキャッシュに同意しない限りセッション中のメモリのみ。プロキシ、ミラー、エクスポートは一切しない |
| Musixmatch | 不使用：全文／同期歌詞には有償の商用ライセンスが必要。無料枠は歌詞の 30% のみ | — |
| Spotify の非公開歌詞 API | 不使用 | — |

**個人的な学習・研究目的の利用**：中国の著作権法第 24 条第 1 項第 1 号は、個人的な学習、研究または鑑賞のために公表済みの著作物を利用することを認めており、多くの法域にも同様の私的使用／フェアユースの例外があります。これにより個人利用のリスクは下がりますが：

- 歌詞の再配布は**対象外**です。
- 各提供元の利用規約に**優先するものではありません**。

ビルドを配布する場合（公開 APK、ストア掲載、有償提供、サーバー機能）は、先に [Attribution, Content Sources, and Compliance](docs/ATTRIBUTION_AND_COMPLIANCE.md) を読んでください。

---

## 免責事項

**本プロジェクトは Spotify AB とは無関係で、Spotify の公式製品ではありません。**

- 本アプリは音声を再生しません。再生はすべて Spotify アプリが行います。
- Spotify APK を改変せず、Spotify の非公開 API も呼び出しません。
- 再生履歴を収集・送信しません。キャッシュはご自身のデバイスにのみ保存されます。
- 利用者は Spotify Developer Terms と各歌詞ソースの規約に従う必要があります。

---

## コントリビュート

1. このリポジトリを Fork
2. フィーチャーブランチを作成（`git checkout -b feature/amazing-feature`）
3. 変更をコミット（`git commit -m 'Add amazing feature'`）
4. ブランチをプッシュ（`git push origin feature/amazing-feature`）
5. Pull Request を作成

新しい UI 文字列は 4 言語すべての strings.xml に追加してください。新しい歌詞ソースやプロトコルの参考実装は、ライセンスとともに[コンプライアンス文書](docs/ATTRIBUTION_AND_COMPLIANCE.md)に記録してください。

---

## ライセンス

コードは [Apache License 2.0](LICENSE) で提供されます。
