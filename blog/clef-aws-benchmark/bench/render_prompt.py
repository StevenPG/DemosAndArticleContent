#!/usr/bin/env python3
"""Show the literal prompt Clef-flash reads for a request.

llama.cpp renders every /v1/systemone request through the ``tokenizer.chat_template.systemone``
Jinja template stored in the GGUF (copied to data/systemone_template.jinja from
ggml-org/Clef-Flash-GGUF). This script renders the same template so you can see exactly what the
model was asked: system prompt, state, then every question with its allowed options. The state is
rendered as clef_client sends it (a pre-serialized JSON string; see clef_client.render_state).

    python bench/render_prompt.py --workload small            # first request of a workload
    python bench/render_prompt.py --workload large --index 2
    python bench/render_prompt.py --case mx-hydraulic-leak   # a labeled eval case
    python bench/render_prompt.py --request my_request.json  # any request body
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import jinja2

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import workloads  # noqa: E402
from clef_client import render_state  # noqa: E402


def sort_keys(value: Any) -> Any:
    # llama.cpp sorts object keys before rendering, matching how Clef was trained
    if isinstance(value, dict):
        return {k: sort_keys(value[k]) for k in sorted(value)}
    if isinstance(value, list):
        return [sort_keys(v) for v in value]
    return value


def options(question: dict[str, Any]) -> list[dict[str, Any]]:
    criteria = question.get("criteria")
    if question["type"] == "noul":
        criteria = criteria or {}
        return [{"key": k, "description": criteria.get(k)} for k in ("true", "false")]  # true first
    if question["type"] == "choice":
        return [{"key": k, "description": criteria[k]} for k in sorted(criteria)]  # sorted by key
    return [{"key": str(i), "description": d} for i, d in enumerate(criteria)]  # score: level index


def render(request: dict[str, Any]) -> str:
    env = jinja2.Environment(keep_trailing_newline=True)
    env.filters["tojson"] = lambda v, separators=(",", ":"): json.dumps(
        v, separators=tuple(separators), ensure_ascii=False
    )
    template = env.from_string((HERE / "data" / "systemone_template.jinja").read_text())
    questions = [
        {"id": qid, "type": q["type"], "instructions": q.get("instructions") or qid, "options": options(q)}
        for qid, q in request["questions"].items()
    ]
    # sep / mark_* are llama.cpp's internal span markers; they render as nothing in the final text
    return template.render(
        state=render_state(request["state"]),  # what clef_client sends: a pre-serialized string
        questions=sort_keys(questions),
        sep="",
        mark_question="",
        mark_option="",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--workload", choices=sorted(workloads.BUILDERS))
    source.add_argument("--case", help="id from data/eval_cases.json")
    source.add_argument("--request", type=Path, help="JSON file with a /v1/systemone request body")
    parser.add_argument("--index", type=int, default=0, help="which request of the workload")
    args = parser.parse_args()

    if args.workload:
        request = workloads.build(args.workload, args.index + 1)[args.index]
    elif args.case:
        cases = {c["id"]: c for c in json.loads((HERE / "data" / "eval_cases.json").read_text())}
        request = cases[args.case]
    else:
        request = json.loads(args.request.read_text())
    print(render(request))


if __name__ == "__main__":
    main()
