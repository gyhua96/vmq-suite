# Security Policy

## Reporting a vulnerability

Do not publish exploitable details in a public issue. Contact the repository
maintainer privately and include the affected version, reproduction steps,
impact, and any suggested mitigation.

## Deployment baseline

- Use a unique, long `VMQ_ADMIN_PASSWORD` and database password.
- Put the public endpoint behind HTTPS.
- Do not expose PostgreSQL to the public network.
- Treat the Android notification-listener permission as sensitive.
- Keep the legacy MD5 signature mode only for compatibility; new integrations
  should use HMAC-SHA256 and HTTPS.
- Provide a private Android release signing key outside this repository.

No production credentials or Android signing keys are distributed here.

