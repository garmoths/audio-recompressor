# Contributing

Thanks for helping improve Audio Recompressor.

1. Open an issue before large behavioral or architectural changes.
2. Keep audio processing fully offline unless a proposal explicitly changes the privacy model.
3. Preserve the core invariant: generation `n + 1` consumes generation `n`, never the original input.
4. Add or update tests for pipeline, cancellation and storage behavior.
5. Run `./gradlew testDebugUnitTest lintDebug assembleDebug` before submitting a pull request.
6. Do not commit APK/AAB files, keystores, credentials, personal audio or device logs.

Pull requests should explain the user-facing change, tests performed and any license impact introduced by new codecs or dependencies.
