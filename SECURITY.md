# Security policy

Only the latest published release receives security fixes.

Do not open a public issue for vulnerabilities involving arbitrary file access, native codec execution, path traversal, credential exposure or unsafe media parsing. Contact the repository owner privately through the security-reporting channel configured on the hosting platform.

Include the affected version, Android version, reproduction steps and a minimal non-sensitive sample when possible. Never include private audio, signing keys, passwords or access tokens.

Release keystores and credentials must remain outside the repository. Release builds read signing data only from `AUDIOCOMPRESSOR_*` environment variables.
