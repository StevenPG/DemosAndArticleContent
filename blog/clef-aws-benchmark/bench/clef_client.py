"""Tiny clients for the two places Clef-flash runs in this project.

Both speak the TypeSafe/Jev System One request body:

    {"model": "clef-flash", "state": ..., "questions": {id: {type, instructions, criteria}}}

and return the same response body:

    {"model": ..., "answers": {id: {...}}, "usage": {"input_tokens": N, "output_tokens": 0}}

- ``LlamaCppClient`` calls ``POST /v1/systemone`` on a local ``llama-server`` (llama.cpp b11390+).
- ``WorkersAIClient`` calls Cloudflare's hosted ``@cf/cloudflare/clef-flash``.
"""

from __future__ import annotations

import os
import time
from dataclasses import dataclass
from typing import Any

import httpx

MODEL_NAME = "clef-flash"
WORKERS_AI_MODEL = "@cf/cloudflare/clef-flash"
# Cloudflare list price at launch (Oct 2026): $0.09 per million input tokens, no output tokens.
WORKERS_AI_USD_PER_M_INPUT_TOKENS = 0.09


@dataclass
class Decision:
    """One answered request: the response body plus what we measured around it."""

    body: dict[str, Any]
    latency_s: float

    @property
    def answers(self) -> dict[str, Any]:
        return self.body.get("answers", {})

    @property
    def input_tokens(self) -> int:
        return int(self.body.get("usage", {}).get("input_tokens", 0))


def _unwrap(body: dict[str, Any]) -> dict[str, Any]:
    # Workers AI REST responses are documented as unwrapped, but early write-ups show the usual
    # {"result": {...}, "success": true} envelope. Accept both.
    if "answers" not in body and isinstance(body.get("result"), dict):
        return body["result"]
    return body


def _request_body(request: dict[str, Any]) -> dict[str, Any]:
    body = {"model": MODEL_NAME}
    body.update({k: v for k, v in request.items() if k in ("model", "state", "questions", "images")})
    return body


class LlamaCppClient:
    def __init__(self, base_url: str = "http://127.0.0.1:8080", timeout_s: float = 600.0) -> None:
        self.base_url = base_url.rstrip("/")
        self.url = f"{self.base_url}/v1/systemone"
        self.timeout_s = timeout_s
        self.name = "llama.cpp"

    def headers(self) -> dict[str, str]:
        return {"Content-Type": "application/json"}

    def decide(self, request: dict[str, Any], client: httpx.Client | None = None) -> Decision:
        own = client is None
        client = client or httpx.Client(timeout=self.timeout_s)
        try:
            start = time.perf_counter()
            response = client.post(self.url, json=_request_body(request), headers=self.headers())
            latency = time.perf_counter() - start
            response.raise_for_status()
            return Decision(_unwrap(response.json()), latency)
        finally:
            if own:
                client.close()

    async def adecide(self, request: dict[str, Any], client: httpx.AsyncClient) -> Decision:
        start = time.perf_counter()
        response = await client.post(self.url, json=_request_body(request), headers=self.headers())
        latency = time.perf_counter() - start
        response.raise_for_status()
        return Decision(_unwrap(response.json()), latency)

    def wait_until_ready(self, timeout_s: float = 900.0) -> float:
        """Poll /health until the model is loaded. Returns seconds waited."""
        start = time.perf_counter()
        while time.perf_counter() - start < timeout_s:
            try:
                if httpx.get(f"{self.base_url}/health", timeout=5).status_code == 200:
                    return time.perf_counter() - start
            except httpx.HTTPError:
                pass
            time.sleep(0.5)
        raise TimeoutError(f"{self.base_url} not healthy after {timeout_s:.0f}s")


class WorkersAIClient(LlamaCppClient):
    def __init__(
        self,
        account_id: str | None = None,
        api_token: str | None = None,
        model: str = WORKERS_AI_MODEL,
        timeout_s: float = 120.0,
    ) -> None:
        account_id = account_id or os.environ.get("CLOUDFLARE_ACCOUNT_ID")
        api_token = api_token or os.environ.get("CLOUDFLARE_API_TOKEN")
        if not account_id or not api_token:
            raise SystemExit("set CLOUDFLARE_ACCOUNT_ID and CLOUDFLARE_API_TOKEN for --target workers-ai")
        self.url = f"https://api.cloudflare.com/client/v4/accounts/{account_id}/ai/run/{model}"
        self.base_url = self.url
        self.api_token = api_token
        self.timeout_s = timeout_s
        self.name = "workers-ai"

    def headers(self) -> dict[str, str]:
        return {"Content-Type": "application/json", "Authorization": f"Bearer {self.api_token}"}

    def wait_until_ready(self, timeout_s: float = 0.0) -> float:
        return 0.0


def make_client(target: str, url: str) -> LlamaCppClient:
    if target == "workers-ai":
        return WorkersAIClient()
    return LlamaCppClient(url)
