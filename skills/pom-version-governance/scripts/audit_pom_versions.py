#!/usr/bin/env python3
"""Audit duplicated and conflicting Maven POM version definitions."""

from __future__ import annotations

import argparse
import json
import os
import sys
from collections import defaultdict
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Dict, Iterable, List, Sequence
from xml.etree import ElementTree as ET


@dataclass
class VersionEntry:
    kind: str
    key: str
    value: str
    pom: str
    module: str
    section: str


def local_name(tag: str) -> str:
    if "}" in tag:
        return tag.split("}", 1)[1]
    return tag


def child_text(elem: ET.Element, name: str) -> str:
    for child in list(elem):
        if local_name(child.tag) == name:
            return (child.text or "").strip()
    return ""


def child_element(elem: ET.Element, name: str) -> ET.Element | None:
    for child in list(elem):
        if local_name(child.tag) == name:
            return child
    return None


def children(elem: ET.Element, name: str) -> Iterable[ET.Element]:
    for child in list(elem):
        if local_name(child.tag) == name:
            yield child


def parse_pom(pom_path: Path, root_dir: Path) -> List[VersionEntry]:
    entries: List[VersionEntry] = []
    try:
        tree = ET.parse(pom_path)
    except ET.ParseError as exc:
        print(f"[WARN] Failed to parse {pom_path}: {exc}", file=sys.stderr)
        return entries

    root = tree.getroot()
    rel_pom = str(pom_path.relative_to(root_dir))
    artifact_id = child_text(root, "artifactId")
    module = artifact_id or pom_path.parent.name

    properties = child_element(root, "properties")
    if properties is not None:
        for prop in list(properties):
            key = local_name(prop.tag)
            value = (prop.text or "").strip()
            if not value:
                continue
            entries.append(
                VersionEntry(
                    kind="property",
                    key=key,
                    value=value,
                    pom=rel_pom,
                    module=module,
                    section="properties",
                )
            )

    dep_mgmt = child_element(root, "dependencyManagement")
    if dep_mgmt is not None:
        deps = child_element(dep_mgmt, "dependencies")
        if deps is not None:
            for dep in children(deps, "dependency"):
                dep_entries = parse_dependency(dep, rel_pom, module, "dependencyManagement")
                entries.extend(dep_entries)

    direct_dependencies = child_element(root, "dependencies")
    if direct_dependencies is not None:
        for dep in children(direct_dependencies, "dependency"):
            dep_entries = parse_dependency(dep, rel_pom, module, "dependencies")
            entries.extend(dep_entries)

    build = child_element(root, "build")
    if build is not None:
        plugin_mgmt = child_element(build, "pluginManagement")
        if plugin_mgmt is not None:
            plugins = child_element(plugin_mgmt, "plugins")
            if plugins is not None:
                for plugin in children(plugins, "plugin"):
                    plugin_entry = parse_plugin(
                        plugin, rel_pom, module, "pluginManagement"
                    )
                    if plugin_entry:
                        entries.append(plugin_entry)

        plugins = child_element(build, "plugins")
        if plugins is not None:
            for plugin in children(plugins, "plugin"):
                plugin_entry = parse_plugin(plugin, rel_pom, module, "plugins")
                if plugin_entry:
                    entries.append(plugin_entry)

    return entries


def parse_dependency(
    dep: ET.Element, rel_pom: str, module: str, section: str
) -> List[VersionEntry]:
    group_id = child_text(dep, "groupId")
    artifact_id = child_text(dep, "artifactId")
    version = child_text(dep, "version")

    if not group_id or not artifact_id or not version:
        return []

    scope = child_text(dep, "scope")
    dep_key = f"{group_id}:{artifact_id}"
    section_label = section if not scope else f"{section} (scope={scope})"
    return [
        VersionEntry(
            kind="dependency",
            key=dep_key,
            value=version,
            pom=rel_pom,
            module=module,
            section=section_label,
        )
    ]


def parse_plugin(
    plugin: ET.Element, rel_pom: str, module: str, section: str
) -> VersionEntry | None:
    group_id = child_text(plugin, "groupId") or "org.apache.maven.plugins"
    artifact_id = child_text(plugin, "artifactId")
    version = child_text(plugin, "version")

    if not artifact_id or not version:
        return None

    plugin_key = f"{group_id}:{artifact_id}"
    return VersionEntry(
        kind="plugin",
        key=plugin_key,
        value=version,
        pom=rel_pom,
        module=module,
        section=section,
    )


def find_pom_files(root_dir: Path, ignored_dirs: Sequence[str]) -> List[Path]:
    pom_files: List[Path] = []
    ignored = set(ignored_dirs)
    for dirpath, dirnames, filenames in os.walk(root_dir):
        dirnames[:] = [d for d in dirnames if d not in ignored]
        if "pom.xml" in filenames:
            pom_files.append(Path(dirpath) / "pom.xml")
    return sorted(pom_files)


def group_by_key(entries: Iterable[VersionEntry]) -> Dict[str, List[VersionEntry]]:
    grouped: Dict[str, List[VersionEntry]] = defaultdict(list)
    for entry in entries:
        grouped[entry.key].append(entry)
    return grouped


def analyze_groups(
    grouped: Dict[str, List[VersionEntry]], min_occurrences: int
) -> Dict[str, List[Dict[str, object]]]:
    conflicts: List[Dict[str, object]] = []
    duplicates: List[Dict[str, object]] = []

    for key, rows in grouped.items():
        if len(rows) < min_occurrences:
            continue

        value_groups: Dict[str, List[VersionEntry]] = defaultdict(list)
        for row in rows:
            value_groups[row.value].append(row)

        info = {
            "key": key,
            "occurrences": len(rows),
            "values": [
                {
                    "value": value,
                    "locations": [
                        {
                            "pom": item.pom,
                            "module": item.module,
                            "section": item.section,
                        }
                        for item in sorted(
                            items, key=lambda x: (x.pom, x.module, x.section)
                        )
                    ],
                }
                for value, items in sorted(value_groups.items(), key=lambda x: x[0])
            ],
        }

        if len(value_groups) > 1:
            conflicts.append(info)
        else:
            duplicates.append(info)

    conflicts.sort(key=lambda x: x["occurrences"], reverse=True)
    duplicates.sort(key=lambda x: x["occurrences"], reverse=True)
    return {"conflicts": conflicts, "duplicates": duplicates}


def build_report_payload(
    root_dir: Path, pom_files: List[Path], entries: List[VersionEntry], min_occurrences: int
) -> Dict[str, object]:
    dependency_entries = [e for e in entries if e.kind == "dependency"]
    plugin_entries = [e for e in entries if e.kind == "plugin"]
    property_entries = [e for e in entries if e.kind == "property"]

    dependencies = analyze_groups(group_by_key(dependency_entries), min_occurrences)
    plugins = analyze_groups(group_by_key(plugin_entries), min_occurrences)
    properties = analyze_groups(group_by_key(property_entries), min_occurrences)

    summary = {
        "generated_at_utc": datetime.now(timezone.utc).isoformat(),
        "root": str(root_dir),
        "pom_files": len(pom_files),
        "dependency_versions": len(dependency_entries),
        "plugin_versions": len(plugin_entries),
        "property_versions": len(property_entries),
        "dependency_conflicts": len(dependencies["conflicts"]),
        "plugin_conflicts": len(plugins["conflicts"]),
        "property_conflicts": len(properties["conflicts"]),
    }

    return {
        "summary": summary,
        "dependencies": dependencies,
        "plugins": plugins,
        "properties": properties,
        "raw_entries": [asdict(e) for e in entries],
    }


def render_markdown(payload: Dict[str, object], min_occurrences: int) -> str:
    summary = payload["summary"]
    lines: List[str] = []

    lines.append("# Maven POM Version Audit")
    lines.append("")
    lines.append(f"- Root: `{summary['root']}`")
    lines.append(f"- Generated (UTC): `{summary['generated_at_utc']}`")
    lines.append(f"- POM files scanned: `{summary['pom_files']}`")
    lines.append(f"- Dependency versions found: `{summary['dependency_versions']}`")
    lines.append(f"- Plugin versions found: `{summary['plugin_versions']}`")
    lines.append(f"- Property values found: `{summary['property_versions']}`")
    lines.append("")

    lines.append("## Conflicting Version Definitions")
    lines.append("")
    lines.extend(render_kind_section("Dependencies", payload["dependencies"]["conflicts"]))
    lines.extend(render_kind_section("Plugins", payload["plugins"]["conflicts"]))
    lines.extend(render_kind_section("Properties", payload["properties"]["conflicts"]))

    lines.append("")
    lines.append(f"## Repeated Identical Definitions (>= {min_occurrences} occurrences)")
    lines.append("")
    lines.extend(
        render_kind_section("Dependencies", payload["dependencies"]["duplicates"])
    )
    lines.extend(render_kind_section("Plugins", payload["plugins"]["duplicates"]))
    lines.extend(render_kind_section("Properties", payload["properties"]["duplicates"]))

    lines.append("")
    lines.append("## Consolidation Checklist")
    lines.append("")
    lines.append("1. Resolve every conflict first; keep exactly one target version per key.")
    lines.append(
        "2. Move shared dependency versions into a root `dependencyManagement` or BOM module."
    )
    lines.append("3. Move shared plugin versions into root `pluginManagement`.")
    lines.append(
        "4. Keep common version properties only once in parent/BOM and remove child duplicates."
    )
    lines.append("5. Re-run this audit and ensure conflict sections are empty.")

    return "\n".join(lines)


def render_kind_section(title: str, groups: Sequence[Dict[str, object]]) -> List[str]:
    lines = [f"### {title}", ""]
    if not groups:
        lines.append("- None")
        lines.append("")
        return lines

    for group in groups:
        lines.append(f"- `{group['key']}` ({group['occurrences']} occurrences)")
        for value_info in group["values"]:
            lines.append(f"  - Version/Value `{value_info['value']}`")
            for loc in value_info["locations"]:
                lines.append(
                    f"    - `{loc['pom']}` | module `{loc['module']}` | section `{loc['section']}`"
                )
        lines.append("")
    return lines


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Audit duplicate/conflicting Maven dependency/plugin/property versions."
    )
    parser.add_argument("--root", default=".", help="Project root to scan.")
    parser.add_argument(
        "--min-occurrences",
        type=int,
        default=2,
        help="Minimum repeat count to report duplicates/conflicts.",
    )
    parser.add_argument(
        "--ignore-dir",
        action="append",
        default=[],
        help="Directory names to ignore. Can be repeated.",
    )
    parser.add_argument(
        "--format",
        choices=["markdown", "json"],
        default="markdown",
        help="Output format.",
    )
    parser.add_argument(
        "--output",
        default="",
        help="Write report to file path instead of stdout.",
    )
    parser.add_argument(
        "--fail-on-conflict",
        action="store_true",
        help="Exit with code 1 if any conflict is found.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root_dir = Path(args.root).resolve()
    if not root_dir.exists():
        print(f"[ERROR] Root directory does not exist: {root_dir}", file=sys.stderr)
        return 2

    min_occurrences = max(2, args.min_occurrences)
    ignored_dirs = [".git", "target", ".idea", ".mvn", ".gradle", "node_modules"]
    ignored_dirs.extend(args.ignore_dir)

    pom_files = find_pom_files(root_dir, ignored_dirs)
    if not pom_files:
        print(f"[WARN] No pom.xml found under {root_dir}", file=sys.stderr)
        return 0

    entries: List[VersionEntry] = []
    for pom in pom_files:
        entries.extend(parse_pom(pom, root_dir))

    payload = build_report_payload(root_dir, pom_files, entries, min_occurrences)

    if args.format == "json":
        rendered = json.dumps(payload, indent=2, ensure_ascii=False)
    else:
        rendered = render_markdown(payload, min_occurrences)

    if args.output:
        output_path = Path(args.output)
        output_path.parent.mkdir(parents=True, exist_ok=True)
        output_path.write_text(rendered, encoding="utf-8")
        print(f"[INFO] Report written to {output_path}")
    else:
        print(rendered)

    summary = payload["summary"]
    has_conflict = any(
        (
            summary["dependency_conflicts"] > 0,
            summary["plugin_conflicts"] > 0,
            summary["property_conflicts"] > 0,
        )
    )
    if args.fail_on_conflict and has_conflict:
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
