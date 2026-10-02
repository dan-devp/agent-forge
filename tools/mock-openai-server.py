"""Minimal OpenAI-compatible server for testing without a real model.

Start:  python tools/mock-openai-server.py          (port 1234)
Use:    base-url http://localhost:1234/v1             (local)
        base-url http://host.docker.internal:1234/v1  (from the container)
        model    mock-local-7b

Replaces LM Studio / Ollama / vLLM in tests: same route POST /v1/chat/completions, same
response shape. Logs every request so it is visible which path and which model arrive.

Agent: the mock follows its system prompt just enough for a training to show progress.
It requests a tool only if the tool is offered, the system prompt speaks of tools
('Tool' or 'Werkzeug'), and the question needs it (time -> currentTime, 'geteilt' ->
divide, with the numbers from the question). Once tool results are present it answers with
them, so time-and-division passes. Asked for a capital, it answers 'Paris.' - unless the
system prompt asks for a full sentence that picks up the terms of the question. A rookie
with a one-line prompt therefore guesses and fails; a prompt that learned both rules passes.

Judges: a chat request whose user message has a '# Fragen' section gets one line per
question ('1: JA - ...'). POST /v1/systemone plays a TypeSafe decision model (Jev) and
answers every Noul question with a truth value. Both say yes when a word of five or
more letters from the question appears in the judged answer, else no - crude, but
deterministic, and it gives a verification both outcomes.

Coach: a request whose user message has '# Aktueller Systemprompt' is a training coach.
It adds one rule per call, read off the failed verdicts: tools first, then - for a missing
term - a useless 'answer briefly' that the gate rejects, and after that rejection the rule
that works. So a mock training shows an accepted step, a rejected one and a recovery.
"""
import json
import re
import sys
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

TOOL_RULE = ("Nutze die verfuegbaren Tools fuer Uhrzeit und Rechnungen, statt zu raten. "
             "Wo kein Tool noetig ist, rufst du keines auf.")
BRIEF_RULE = "Antworte knapp, ohne Einleitung."
SENTENCE_RULE = "Antworte in einem vollstaendigen Satz, der die Begriffe der Frage aufgreift."


def speaks_of_tools(system):
    return re.search(r"tool|werkzeug", system, re.I) is not None


def needed_tools(user):
    needed = []
    if re.search(r"spaet|spät|uhrzeit|wie viel uhr", user, re.I):
        needed.append("currentTime")
    if re.search(r"geteilt|rechne", user, re.I):
        needed.append("divide")
    return needed


def tool_args(name, user):
    if name == "currentTime":
        return {"timeZone": "Europe/Berlin"}
    if name == "divide":
        m = re.search(r"(\d+)\s*geteilt durch\s*(\d+)", user)
        return {"dividend": int(m.group(1)), "divisor": int(m.group(2))} if m else {"dividend": 100, "divisor": 7}
    return {}


def answer_from_tools(messages, system, user):
    results = [m.get("content") or "" for m in messages if m.get("role") == "tool"]
    if not results:
        if "Hauptstadt" in user:
            if re.search(r"vollstaendigen satz|begriffe der frage", system, re.I):
                return "Die Hauptstadt von Frankreich ist Paris."
            return "Paris."
        return "Antwort vom lokalen Mock-Modell."
    parts = []
    for r in results:
        if re.search(r"\d{4}-\d{2}-\d{2}", r):
            parts.append("Es ist %s." % r)
        elif "geteilt" in r:
            parts.append(r + ".")
        else:
            parts.append(r)
    return " ".join(parts)


def says_yes(question, answer):
    words = [w for w in re.findall(r"\w+", question.lower()) if len(w) >= 5]
    return any(w in answer.lower() for w in words)


def judge_lines(user):
    answer = user.split("# Antwort des Agenten", 1)[-1].split("# Fragen", 1)[0]
    questions = re.findall(r"(?m)^\s*(\d+)\.\s*(.+)$", user.split("# Fragen", 1)[1])
    return "\n".join("%s: %s - Mock-Judge" % (n, "JA" if says_yes(q, answer) else "NEIN")
                     for n, q in questions)


def coach_answer(brief):
    prompt = brief.split("<<<\n", 1)[1].split("\n>>>", 1)[0] if "<<<\n" in brief else ""
    failures = brief.split("# Gescheiterte Runs", 1)[1] if "# Gescheiterte Runs" in brief else ""
    failures = failures.split("# Bereits versucht", 1)[0].split("# Antwortformat", 1)[0]
    rejected = brief.split("# Bereits versucht", 1)[1] if "# Bereits versucht" in brief else ""

    if "tool-called" in failures and TOOL_RULE not in prompt:
        rule, why = TOOL_RULE, "Der Agent hat Uhrzeit und Rechnung geraten, tool-called sagte Nein."
    elif "contains" in failures and BRIEF_RULE not in prompt and "knapp" not in rejected:
        rule, why = BRIEF_RULE, "Die Antwort traf den gesuchten Begriff nicht, vielleicht war sie zu ausschweifend."
    elif "contains" in failures and SENTENCE_RULE not in prompt:
        rule, why = SENTENCE_RULE, "Die Antwort war ein einzelnes Wort, der Begriff aus der Frage fehlte (contains)."
    else:
        rule, why = "Pruefe deine Antwort, bevor du sie abschickst.", "Kein klarer Befund mehr."
    return "BEGRUENDUNG:\n- %s %s\nPROMPT:\n<<<\n%s\n\n%s\n>>>" % (rule, why, prompt.strip(), rule)


def system_one(req):
    state = req.get("state")
    answer = state.get("antwort", "") if isinstance(state, dict) else str(state)
    answers = {}
    for key, q in (req.get("questions") or {}).items():
        text = q.get("instructions") if isinstance(q, dict) else q
        answers[key] = {"type": "noul", "noul": 0.9 if says_yes(str(text), str(answer)) else 0.2}
    return {"model": req.get("model") or "jev-mock", "answers": answers,
            "usage": {"input_tokens": 40, "output_tokens": len(answers)}}


def text_of(content):
    if isinstance(content, list):
        return "".join(part.get("text", "") for part in content if isinstance(part, dict))
    return content or ""


class Handler(BaseHTTPRequestHandler):
    def _json(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _body(self):
        # The TypeSafe SDK (JDK HttpClient) streams its body chunked, without Content-Length.
        if "chunked" in (self.headers.get("Transfer-Encoding") or "").lower():
            data = b""
            while True:
                size = int(self.rfile.readline().strip().split(b";")[0], 16)
                if size == 0:
                    self.rfile.readline()
                    return data
                data += self.rfile.read(size)
                self.rfile.readline()
        return self.rfile.read(int(self.headers.get("Content-Length", 0)))

    def do_GET(self):
        if self.path.rstrip("/").endswith("/models"):
            self._json(200, {"object": "list", "data": [
                {"id": "mock-local-7b", "object": "model", "created": 0, "owned_by": "mock"}]})
        else:
            self._json(404, {"error": {"message": "not found"}})

    def do_POST(self):
        req = json.loads(self._body() or b"{}")
        if self.path.rstrip("/").endswith("/systemone"):
            reply = system_one(req)
            print("REQ path=%s model=%s questions=%s answers=%s" % (
                self.path, req.get("model"), len(req.get("questions") or {}),
                {k: v["noul"] for k, v in reply["answers"].items()}), flush=True)
            self._json(200, reply)
            return
        messages = req.get("messages", [])
        tools = req.get("tools") or []
        offered = [t["function"]["name"] for t in tools]
        has_results = any(m.get("role") == "tool" for m in messages)
        system = "\n".join(text_of(m.get("content")) for m in messages if m.get("role") == "system")
        user = next((text_of(m.get("content")) for m in reversed(messages) if m.get("role") == "user"), "")
        coach = "# Aktueller Systemprompt" in user
        print("REQ path=%s model=%s msgs=%s tools=%s results=%s%s" % (
            self.path, req.get("model"), len(messages), offered, has_results, " coach" if coach else ""),
            flush=True)

        wanted = [n for n in needed_tools(user) if n in offered] if speaks_of_tools(system) else []
        if coach:
            message = {"role": "assistant", "content": coach_answer(user)}
            finish = "stop"
        elif wanted and not has_results:
            calls = [{
                "id": "call_%d" % i,
                "type": "function",
                "function": {"name": name, "arguments": json.dumps(tool_args(name, user))},
            } for i, name in enumerate(wanted)]
            message = {"role": "assistant", "content": None, "tool_calls": calls}
            finish = "tool_calls"
        else:
            content = judge_lines(user) if "# Fragen" in user else answer_from_tools(messages, system, user)
            message = {"role": "assistant", "content": content}
            finish = "stop"

        self._json(200, {
            "id": "chatcmpl-mock",
            "object": "chat.completion",
            "created": int(time.time()),
            "model": req.get("model", "mock-local-7b"),
            "choices": [{"index": 0, "message": message, "finish_reason": finish}],
            "usage": {"prompt_tokens": 11, "completion_tokens": 7, "total_tokens": 18},
        })

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 1234
    print("Mock OpenAI server on http://localhost:%d/v1" % port, flush=True)
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
