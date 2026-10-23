"""Contrôles de cohérence de la documentation (TODO §95, lot D1 du plan de remédiation).

Lancé par scripts/check-docs.sh (et par le job CI « docs »). Hors ligne, sans dépendance hors
bibliothèque standard. Chaque contrôle renvoie une liste de fautes ; le script échoue si l'une
d'elles n'est pas vide.

  links       liens Markdown relatifs vers un fichier ou dossier inexistant
  skills      copies générées de skills (.claude/skills, .cursor/skills) divergentes de leur source
  todo-refs   « TODO §N » / « décision n° N » cité sans section correspondante dans le registre
  decision-ids identifiant de décision (D-…, DD-…) cité par le code sans définition dans la doc
  versions    version citée dans le README incohérente avec le pom.xml
"""

import filecmp
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET

SKILL_TREES = (".claude", ".cursor", ".agents")
SKIP_DIRS = {".git", "node_modules", "target", "dist", "test-results", "playwright-report"} | set(SKILL_TREES)
CODE_DIRS = ("src", "frontend/src", "frontend/e2e", "frontend/e2e-real", "frontend/portal", "scripts")
CODE_EXT = (".java", ".ts", ".vue", ".js", ".sh", ".py", ".yml", ".yaml", ".xml", ".sql", ".properties", ".html")
FENCE = re.compile(r"^\s*(```|~~~)")
INLINE_CODE = re.compile(r"`[^`\n]*`")
LINK = re.compile(r"\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
TODO_REF = re.compile(r"TODO\s*§\s*(\d+)")
DECISION_NO = re.compile(r"[Dd]écision\s+n°\s*(\d+)")
DECISION_ID = re.compile(r"(?<![A-Za-z0-9_-])(DD?-[A-Z][A-Z0-9]*(?:-[A-Z0-9]+)+)(?![A-Za-z0-9_-])")
DECISIONS_DIR = "docs/decisions"
ID_INDEX = "docs/decisions/identifiants.md"


def files(root, predicate):
    """Fichiers suivis par git (ou parcours du disque hors dépôt git, pour l'auto-test)."""
    try:
        out = subprocess.run(["git", "-C", root, "ls-files", "-z"], capture_output=True, check=True).stdout
        paths = [p.decode() for p in out.split(b"\0") if p]
        if not paths:
            raise subprocess.CalledProcessError(1, "git")
    except (subprocess.CalledProcessError, FileNotFoundError):
        paths = []
        for dirpath, dirnames, filenames in os.walk(root):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for name in filenames:
                paths.append(os.path.relpath(os.path.join(dirpath, name), root))
    return sorted(p for p in paths if predicate(p) and os.path.isfile(os.path.join(root, p)))


def in_skill_tree(path):
    return path.split("/", 1)[0] in SKILL_TREES


def markdown_files(root):
    return files(root, lambda p: p.endswith(".md") and not in_skill_tree(p))


def prose_lines(text):
    """Lignes hors blocs de code, code en ligne retiré."""
    fenced = False
    for number, line in enumerate(text.splitlines(), 1):
        if FENCE.match(line):
            fenced = not fenced
            continue
        if not fenced:
            yield number, INLINE_CODE.sub("", line)


def read(root, path):
    with open(os.path.join(root, path), encoding="utf-8", errors="replace") as handle:
        return handle.read()


# --- links -------------------------------------------------------------------------------------

def check_links(root):
    faults = []
    for path in markdown_files(root):
        base = os.path.dirname(path)
        for number, line in prose_lines(read(root, path)):
            for target in LINK.findall(line):
                if re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", target) or target.startswith("#"):
                    continue
                target = re.sub(r"%20", " ", target.split("#", 1)[0].split("?", 1)[0])
                if not target:
                    continue
                resolved = os.path.normpath(os.path.join(root, base, target))
                if not os.path.exists(resolved):
                    faults.append(f"{path}:{number}: lien cassé -> {target}")
    return faults


# --- skills ------------------------------------------------------------------------------------

def tree_differences(left, right):
    comparison = filecmp.dircmp(left, right)
    differences = [f"{os.path.join(left, n)} absent de {right}" for n in comparison.left_only]
    differences += [f"{os.path.join(right, n)} absent de {left}" for n in comparison.right_only]
    _, mismatch, errors = filecmp.cmpfiles(left, right, comparison.common_files, shallow=False)
    differences += [f"{os.path.join(left, n)} diffère de {os.path.join(right, n)}" for n in mismatch + errors]
    for sub in comparison.common_dirs:
        differences += tree_differences(os.path.join(left, sub), os.path.join(right, sub))
    return differences


def check_skills(root):
    faults = []
    copies = [os.path.join(root, d, "skills") for d in (".claude", ".cursor")]
    copies = [c for c in copies if os.path.isdir(c)]
    if len(copies) == 2:
        faults += tree_differences(*copies)
    for source_dir in (".project-skills", ".agents/skills"):
        source_root = os.path.join(root, source_dir)
        if not os.path.isdir(source_root):
            continue
        for name in sorted(os.listdir(source_root)):
            source = os.path.join(source_root, name)
            if not os.path.isdir(source):
                continue
            for copy_root in copies:
                copy = os.path.join(copy_root, name)
                if not os.path.isdir(copy):
                    faults.append(f"{os.path.relpath(copy, root)} manquant (source {source_dir}/{name})")
                else:
                    faults += tree_differences(source, copy)
    return [os.path.relpath(f, root) if f.startswith(root) else f.replace(root + os.sep, "") for f in faults]


# --- todo-refs ---------------------------------------------------------------------------------

SELF = "scripts/check_docs.py"  # ses fixtures d'auto-test citent volontairement des références inexistantes


def code_files(root):
    return files(root, lambda p: p.startswith(tuple(d + "/" for d in CODE_DIRS)) and p.endswith(CODE_EXT)
                 and p != SELF)


def registry_numbers(root):
    """Numéros définis : fichiers docs/decisions/NNN-*.md, titres « N. » et lignes « | N | » de TODO.md."""
    numbers = set()
    decisions = os.path.join(root, DECISIONS_DIR)
    if os.path.isdir(decisions):
        for name in os.listdir(decisions):
            match = re.match(r"^(\d{3})-.+\.md$", name)
            if match:
                numbers.add(int(match.group(1)))
    todo = os.path.join(root, "TODO.md")
    if os.path.isfile(todo):
        for _, line in prose_lines(read(root, "TODO.md")):
            match = re.match(r"^#{2,4} (?:\[[ x]\] )?(\d+)\.", line) or re.match(r"^\| *(\d+) *\|", line)
            if match:
                numbers.add(int(match.group(1)))
    return numbers


def check_todo_refs(root):
    defined = registry_numbers(root)
    faults = []
    sources = code_files(root) + markdown_files(root)
    for path in sources:
        text = read(root, path)
        for pattern, label in ((TODO_REF, "TODO §"), (DECISION_NO, "décision n° ")):
            for match in pattern.finditer(text):
                number = int(match.group(1))
                if number not in defined:
                    line = text.count("\n", 0, match.start()) + 1
                    faults.append(f"{path}:{line}: {label}{number} sans section dans le registre")
    return faults


# --- decision-ids ------------------------------------------------------------------------------

def defined_ids(root):
    """Identifiants définis : l'index docs/decisions/identifiants.md s'il existe, sinon toute la doc."""
    if os.path.isfile(os.path.join(root, ID_INDEX)):
        sources = [ID_INDEX]
    else:
        sources = markdown_files(root)
    found = set()
    for path in sources:
        found.update(DECISION_ID.findall(read(root, path)))
    return found


def check_decision_ids(root):
    defined = defined_ids(root)
    faults = []
    for path in code_files(root):
        text = read(root, path)
        for match in DECISION_ID.finditer(text):
            if match.group(1) not in defined:
                line = text.count("\n", 0, match.start()) + 1
                faults.append(f"{path}:{line}: {match.group(1)} cité sans définition"
                              f" ({ID_INDEX if os.path.isfile(os.path.join(root, ID_INDEX)) else 'doc'})")
    return faults


# --- versions ----------------------------------------------------------------------------------

README_VERSIONS = (
    # (motif dans le README, propriété du pom ; None = version du parent Spring Boot)
    (r"Spring Boot (\d+(?:\.\d+){0,2}(?:\.x)?)", None),
    (r"Java (\d+)(?: LTS)?", "java.version"),
    (r"Flowable (\d+(?:\.\d+){0,2}(?:\.x)?)", "flowable.version"),
    (r"Flying Saucer (\d+(?:\.\d+){0,2}(?:\.x)?)", "flying-saucer.version"),
    (r"jsoup (\d+(?:\.\d+){0,2}(?:\.x)?)", "jsoup.version"),
)


def pom_versions(root):
    tree = ET.parse(os.path.join(root, "pom.xml"))
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    versions = {None: tree.findtext("m:parent/m:version", namespaces=ns)}
    properties = tree.find("m:properties", ns)
    if properties is not None:
        for prop in properties:
            versions[prop.tag.split("}", 1)[1]] = (prop.text or "").strip()
    return versions


def version_matches(cited, actual):
    cited_parts = [p for p in cited.split(".") if p != "x"]
    return actual.split(".")[: len(cited_parts)] == cited_parts


def check_versions(root):
    if not (os.path.isfile(os.path.join(root, "README.md")) and os.path.isfile(os.path.join(root, "pom.xml"))):
        return []
    versions = pom_versions(root)
    faults = []
    for number, line in prose_lines(read(root, "README.md")):
        for pattern, prop in README_VERSIONS:
            for cited in re.findall(pattern, line):
                actual = versions.get(prop)
                if actual and not version_matches(cited, actual):
                    faults.append(f"README.md:{number}: {pattern.split(' ')[0]} {cited} ≠ pom.xml {actual}")
    return faults


CHECKS = {
    "links": check_links,
    "skills": check_skills,
    "todo-refs": check_todo_refs,
    "decision-ids": check_decision_ids,
    "versions": check_versions,
}


def run(root, names):
    failed = False
    for name in names:
        faults = CHECKS[name](root)
        status = "ok" if not faults else f"{len(faults)} faute(s)"
        print(f"[check-docs] {name}: {status}")
        for fault in faults[:200]:
            print(f"  {fault}")
        if len(faults) > 200:
            print(f"  … {len(faults) - 200} de plus")
        failed |= bool(faults)
    return 1 if failed else 0


# --- self-test ---------------------------------------------------------------------------------

def write(root, path, content):
    full = os.path.join(root, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8") as handle:
        handle.write(content)


def fixture(root):
    """Un mini-dépôt sain : chaque contrôle doit y passer."""
    write(root, "README.md", "Stack : Java 25 · Spring Boot 4.1.x · Flowable 8.0.x. Voir [guide](docs/guide.md).\n"
                             "```\n[pas un lien](absent.md)\n```\n")
    write(root, "docs/guide.md", "Retour au [README](../README.md#stack), [site](https://example.org).\n")
    write(root, "TODO.md", "## 12. Un point\n\n| § | Lot |\n|---|---|\n| 80 | lot |\n\n**D-ALPHA-ONE** — défini.\n")
    write(root, "pom.xml", '<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><version>4.1.1</version>'
                           "</parent><properties><java.version>25</java.version>"
                           "<flowable.version>8.0.0</flowable.version></properties></project>\n")
    write(root, "src/main/java/A.java", "// TODO §12, TODO §80, décision n° 12, D-ALPHA-ONE\nclass A {}\n")
    for tree in (".project-skills/demo", ".claude/skills/demo", ".cursor/skills/demo"):
        write(root, f"{tree}/SKILL.md", "# demo\n")


def self_test():
    """Prouve que chaque contrôle passe sur un dépôt sain et détecte la faute qu'on y injecte."""
    mutations = {
        "links": lambda r: write(r, "docs/guide.md", "Voir [disparu](gone.md).\n"),
        "skills": lambda r: write(r, ".claude/skills/demo/SKILL.md", "# demo modifié à la main\n"),
        "todo-refs": lambda r: write(r, "src/main/java/B.java", "// TODO §999\n"),
        "decision-ids": lambda r: write(r, "src/main/java/B.java", "// D-UNKNOWN-ID\n"),
        "versions": lambda r: write(r, "README.md", "Stack : Spring Boot 3.5.x.\n"),
    }
    ok = True
    for name, mutate in mutations.items():
        with tempfile.TemporaryDirectory() as root:
            fixture(root)
            clean = CHECKS[name](root)
            mutate(root)
            dirty = CHECKS[name](root)
        passed = not clean and bool(dirty)
        ok &= passed
        print(f"[self-test] {name}: {'ok' if passed else 'ÉCHEC'} (sain: {len(clean)} faute(s), muté: {len(dirty)})")
        for fault in clean:
            print(f"  inattendu : {fault}")
    return 0 if ok else 1


def main(argv):
    root = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
    if argv[:1] == ["--self-test"]:
        return self_test()
    names = argv or list(CHECKS)
    unknown = [n for n in names if n not in CHECKS]
    if unknown:
        print(f"contrôle inconnu : {', '.join(unknown)} (connus : {', '.join(CHECKS)})", file=sys.stderr)
        return 2
    return run(root, names)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
