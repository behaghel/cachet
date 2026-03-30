---
name: Feedback - expedite execution
description: User wants fast execution, avoid slow devenv shell invocations, don't get stuck validating
type: feedback
---

Don't run `devenv shell --` for quick validations when tools are available locally or checks can be deferred. The shell startup is unpredictable and blocks progress.

**Why:** devenv shell can take minutes on first run after lock changes. User got frustrated waiting.
**How to apply:** Use local tools directly when possible. Batch devenv-dependent work. Don't iterate on yamllint line-length fixes one at a time via devenv shell. Commit and let CI validate.
