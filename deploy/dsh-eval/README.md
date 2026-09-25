# DSH evaluation sidecar

This image is an optional, offline-evaluation-only target. It does not replace
AgentForge's production chat runtime and is not started when
`SXW_EVAL_DSH_ENABLED=false` (the default).

## Build the pinned source

Prerequisites: Docker Desktop/Engine and the DSH checkout at commit
`99f6f02fecdb7dff40c3fbc9470f5907c29f74ca`.

```powershell
.\deploy\dsh-eval\build.ps1 -DshSource D:\github-work\deepseek-harness
```

If the image is built on the CentOS Docker host, copy both repositories to the
host and run:

```bash
bash ./deploy/dsh-eval/build.sh /opt/deepseek-harness agentforge/dsh-eval:99f6f02
```

The build script refuses any other commit, stages the checkout in a temporary
directory, uses its lockfile, and creates `agentforge/dsh-eval:99f6f02`.

## Enable comparisons

```text
DEEPSEEK_API_KEY=...
SXW_EVAL_DSH_ENABLED=true
SXW_EVAL_DSH_IMAGE=agentforge/dsh-eval:99f6f02
```

DSH runs with a read-only root filesystem, an isolated temporary directory,
CPU/memory/process limits, no host mounts and no published ports. Its only
model-facing tools are deterministic fixtures: `fixture_lookup`, `calculator`,
`simulated_write`, and `forced_failure`.

Docker's default bridge network does not provide domain-level egress filtering.
Production evaluators should attach the container to a controlled egress proxy
or firewall policy that only permits the configured DeepSeek API endpoint.
Neither the API key nor raw event logs are stored in the database; redacted
event logs are written to the configured MinIO bucket.
