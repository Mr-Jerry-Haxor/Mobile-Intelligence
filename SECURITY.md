# Security Policy

Mobile Intelligence handles sensitive local behavioral and DNS metadata. Security reports are welcome and appreciated.

## Supported Versions

| Version | Supported |
| --- | --- |
| 1.x | Yes |
| < 1.0.0 | No |

## Reporting a Vulnerability

Please do not report vulnerabilities in public issues.

Preferred process:

1. Use GitHub Private Vulnerability Reporting (Security Advisories) if enabled.
2. If not available, contact the maintainers through a private channel exposed by the repository owner.
3. Include:
   - Affected version/commit
   - Reproduction steps or proof of concept
   - Impact assessment
   - Suggested mitigation (if available)

## Response Targets

- Initial acknowledgement: within 3 business days
- Triage status update: within 7 business days
- Fix timeline: depends on severity and release planning

## Scope and Priorities

High-priority areas include:

- Android component exposure (`activity`, `service`, `receiver`, `provider`)
- Permission use and data access boundaries
- Local data safety (database, backup, deletion)
- DNS VPN pipeline correctness and abuse resistance
- Authentication/authorization logic for protected actions

## Disclosure

After a fix is released, maintainers may publish an advisory with:

- Severity and affected versions
- Fix version/commit
- Mitigations for users who cannot upgrade immediately

## Out of Scope

General product feedback, UX requests, and non-security defects should be opened as standard issues.
