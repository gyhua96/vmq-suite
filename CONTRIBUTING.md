# Contributing

## Development checks

Run the checks that cover the component you changed:

```bash
cd apps/vmq-api && mvn test
pnpm install --frozen-lockfile && pnpm build:web
cd apps/vmq-android && ./gradlew testDebugUnitTest
docker compose config
```

Keep changes focused, add tests for behavior changes, and do not commit `.env`,
database files, build output, logs, APKs, or signing keys.

