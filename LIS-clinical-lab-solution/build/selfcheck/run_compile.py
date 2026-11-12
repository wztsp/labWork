"""Full explicit javac compile of the reagent module (no mkdir/sed needed)."""
import os
import subprocess

BASE = "build/selfcheck"
ARGS_IN = os.path.join(BASE, "javac-reagent.args")
OUTDIR = os.path.join(BASE, "reagent-classes-e5")
ARGS_OUT = os.path.join(BASE, "javac-reagent-e5.args")
LOG = os.path.join(BASE, "e5.log")

os.makedirs(OUTDIR, exist_ok=True)

with open(ARGS_IN, encoding="utf-8") as f:
    lines = f.read().splitlines()

newlines = []
for ln in lines:
    if ln.strip() == "build/selfcheck/reagent-classes":
        newlines.append(OUTDIR)
    else:
        newlines.append(ln)

with open(ARGS_OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(newlines))

with open(LOG, "w", encoding="utf-8") as lf:
    proc = subprocess.run(["javac", "@" + ARGS_OUT], stdout=lf, stderr=subprocess.STDOUT)

print("EXITCODE=%d" % proc.returncode)

cnt = 0
for root, _dirs, files in os.walk(OUTDIR):
    for fn in files:
        if fn.endswith(".class"):
            cnt += 1
print("classes=%d" % cnt)

# error line count
with open(LOG, encoding="utf-8", errors="ignore") as f:
    log_text = f.read()
errs = log_text.count("error:")
print("error_lines=%d" % errs)
if proc.returncode != 0:
    tail = "\n".join(log_text.splitlines()[-20:])
    print("--- LOG TAIL ---")
    print(tail)
