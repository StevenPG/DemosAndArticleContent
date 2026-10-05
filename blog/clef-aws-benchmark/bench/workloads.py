"""Deterministic benchmark workloads at three prompt sizes.

Clef is prefill-only: one forward pass over (system prompt + state + schema), no decode loop.
Cost and latency therefore scale with *input* tokens, so the workloads differ mainly in state size:

    small   ~500 tokens   support-ticket triage        (4 questions)
    medium  ~1.7k tokens  invoice review, JSON state   (4 questions)
    large   ~4.4k tokens  auth/web log incident triage (4 questions)

(Prompt tokens as reported in usage.input_tokens; ~300 of them are Clef's system prompt and schema.)

Every call to ``build(name, n)`` returns the same ``n`` requests (seeded RNG), so runs on different
machines evaluate identical prompts. Each request has a different state so the server cannot answer
from a cached prompt prefix.
"""

from __future__ import annotations

import random
from typing import Any

SEED = 20261001

# ---------------------------------------------------------------------------------------------
# small: support tickets
# ---------------------------------------------------------------------------------------------

TICKETS = [
    "I was charged twice for order #{n} last week and nobody has replied to my emails.",
    "Checkout has been returning a 502 error for the last 20 minutes, none of our customers can pay.",
    "My package says delivered but it is not at my door. Tracking number {n}.",
    "How do I change the email address on my account? The settings page does not let me edit it.",
    "The mobile app crashes every time I open the camera scanner on Android 16.",
    "Please cancel my subscription and refund the last invoice, I never used the service this month.",
    "Our SSO login stopped working after we rotated the certificate this morning. 400 users locked out.",
    "The invoice PDF shows the wrong VAT number for our company. Can you reissue it?",
    "Shipping to Canada shows as unavailable even though it worked last month.",
    "Two-factor codes are not arriving by SMS, I am stuck at the login screen.",
    "Your API returns 429 for us at 10 requests per second, the docs say the limit is 100.",
    "I would like to upgrade to the annual plan, will I get a prorated credit?",
]

TICKET_QUESTIONS: dict[str, Any] = {
    "department": {
        "type": "choice",
        "instructions": "Which team should handle this message?",
        "criteria": {
            "billing": "Payments, invoices, refunds, plan changes",
            "technical": "Bugs, errors, outages, integrations, login problems",
            "shipping": "Deliveries, tracking, carriers",
            "account": "Profile and account settings",
        },
    },
    "urgency": {
        "type": "score",
        "instructions": "How quickly does this need a response?",
        "criteria": ["Can wait", "This week", "Today", "Right now"],
    },
    "outage": {"type": "noul", "instructions": "Is a service down for more than one user?"},
    "refund": {"type": "noul", "instructions": "Is the customer asking for money back?"},
}


def _small(rng: random.Random, i: int) -> dict[str, Any]:
    text = TICKETS[i % len(TICKETS)].format(n=rng.randint(100000, 999999))
    tier = rng.choice(["free", "pro", "enterprise"])
    return {
        "state": {
            "channel": rng.choice(["email", "chat", "web form"]),
            "customer": {"id": f"C-{rng.randint(1000, 9999)}", "tier": tier, "tenure_months": rng.randint(1, 60)},
            "message": text,
        },
        "questions": TICKET_QUESTIONS,
    }


# ---------------------------------------------------------------------------------------------
# medium: invoice review
# ---------------------------------------------------------------------------------------------

ITEMS = [
    ("Laptop 14in", 1100.0),
    ("Docking station", 210.0),
    ("27in monitor", 330.0),
    ("USB-C cable", 19.0),
    ("Cloud hosting, monthly", 2400.0),
    ("Support contract", 1800.0),
    ("Office chairs", 420.0),
    ("Consulting hours", 175.0),
    ("Software licence seat", 49.0),
    ("Printer toner", 88.0),
    ("Network switch 48p", 1650.0),
    ("Rack PDU", 540.0),
    ("Conference speakerphone", 260.0),
]


def _medium(rng: random.Random, i: int) -> dict[str, Any]:
    lines = []
    for n in range(rng.randint(18, 26)):
        name, price = rng.choice(ITEMS)
        qty = rng.randint(1, 12)
        lines.append(
            {
                "line": n + 1,
                "sku": f"SKU-{rng.randint(10000, 99999)}",
                "description": name,
                "qty": qty,
                "unit_price": price,
                "amount": round(qty * price, 2),
            }
        )
    total = round(sum(line["amount"] for line in lines), 2)
    po_total = total if i % 3 else round(total * rng.uniform(0.6, 0.9), 2)  # every 3rd invoice exceeds its PO
    history = [
        {
            "invoice": f"INV-{rng.randint(10000, 99999)}",
            "total": round(rng.uniform(500, 40000), 2),
            "status": rng.choice(["paid", "paid", "paid", "disputed"]),
        }
        for _ in range(6)
    ]
    if i % 4 == 0:  # every 4th invoice duplicates a previous one
        history.append({"invoice": f"INV-{20000 + i}", "total": total, "status": "paid"})
    return {
        "state": {
            "invoice": {
                "number": f"INV-{20000 + i}",
                "vendor": rng.choice(["Acme Corp", "Globex", "Initech", "Umbrella"]),
                "currency": "USD",
                "issued": f"2026-09-{1 + i % 28:02d}",
                "due_days": rng.choice([15, 30, 45]),
                "lines": lines,
                "total": total,
            },
            "purchase_order": {"number": f"PO-{rng.randint(1000, 9999)}", "approved_total": po_total},
            "vendor_history": history,
        },
        "questions": {
            "action": {
                "type": "choice",
                "instructions": "What should accounts payable do with this invoice?",
                "criteria": {
                    "approve": "Matches the purchase order and looks legitimate",
                    "hold": "Needs clarification from the vendor before paying",
                    "reject": "Duplicate or invalid, do not pay",
                    "escalate": "Exceeds the approved purchase order and needs a manager",
                },
            },
            "duplicate": {"type": "noul", "instructions": "Has this exact invoice already been paid?"},
            "po_match": {
                "type": "noul",
                "instructions": "Is the invoice total within the approved purchase order total?",
            },
            "risk": {
                "type": "score",
                "instructions": "How risky is paying this invoice?",
                "criteria": ["None", "Low", "Medium", "High", "Critical"],
            },
        },
    }


# ---------------------------------------------------------------------------------------------
# large: log incident triage
# ---------------------------------------------------------------------------------------------

PATHS = ["/", "/login", "/api/v2/orders", "/api/v2/users/me", "/static/app.js", "/health", "/api/v2/export"]
USERS = ["alice", "bob", "carol", "dave", "erin", "frank", "grace", "heidi"]


def _log_line(rng: random.Random, t: int, kind: str) -> str:
    ts = f"2026-10-01T03:{t // 60 % 60:02d}:{t % 60:02d}Z"
    if kind == "auth_fail":
        return f"{ts} sshd[{rng.randint(1000, 9999)}]: Failed password for {rng.choice(USERS + ['root', 'admin'])} from 203.0.113.{rng.randint(2, 250)} port {rng.randint(30000, 65000)}"
    if kind == "auth_ok":
        return f"{ts} sshd[{rng.randint(1000, 9999)}]: Accepted publickey for {rng.choice(USERS)} from 10.0.{rng.randint(0, 9)}.{rng.randint(2, 250)}"
    if kind == "export":
        return f"{ts} nginx: 10.0.4.17 GET /api/v2/export?table=customers&offset={rng.randint(0, 900) * 1000} 200 {rng.randint(4, 9)}MB"
    return (
        f"{ts} nginx: 10.0.{rng.randint(0, 9)}.{rng.randint(2, 250)} {rng.choice(['GET', 'GET', 'POST'])} "
        f"{rng.choice(PATHS)} {rng.choice([200, 200, 200, 304, 404])} {rng.randint(120, 9000)}B"
    )


def _large(rng: random.Random, i: int) -> dict[str, Any]:
    scenario = ["benign", "brute_force", "data_exfiltration"][i % 3]
    lines = []
    for t in range(80):
        kind = "web" if rng.random() < 0.8 else "auth_ok"
        if scenario == "brute_force" and 20 <= t < 60 and rng.random() < 0.7:
            kind = "auth_fail"
        if scenario == "data_exfiltration" and 30 <= t < 70 and rng.random() < 0.5:
            kind = "export"
        lines.append(_log_line(rng, t, kind))
    return {
        "state": {"host": f"web-{rng.randint(1, 40):02d}.prod", "window": "03:00-03:02 UTC", "log": "\n".join(lines)},
        "questions": {
            "incident": {
                "type": "choice",
                "instructions": "What best describes the activity in this log window?",
                "criteria": {
                    "benign": "Normal traffic, nothing suspicious",
                    "brute_force": "Repeated failed logins from external addresses",
                    "data_exfiltration": "Unusual bulk export of data",
                    "malware": "Signs of malicious code running on the host",
                    "misconfiguration": "Errors caused by a configuration change",
                },
            },
            "severity": {
                "type": "score",
                "instructions": "How severe is this?",
                "criteria": ["Informational", "Low", "Medium", "High", "Critical"],
            },
            "page_oncall": {"type": "noul", "instructions": "Should the on-call engineer be paged now?"},
            "external_source": {
                "type": "noul",
                "instructions": "Does the suspicious activity come from outside the network?",
            },
        },
    }


BUILDERS = {"small": _small, "medium": _medium, "large": _large}


def build(name: str, n: int = 24) -> list[dict[str, Any]]:
    if name not in BUILDERS:
        raise SystemExit(f"unknown workload {name!r}, expected one of {sorted(BUILDERS)}")
    rng = random.Random(f"{SEED}-{name}")
    return [BUILDERS[name](rng, i) for i in range(n)]


if __name__ == "__main__":
    import json
    import sys

    print(json.dumps(build(sys.argv[1] if len(sys.argv) > 1 else "small", 1)[0], indent=2))
