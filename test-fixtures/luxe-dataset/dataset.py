#!/usr/bin/env python3
"""Installs, removes and checks the luxe quality dataset of content-governance on a running Jahia.

    export JAHIA_URL=https://my-jahia.example.com JAHIA_USER=root JAHIA_PASSWORD=<password>
    python3 test-fixtures/luxe-dataset/dataset.py install   # or reset: the same, it removes first
    python3 test-fixtures/luxe-dataset/dataset.py check     # runs the checks, compares with expected.json
    python3 test-fixtures/luxe-dataset/dataset.py remove

It needs Python 3 and no package. install and remove run a Groovy script through the provisioning API,
so the user needs the server administration. check reads the findings through GraphQL.
"""
import base64
import json
import os
import sys
import urllib.error
import urllib.request
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))


def settings():
    url = os.environ.get("JAHIA_URL", "http://localhost:8080").rstrip("/")
    user = os.environ.get("JAHIA_USER", "root")
    password = os.environ.get("JAHIA_PASSWORD") or os.environ.get("SUPER_USER_PASSWORD")
    if not password:
        sys.exit("Set JAHIA_PASSWORD to the password of JAHIA_USER (root by default).")
    token = base64.b64encode((user + ":" + password).encode()).decode()
    return url, {"Authorization": "Basic " + token}


def provision(script_name):
    """Runs a Groovy script of this folder through the provisioning API, and prints what it did."""
    url, auth = settings()
    with open(os.path.join(HERE, script_name), encoding="utf-8") as source:
        script = source.read()
    boundary = uuid.uuid4().hex
    parts = [("script", "dataset.yaml", "application/yaml", '- executeScript: "%s"\n' % script_name),
             ("file", script_name, "text/plain", script)]
    body = b""
    for field, filename, content_type, content in parts:
        body += ("--%s\r\nContent-Disposition: form-data; name=\"%s\"; filename=\"%s\"\r\nContent-Type: %s\r\n\r\n"
                 % (boundary, field, filename, content_type)).encode() + content.encode("utf-8") + b"\r\n"
    body += ("--%s--\r\n" % boundary).encode()
    headers = dict(auth, **{"Content-Type": "multipart/form-data; boundary=" + boundary})
    request = urllib.request.Request(url + "/modules/api/provisioning", data=body, method="POST", headers=headers)
    try:
        urllib.request.urlopen(request, timeout=300).read()
    except urllib.error.HTTPError as error:
        sys.exit("The script %s failed with HTTP %s: %s" % (script_name, error.code, error.read().decode()[:2000]))


# The provisioning API answers 200 when a script throws, so the driver reads what the script left. The
# terms are the last thing that install.groovy writes.
EDIT_SQL = ("SELECT * FROM [nt:base] WHERE ISSAMENODE('/sites/luxe/contents/quality-lab-terms')"
            " OR ISSAMENODE('/sites/luxe/home/quality-lab')")
LIVE_SQL = "SELECT * FROM [nt:base] WHERE ISSAMENODE('/sites/luxe/home/quality-lab')"
STATE = """query($edit: String!, $live: String!) {
  edit: jcr { nodesByQuery(query: $edit, queryLanguage: SQL2) { nodes { path } } }
  live: jcr(workspace: LIVE) { nodesByQuery(query: $live, queryLanguage: SQL2) { nodes { path } } } }"""


def state():
    data = graphql(STATE, {"edit": EDIT_SQL, "live": LIVE_SQL})
    return {node["path"] for node in data["edit"]["nodesByQuery"]["nodes"]}, bool(data["live"]["nodesByQuery"]["nodes"])


def install():
    provision("install.groovy")
    edit, live = state()
    if "/sites/luxe/contents/quality-lab-terms" not in edit or not live:
        sys.exit("install.groovy stopped before its end. Read the GroovyPatcher line of jahia.log.")
    print("The dataset is installed: the pages under /sites/luxe/home/quality-lab, published, and the terms.")


def remove():
    provision("remove.groovy")
    edit, live = state()
    if edit or live:
        sys.exit("remove.groovy left %s. Read the GroovyPatcher line of jahia.log." % sorted(edit))
    print("The dataset is removed, in edit and in live.")


def graphql(query, variables=None):
    url, auth = settings()
    headers = dict(auth, **{"Content-Type": "application/json", "Origin": url})
    data = json.dumps({"query": query, "variables": variables or {}}).encode()
    request = urllib.request.Request(url + "/modules/graphql", data=data, method="POST", headers=headers)
    answer = json.loads(urllib.request.urlopen(request, timeout=300).read().decode())
    if answer.get("errors"):
        raise RuntimeError(json.dumps(answer["errors"])[:2000])
    return answer["data"]


CHECK_PAGE = """query($path: String!, $language: String!) { contentGovernance { checkPage(path: $path, language: $language) {
  results { ruleId passed findings { ruleId severity text } } } } }"""
SCAN_PAGE = """query($path: String!, $language: String!) { complianceScan { scanPage(path: $path, language: $language) {
  status issues { ruleId term matchedText } } } }"""


def findings(path, language):
    """The ids of the rules that fail on the page, from the page check and from the compliance scan."""
    failed = set()
    page = graphql(CHECK_PAGE, {"path": path, "language": language})["contentGovernance"]["checkPage"]
    for result in page["results"]:
        if not result["passed"]:
            failed.add(result["ruleId"])
    scan = graphql(SCAN_PAGE, {"path": path, "language": language})["complianceScan"]["scanPage"]
    for issue in scan["issues"] or []:
        failed.add(issue["ruleId"])
    return failed


def check():
    with open(os.path.join(HERE, "expected.json"), encoding="utf-8") as source:
        expected = json.load(source)
    template = set(expected["template"])
    missing_any = False
    for page in expected["pages"]:
        for language in page["languages"]:
            failed = findings(page["path"], language)
            missing = sorted(set(page["findings"].get(language, page["findings"].get("*", []))) - failed)
            extra = sorted(failed - set(page["findings"].get(language, page["findings"].get("*", []))) - template)
            status = "MISSING " + ", ".join(missing) if missing else "ok"
            missing_any = missing_any or bool(missing)
            print("%-48s %s  %s" % (page["path"].split("/home/")[-1], language, status))
            if extra:
                print("%-48s     also: %s" % ("", ", ".join(extra)))
    sys.exit(1 if missing_any else 0)


def main():
    command = sys.argv[1] if len(sys.argv) > 1 else ""
    if command in ("install", "reset"):
        install()
    elif command == "remove":
        remove()
    elif command == "check":
        check()
    else:
        sys.exit(__doc__)


if __name__ == "__main__":
    main()
