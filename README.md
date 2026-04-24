# SDF Account Status (WebStorm Plugin)

Shows the active SuiteCloud SDF default account from `project.json` directly in the status bar.

## Features

- Displays current account from `defaultAuthId` (also supports `DefaultAuthID`)
- Color coding by environment:
  - Green: sandbox
  - Red + critical icon: production
  - Yellow: unknown environment or missing account key
- Auto-refresh on `project.json` file updates
- Click status label to open `project.json`

## Account Environment Rules

- Sandbox: values containing `SB`, `SANDBOX`, or `SAND`
- Production: values containing `PROD` or `PRODUCTION`
- Unknown: anything else

Examples:

- `5860676_SB3-Adm-Sand` -> Sandbox
- `5860676-Adm-Prod` -> Production

## Development

Prerequisites:

- JDK 21

Commands:

```bash
./gradlew clean test
./gradlew runIde
./gradlew buildPlugin
```

Plugin ZIP output:

`build/distributions/webstorm-sdf-account-status-0.1.0.zip`

## Publishing Readiness

This project is configured with IntelliJ Platform Gradle Plugin 2.x and includes optional signing/publishing configuration through env vars:

- `CERTIFICATE_CHAIN`
- `PRIVATE_KEY`
- `PRIVATE_KEY_PASSWORD`
- `PUBLISH_TOKEN`

## License

MIT. See `LICENSE`.


## Branding

- Logo prompt for ChatGPT: `docs/branding/logo-prompt.md`
- Plugin icon files:
  - `src/main/resources/META-INF/pluginIcon.svg`
  - `src/main/resources/META-INF/pluginIcon_dark.svg`
