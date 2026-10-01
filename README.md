# Audio Recompressor

Audio Recompressor is an offline Android app that repeatedly re-encodes an audio file. Each completed generation becomes the input for the next one:

```text
original → generation 1 → generation 2 → … → generation N
```

Unlike encoding the original file multiple times, this creates a real generation chain and makes lossy-codec degradation accumulate over time.

## Features

- MP3, OGG Vorbis, WAV and FLAC output
- 1–200 sequential generations
- Bitrate, quality, sample-rate and channel controls
- Save the final result or every generation
- Metadata preservation
- Background progress and safe cancellation
- Fully on-device processing; audio is never uploaded
- Android 8.0 or newer

Repeated MP3 or OGG encoding reduces quality but does not guarantee a smaller file at every generation. WAV and FLAC are lossless and normally do not produce the same degradation effect.

## License

[MIT](LICENSE). Third-party components retain their own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
