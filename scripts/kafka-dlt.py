#!/usr/bin/env python3
"""Inspect / replay Kafka dead-letter topics through the waylo-kafka container (fallback to the admin API).

  scripts/kafka-dlt.py list order-events [--max 20]
  scripts/kafka-dlt.py replay order-events --partition 0 --offset 5

list prints key, original topic/partition/offset and exception headers (payload masked and cut).
replay republishes one DLT record to its original topic. Consumers are idempotent (processed_events).
"""
import argparse
import re
import subprocess
import sys

BOOT = "kafka:29092"
EMAIL = re.compile(r"([A-Za-z0-9_+-])[A-Za-z0-9._+-]*@([A-Za-z0-9-]+[.])")


def dlt(topic):
    return topic if topic.endswith(".DLT") else topic + ".DLT"


def mask(s):
    s = EMAIL.sub(r"\1***@\2", s)
    return s if len(s) <= 300 else s[:300] + "...(truncated)"


def consume(topic, partition, offset, max_messages):
    cmd = ["docker", "exec", "waylo-kafka", "kafka-console-consumer", "--bootstrap-server", BOOT,
           "--topic", dlt(topic), "--max-messages", str(max_messages), "--timeout-ms", "8000",
           "--property", "print.key=true", "--property", "print.headers=true",
           "--property", "print.partition=true", "--property", "print.offset=true",
           "--property", "print.timestamp=true", "--property", "key.separator= | "]
    if offset is None:
        cmd += ["--from-beginning"]
    else:
        cmd += ["--partition", str(partition), "--offset", str(offset)]
    out = subprocess.run(cmd, capture_output=True, text=True)
    return [l for l in out.stdout.splitlines() if l.strip()]


def cmd_list(a):
    for line in consume(a.topic, None, None, a.max):
        print(mask(line))


def cmd_replay(a):
    lines = consume(a.topic, a.partition, a.offset, 1)
    if not lines:
        sys.exit("no DLT record at %s-%s@%s" % (dlt(a.topic), a.partition, a.offset))
    # line: ts | Partition:p | Offset:o | headers | key | value (separators as printed by the console consumer)
    value = lines[0].split("\t")[-1]
    key = None
    m = re.search(r"\s(\S+) \| ", lines[0])
    if m:
        key = m.group(1)
    cmd = ["docker", "exec", "-i", "waylo-kafka", "kafka-console-producer", "--bootstrap-server", BOOT,
           "--topic", a.topic]
    if key:
        cmd += ["--property", "parse.key=true", "--property", "key.separator=|"]
    payload = ((key + "|") if key else "") + value + "\n"
    r = subprocess.run(cmd, input=payload, text=True, capture_output=True)
    if r.returncode != 0:
        sys.exit("replay failed: " + r.stderr)
    print("replayed to", a.topic, "(console producer drops headers, e.g. __TypeId__: prefer the admin API for typed events)")


p = argparse.ArgumentParser()
sub = p.add_subparsers(required=True)
l = sub.add_parser("list")
l.add_argument("topic")
l.add_argument("--max", type=int, default=20)
l.set_defaults(fn=cmd_list)
r = sub.add_parser("replay")
r.add_argument("topic")
r.add_argument("--partition", type=int, required=True)
r.add_argument("--offset", type=int, required=True)
r.set_defaults(fn=cmd_replay)
a = p.parse_args()
a.fn(a)
