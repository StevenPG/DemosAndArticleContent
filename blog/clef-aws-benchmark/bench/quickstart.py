#!/usr/bin/env python3
"""Ask Clef-flash one question set and print every probability.

python bench/quickstart.py                                  # local llama-server on :8080
python bench/quickstart.py --url http://127.0.0.1:18080     # e.g. through an SSM port-forward
python bench/quickstart.py --target workers-ai              # Cloudflare-hosted Clef-flash
python bench/quickstart.py --state "Engine 2 oil pressure low light on climb-out, crew returned to field."
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from clef_client import make_client  # noqa: E402

DEFAULT_STATE = "Our checkout started returning errors 10 minutes ago and every order is blocked. Please help!"

QUESTIONS = {
    "department": {
        "type": "choice",
        "instructions": "Which team should handle the message?",
        "criteria": {
            "billing": "Payments or invoices",
            "technical": "Bugs or outages",
            "shipping": "Deliveries and tracking",
            "flight_ops": "Aircraft, crew or flight operations",
        },
    },
    "urgency": {
        "type": "score",
        "instructions": "How urgent is this?",
        "criteria": ["Can wait", "This week", "Today", "Right now"],
    },
    "outage": {"type": "noul", "instructions": "Is a service or system down?"},
    "safety": {"type": "noul", "instructions": "Is anyone's physical safety at risk?"},
}


def bar(p: float, width: int = 30) -> str:
    return "#" * round(p * width) + "." * (width - round(p * width))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--target", choices=["llama.cpp", "workers-ai"], default="llama.cpp")
    parser.add_argument("--url", default="http://127.0.0.1:8080")
    parser.add_argument("--state", default=DEFAULT_STATE)
    parser.add_argument("--raw", action="store_true", help="print the raw response body")
    args = parser.parse_args()

    client = make_client(args.target, args.url)
    decision = client.decide({"state": args.state, "questions": QUESTIONS})
    if args.raw:
        print(json.dumps(decision.body, indent=2))
        return

    print(f"state: {args.state}\n")
    for qid, answer in decision.answers.items():
        if answer["type"] == "noul":
            p = answer["noul"]
            print(f"{qid:<11} noul    P(true) = {p:.4f}  {bar(p)}")
            continue
        headline = answer.get("choice", answer.get("score"))
        print(f"{qid:<11} {answer['type']:<7} -> {headline}  (confidence {answer['confidence']:.3f})")
        legend = answer.get("legend", {})
        for option, p in sorted(answer["probabilities"].items(), key=lambda kv: -kv[1]):
            name = f"{option} {legend[option]}" if option in legend else option
            print(f"    {name:<22} {p:.4f}  {bar(p)}")
    print(f"\n{decision.input_tokens} input tokens, {decision.latency_s * 1000:.0f} ms round trip via {client.name}")


if __name__ == "__main__":
    main()
