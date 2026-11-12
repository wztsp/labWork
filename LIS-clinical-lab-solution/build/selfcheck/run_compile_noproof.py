"""Negative-proof compile: drop the module's own stale jar from classpath.

If the reagent module compiled against its own previously-built jar, that would
be an illegal implicit dependency. This compile removes lis-service-reagent-1.0.0.jar
from the classpath; a clean result proves the source set is self-contained.
"""
import os
import subprocess

BASE = "build/selfcheck"
ARGS_IN = os.path.join(BASE, "javac-reagent.args")
OUTDIR = os.path.join(BASE, "reagent-classes-e5np")
ARGS_OUT = os.path.join(BASE, "javac-reagent-e5np.args")
LOG = os.path.join(BASE, "e5np.log")

SELF_JAR = "C:/Users/Administrator/.m2/repository/com/lis/lis-service-reagent/1.0.0/lis-service-reagent-1.0.0.jar"

os.makedirs(OUTDIR, exist_ok=True)

with open(ARGS_IN, encoding="utf-8") as f:
    text = f.read()

# drop the self jar whether it is preceded or followed by a separator
text = text.replace(";" + SELF_JAR, "").replace(SELF_JAR + ";", "")

lines = text.splitlines()
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
with open(LOG, encoding="utf-8", errors="ignore") as f:
    log_text = f.read()
print("error_lines=%d" % log_text.count("error:"))
if proc.returncode != 0:
    print("--- LOG TAIL ---")
    print("\n".join(log_text.splitlines()[-20:]))
