# DockMind AI — standing instructions for Claude Code

The full project brief is in `docs/MASTER_PROMPT.md`. Read it at the start of every session,
then read `PROJECT_CONTEXT.md` (once it exists) to see where work left off.

## Authorship (non-negotiable)

- Lavanesh Thirukonda Mahendran is the **only** contributor to this repository.
- Commit with the configured git identity only:
  `Lavanesh Thirukonda Mahendran <lavanesh645@gmail.com>`.
- **Never** add `Co-Authored-By`, `Generated with`, session links, or any other attribution
  trailer or contributor identity to commit messages or pull request descriptions.
- Before every push, run `git log origin/main..HEAD --format='%an <%ae>%n%B'` (or
  `git log --format='%an <%ae>%n%B'` before the first push) and confirm there are no
  trailers and no other authors. If any appear, stop and tell me instead of pushing.
- Never rewrite or force-push history unless I explicitly ask.

## Working rules

- Work one phase at a time, as the brief describes. Each phase ends with passing
  `./mvnw verify` (and frontend tests once the frontend exists), updated docs, and a commit.
- Never claim a test, build, push, or deployment succeeded without showing the real output.
- No secrets in git. Use `.env` files (gitignored) and `.env.example` templates.
- Use synthetic data only; never present simulated numbers as real-world savings.
- Explain new concepts in LEARNING_GUIDE.md as you introduce them — I want to understand
  the code, not just have it.
