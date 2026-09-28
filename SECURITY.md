# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in Quest Activity Tracker, please
report it privately rather than opening a public issue.

Email: **venkatswarajgoli@gmail.com**

Please include:

- A description of the vulnerability and its potential impact.
- Steps to reproduce (proof-of-concept if possible).
- Any suggested remediation.

You can expect an initial acknowledgement within a few days. Once the issue is
confirmed and fixed, we will credit you in the release notes unless you prefer
to remain anonymous.

## Scope

This app runs locally on a Meta Quest headset and serves an optional web
companion on the local network (port 8080). Please be especially mindful of
issues relating to the embedded web server, the exported data, and the
`UsageStatsManager` permission handling.
