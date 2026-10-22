#!/usr/bin/env python3
"""Validate Kiro Bot answer.txt without modifying it. Python 3.9+."""
import argparse
import collections
import pathlib
import re
import sys

LEGACY_VK = re.compile(r"^[a-z]+-?\d+_\d+(?:_\w+)?$")
URL = re.compile(r"^https?://", re.I)

def validate(path: pathlib.Path):
    errors, warnings = [], []
    questions = collections.Counter()
    total = attachment_only = legacy = 0
    try:
        lines = path.read_text(encoding="utf-8-sig").splitlines()
    except (OSError, UnicodeError) as exc:
        print(f"ERROR: unable to read {path}: {exc}", file=sys.stderr)
        return 2
    for number, raw in enumerate(lines, 1):
        line = raw.lstrip("\ufeff").strip()
        if not line or line.startswith("#"):
            continue
        total += 1
        parts = line.split("|", maxsplit=6)
        if len(parts) < 2:
            errors.append(f"line {number}: fewer than two fields")
            continue
        question, answer = parts[0].strip(), parts[1].strip()
        if not question:
            errors.append(f"line {number}: empty question")
        else:
            questions[question.casefold()] += 1
        attachments = parts[2].strip() if len(parts) > 2 else ""
        valid_attachments = []
        for token in attachments.split(","):
            token = token.strip()
            if not token:
                continue
            if URL.match(token):
                valid_attachments.append(token)
            elif LEGACY_VK.fullmatch(token):
                valid_attachments.append(token)
                legacy += 1
            else:
                warnings.append(f"line {number}: unrecognized attachment {token[:80]!r}")
        if not answer:
            attachment_only += 1
            if not valid_attachments:
                warnings.append(f"line {number}: no answer text and no recognized attachment")
        if len(parts) > 3 and parts[3].strip():
            try:
                int(parts[3].strip())
            except ValueError:
                warnings.append(f"line {number}: non-integer repetition limit")
    repeats = sum(n - 1 for n in questions.values() if n > 1)
    print(f"Records: {total}; unique questions: {len(questions)}; repeated-question rows: {repeats}")
    print(f"Empty-text records: {attachment_only}; legacy VK attachment tokens: {legacy}")
    print(f"Errors: {len(errors)}; warnings: {len(warnings)}")
    for item in (errors + warnings)[:60]:
        print(item)
    if len(errors) + len(warnings) > 60:
        print(f"... {len(errors) + len(warnings) - 60} more diagnostics")
    return 1 if errors else 0

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("path", nargs="?", type=pathlib.Path, default=pathlib.Path("app/src/main/assets/answer.txt"))
    args = parser.parse_args()
    sys.exit(validate(args.path))
