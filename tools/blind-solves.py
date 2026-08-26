#!/usr/bin/env python3
"""Pull the blind solves out of the debug app, as fixtures for the reconstruction tests.

The one thing that holds a blind reconstruction to the truth is that each algorithm's spelled moves
shift exactly the slots its own name names -- see AlgorithmSlots and SpelledAsNamedTest. That check
is only worth as many solves as it is run over, and the solves that break it are real ones off a
real cube, not anything that can be written by hand. This is how the corpus grows: pull the debug
app's history, print the blind solves as Java, paste the interesting ones into RecordedBlindSolve
and add them to its ALL, then run

    ./gradlew :nanotimer:testDebugUnitTest --tests '*SpelledAsNamedTest*'

Only the scramble and the move stream are taken. Everything else the tests need -- the method, the
steps, the names -- is read again from those two, which is what makes a pulled solve a fixture and
not a snapshot of whatever the app believed on the day.

Usage:  python tools/blind-solves.py [--limit N] [--db FILE] [--package com.cube.nanotimer.debug]

With no --db it pulls one over adb, which needs the debug build installed and a device attached.
"""

import argparse
import os
import sqlite3
import subprocess
import sys
import tempfile

DB_NAME = "nanoTimerDB"
LINE_WIDTH = 96


def pull(package, into):
    """The debug app's database, copied out through run-as."""
    with open(into, "wb") as out:
        done = subprocess.run(
            ["adb", "exec-out", "run-as", package, "cat", "databases/" + DB_NAME],
            stdout=out, stderr=subprocess.PIPE)
    if done.returncode != 0 or os.path.getsize(into) == 0:
        sys.exit("could not read the database: " + done.stderr.decode(errors="replace").strip())
    return into


def solves(db, limit):
    """Every blind solve with a move stream and a scramble, newest first."""
    connection = sqlite3.connect(db)
    try:
        rows = connection.execute(
            "SELECT id, time, scramble, smartcube_moves FROM timehistory"
            " WHERE smartcube_method = 'BLD' AND smartcube_moves IS NOT NULL"
            " AND scramble IS NOT NULL AND scramble != ''"
            " ORDER BY id DESC LIMIT ?", (limit,)).fetchall()
    finally:
        connection.close()
    return rows


def java_string(text, indent):
    """A long line of moves as the concatenation the fixture file is written in."""
    lines, line = [], ""
    for token in text.split():
        if line and len(line) + len(token) + 1 > LINE_WIDTH - len(indent):
            lines.append(line + " ")
            line = token
        else:
            line = (line + " " + token).strip()
    lines.append(line)
    joined = ('"' + lines[0] + '"')
    for more in lines[1:]:
        joined += "\n" + indent + '    + "' + more + '"'
    return joined


def name_of(solve_id, moves):
    """A fixture name that says which solve it is, since the id is all we know about it."""
    return "%d%s" % (solve_id, "" if moves.startswith("[") else "_NOGRIP")


def main():
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--limit", type=int, default=20, help="how many solves to print")
    parser.add_argument("--db", help="a database already pulled, instead of pulling one")
    parser.add_argument("--package", default="com.cube.nanotimer.debug")
    args = parser.parse_args()

    db = args.db
    temporary = None
    if db is None:
        temporary = tempfile.NamedTemporaryFile(suffix=".db", delete=False)
        temporary.close()
        db = pull(args.package, temporary.name)
    try:
        found = solves(db, args.limit)
    finally:
        if temporary is not None:
            os.unlink(temporary.name)

    if not found:
        sys.exit("no blind solve in that database carries both a scramble and a move stream")
    print("// %d blind solve%s, newest first. One stored before the grip was kept opens with no [y]"
          % (len(found), "" if len(found) == 1 else "s"))
    print("// and cannot be read again at all -- StoredSolveReplay refuses it rather than"
          " guessing.\n")
    for solve_id, time_ms, scramble, moves in found:
        name = name_of(solve_id, moves)
        print("  /** Solve %d, %.2fs. */" % (solve_id, time_ms / 1000.0))
        print("  static final String SCRAMBLE_%s = \"%s\";" % (name, scramble.strip()))
        print("  static final String MOVES_%s =\n      %s;\n"
              % (name, java_string(moves.strip(), "      ")))
    print("  // and in ALL:")
    for solve_id, _, _, moves in found:
        name = name_of(solve_id, moves)
        print("    {SCRAMBLE_%s, MOVES_%s}," % (name, name))


if __name__ == "__main__":
    main()
