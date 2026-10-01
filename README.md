# Audio Recompressor

Audio Recompressor is an offline Android audio re-encoder. It passes every completed generation into the next encode operation:

```text
original → generation 1 → generation 2 → … → generation N
```

It does **not** encode the original file repeatedly. The output of generation 1 becomes the input of generation 2, and so on.

## Features

- MP3, OGG Vorbis, WAV and FLAC output
- 1–200 sequential generations
- Format-specific bitrate and quality controls
- Optional sample-rate and channel conversion
- Optional metadata preservation
- Save only the final file or every generation
- Foreground processing with progress and cancellation
- Fully on-device processing; audio is never uploaded
- Turkish user interface

## Requirements and build

- Android Studio with JDK 17
- Android SDK 36
- Android 8.0 (API 26) or newer

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release signing uses environment variables. Never commit a keystore or credentials:

```bash
export AUDIOCOMPRESSOR_KEYSTORE=/absolute/path/release.jks
export AUDIOCOMPRESSOR_STORE_PASSWORD='...'
export AUDIOCOMPRESSOR_KEY_ALIAS='...'
export AUDIOCOMPRESSOR_KEY_PASSWORD='...'
./gradlew assembleRelease bundleRelease
```

## How the chain is guaranteed

`GenerationPipeline` starts with the original file, encodes into a temporary generation file, closes and validates it, then assigns that file as the next input. The prior temporary file is removed only after the next generation succeeds. Tests verify a 100-generation chain, the 200-generation limit, cancellation cleanup and per-generation publishing.

## Privacy and limitations

- Processing takes place locally on the device.
- Re-encoding a lossy format degrades quality, but does not guarantee a smaller file at every generation.
- WAV and FLAC are lossless; repeated encoding normally does not create the same degradation effect.
- High generation counts consume significant time, battery and storage I/O.

## Contributing and license

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md) before contributing. Audio Recompressor source is available under the [MIT License](LICENSE). Dependencies retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
