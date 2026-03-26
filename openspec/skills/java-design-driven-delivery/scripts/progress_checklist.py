#!/usr/bin/env python3
"""Manage a local markdown progress checklist for Java delivery tasks."""

from __future__ import annotations

import argparse
import sys
from dataclasses import dataclass
from pathlib import Path

VALID_STATUS = {"TODO", "IN_PROGRESS", "DONE", "BLOCKED"}
VALID_CHECK = {"PENDING", "OK", "FAIL"}


@dataclass
class TaskRow:
    task_id: str
    task: str
    status: str = "TODO"
    check: str = "PENDING"
    notes: str = ""


def parse_task_arg(task_value: str) -> tuple[str, str]:
    if "::" not in task_value:
        raise ValueError("task must use the format ID::Task description")
    task_id, task = task_value.split("::", 1)
    task_id = task_id.strip()
    task = task.strip()
    if not task_id or not task:
        raise ValueError("task id and description must be non-empty")
    return task_id, task


def escape_cell(value: str) -> str:
    return value.replace("\n", " ").replace("|", "/").strip()


def compute_progress(rows: list[TaskRow]) -> int:
    if not rows:
        return 0
    done_count = sum(1 for row in rows if row.status == "DONE")
    return round(done_count * 100 / len(rows))


def compute_gate(rows: list[TaskRow]) -> str:
    if not rows:
        return "PENDING"
    if any(row.check == "FAIL" for row in rows):
        return "FAIL"
    if all(row.status == "DONE" and row.check == "OK" for row in rows):
        return "PASS"
    return "PENDING"


def render_markdown(title: str, path: Path, rows: list[TaskRow]) -> str:
    progress = compute_progress(rows)
    final_gate = compute_gate(rows)
    lines = [
        f"# {title}",
        "",
        f"- File: `{path}`",
        f"- Progress: `{progress}%`",
        f"- Final Gate: `{final_gate}`",
        "",
        "| ID | Task | Status | Check | Notes |",
        "| --- | --- | --- | --- | --- |",
    ]
    for row in rows:
        lines.append(
            "| "
            + " | ".join(
                [
                    escape_cell(row.task_id),
                    escape_cell(row.task),
                    escape_cell(row.status),
                    escape_cell(row.check),
                    escape_cell(row.notes),
                ]
            )
            + " |"
        )
    lines.append("")
    return "\n".join(lines)


def parse_markdown(path: Path) -> tuple[str, list[TaskRow]]:
    if not path.exists():
        raise FileNotFoundError(f"checklist file not found: {path}")

    lines = path.read_text(encoding="utf-8").splitlines()
    title = path.stem
    rows: list[TaskRow] = []

    for line in lines:
        if line.startswith("# "):
            title = line[2:].strip()
            break

    for line in lines:
        stripped = line.strip()
        if not stripped.startswith("|"):
            continue
        if stripped.startswith("| ID ") or stripped.startswith("| --- "):
            continue
        parts = [part.strip() for part in stripped.split("|")[1:-1]]
        if len(parts) != 5:
            continue
        rows.append(
            TaskRow(
                task_id=parts[0],
                task=parts[1],
                status=parts[2],
                check=parts[3],
                notes=parts[4],
            )
        )

    if not rows:
        raise ValueError(f"no checklist rows found in: {path}")
    return title, rows


def ensure_parent(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)


def write_checklist(path: Path, title: str, rows: list[TaskRow]) -> None:
    ensure_parent(path)
    path.write_text(render_markdown(title, path, rows), encoding="utf-8")


def init_command(args: argparse.Namespace) -> int:
    path = Path(args.file).expanduser()
    if path.exists() and not args.force:
        print(f"checklist already exists: {path}", file=sys.stderr)
        return 1

    rows = []
    seen_ids: set[str] = set()
    for task_value in args.task:
        task_id, task = parse_task_arg(task_value)
        if task_id in seen_ids:
            print(f"duplicate task id: {task_id}", file=sys.stderr)
            return 1
        seen_ids.add(task_id)
        rows.append(TaskRow(task_id=task_id, task=task))

    write_checklist(path, args.title, rows)
    print(f"initialized checklist: {path}")
    return 0


def add_command(args: argparse.Namespace) -> int:
    path = Path(args.file).expanduser()
    title, rows = parse_markdown(path)
    seen_ids = {row.task_id for row in rows}

    for task_value in args.task:
        task_id, task = parse_task_arg(task_value)
        if task_id in seen_ids:
            print(f"duplicate task id: {task_id}", file=sys.stderr)
            return 1
        rows.append(TaskRow(task_id=task_id, task=task))
        seen_ids.add(task_id)

    write_checklist(path, title, rows)
    print(f"added {len(args.task)} task(s) to: {path}")
    return 0


def update_command(args: argparse.Namespace) -> int:
    path = Path(args.file).expanduser()
    title, rows = parse_markdown(path)

    target_row = next((row for row in rows if row.task_id == args.task_id), None)
    if target_row is None:
        print(f"task id not found: {args.task_id}", file=sys.stderr)
        return 1

    if args.status:
        if args.status not in VALID_STATUS:
            print(f"invalid status: {args.status}", file=sys.stderr)
            return 1
        target_row.status = args.status

    if args.check:
        if args.check not in VALID_CHECK:
            print(f"invalid check: {args.check}", file=sys.stderr)
            return 1
        target_row.check = args.check

    if args.notes is not None:
        target_row.notes = args.notes

    write_checklist(path, title, rows)
    print(f"updated task {args.task_id} in: {path}")
    return 0


def verify_command(args: argparse.Namespace) -> int:
    path = Path(args.file).expanduser()
    title, rows = parse_markdown(path)
    progress = compute_progress(rows)
    final_gate = compute_gate(rows)
    print(f"{title}: progress={progress}% final_gate={final_gate}")
    return 0 if progress == 100 and final_gate == "PASS" else 1


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    init_parser = subparsers.add_parser("init", help="initialize a checklist")
    init_parser.add_argument("--file", required=True)
    init_parser.add_argument("--title", required=True)
    init_parser.add_argument("--task", action="append", required=True)
    init_parser.add_argument("--force", action="store_true")
    init_parser.set_defaults(func=init_command)

    add_parser = subparsers.add_parser("add", help="append task rows")
    add_parser.add_argument("--file", required=True)
    add_parser.add_argument("--task", action="append", required=True)
    add_parser.set_defaults(func=add_command)

    update_parser = subparsers.add_parser("update", help="update a task row")
    update_parser.add_argument("--file", required=True)
    update_parser.add_argument("--task-id", required=True)
    update_parser.add_argument("--status")
    update_parser.add_argument("--check")
    update_parser.add_argument("--notes")
    update_parser.set_defaults(func=update_command)

    verify_parser = subparsers.add_parser("verify", help="verify completion gate")
    verify_parser.add_argument("--file", required=True)
    verify_parser.set_defaults(func=verify_command)

    return parser


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
