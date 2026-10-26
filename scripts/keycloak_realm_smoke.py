"""Recette d'un realm Myrmacta sur un vrai Keycloak (TODO §104, D-BEAUTH-KC-SMOKE).

Appelé par scripts/keycloak-realm-smoke.sh, qui démarre Keycloak et y applique les realms. Ce module joue le
navigateur (formulaires HTML, redirections, cookies) avec la seule bibliothèque standard, puis échange le code
d'autorisation comme le BFF et vérifie l'ID token reçu.

Usage : python3 -I keycloak_realm_smoke.py <url_keycloak> <scenario> [options]
"""

import base64
import hashlib
import hmac
import html
import http.cookiejar
import json
import re
import struct
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

APP = "http://localhost:8080"


class Stop(urllib.request.HTTPRedirectHandler):
    """Ne suit pas une redirection vers l'application : on veut le code, pas l'appel au BFF."""

    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if newurl.startswith(APP + "/"):
            raise Arrived(newurl)
        return super().redirect_request(req, fp, code, msg, headers, newurl)


class Arrived(Exception):
    def __init__(self, url):
        super().__init__(url)
        self.url = url


class LocalhostPolicy(http.cookiejar.DefaultCookiePolicy):
    """Keycloak pose des cookies « Secure » ; un navigateur les renvoie à http://localhost, pas cookiejar."""

    def return_ok_secure(self, cookie, request):
        return True


class Browser:
    def __init__(self):
        self.jar = http.cookiejar.CookieJar(LocalhostPolicy())
        self.opener = urllib.request.build_opener(
            urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(self.jar), Stop())

    def get(self, url):
        return self._open(urllib.request.Request(url))

    def post(self, url, fields):
        data = urllib.parse.urlencode(fields).encode()
        return self._open(urllib.request.Request(url, data=data))

    def _open(self, request):
        try:
            with self.opener.open(request, timeout=30) as response:
                return ("page", response.geturl(), response.read().decode("utf-8", "replace"))
        except Arrived as arrived:
            return ("arrived", arrived.url, "")
        except urllib.error.HTTPError as error:
            return ("error", error.geturl(), error.read().decode("utf-8", "replace"))


def form(page):
    """Action et champs cachés du premier formulaire de la page Keycloak."""
    match = re.search(r'<form[^>]*action="([^"]+)"', page)
    if not match:
        return None, {}
    hidden = dict(re.findall(r'<input[^>]*type="hidden"[^>]*name="([^"]+)"[^>]*value="([^"]*)"', page))
    return html.unescape(match.group(1)), hidden


def claims(jwt):
    payload = jwt.split(".")[1]
    return json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))


def post_json(url, fields, headers=None):
    request = urllib.request.Request(url, data=urllib.parse.urlencode(fields).encode(), headers=headers or {})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    with opener.open(request, timeout=30) as response:
        return json.loads(response.read())


def totp(secret, at=None):
    """Code TOTP de Keycloak par défaut (HmacSHA1, 6 chiffres, 30 s) ; la clé est le secret brut du formulaire."""
    counter = int((at or time.time()) // 30)
    digest = hmac.new(secret.encode(), struct.pack(">Q", counter), hashlib.sha1).digest()
    offset = digest[-1] & 0x0F
    return "%06d" % ((struct.unpack(">I", digest[offset:offset + 4])[0] & 0x7FFFFFFF) % 1_000_000)


def login(kc, realm, client_id, secret, username, password, idp=None, otp_secret=None, new_password=None,
          redirect=APP + "/login/oauth2/code/oidc", link_password=None, link_user=None):
    """Connexion navigateur au client BFF, directe ou par un fournisseur ; renvoie (résultat, détail, secret OTP).

    {@code link_user} et {@code link_password} : compte Keycloak existant auquel relier le fournisseur (A3) ; sans eux,
    la proposition de rattachement est refusée.
    """
    browser = Browser()
    enrolled = None
    query = {"client_id": client_id, "redirect_uri": redirect, "response_type": "code", "scope": "openid",
             "state": "smoke", "nonce": "smoke"}
    if idp:
        query["kc_idp_hint"] = idp
    kind, url, page = browser.get(f"{kc}/realms/{realm}/protocol/openid-connect/auth?" + urllib.parse.urlencode(query))
    for _ in range(10):
        if kind == "arrived":
            code = urllib.parse.parse_qs(urllib.parse.urlparse(url).query).get("code", [None])[0]
            if not code:
                return "refused", url, enrolled
            tokens = post_json(f"{kc}/realms/{realm}/protocol/openid-connect/token", {
                "grant_type": "authorization_code", "code": code, "redirect_uri": redirect,
                "client_id": client_id, "client_secret": secret})
            return "logged-in", claims(tokens["id_token"]), enrolled
        if kind == "error":
            return "refused", page, enrolled
        action, fields = form(page)
        if action is None:
            return "refused", page, enrolled
        at_provider = "/realms/fas-simulateur/" in action
        if 'value="linkAccount"' in page:
            if link_password is None:
                return "link-proposed", page, enrolled
            fields.update(submitAction="linkAccount")
        elif link_password is not None and not at_provider and 'name="password"' in page:
            fields.update(password=link_password)
            if 'name="username"' in page and link_user:
                fields.update(username=link_user)
        elif 'name="password-new"' in page:
            if new_password is None:
                return "password-change-required", page, enrolled
            fields.update({"password-new": new_password, "password-confirm": new_password})
        elif 'name="totpSecret"' in page:
            enrolled = fields["totpSecret"]
            fields.update(totp=totp(enrolled), userLabel="recette")
        elif 'name="password"' in page and 'name="username"' in page:
            fields.update(username=username, password=password)
        elif 'name="username"' in page:
            fields.update(username=username)
        elif 'name="password"' in page:
            fields.update(password=password)
        elif 'name="otp"' in page:
            if otp_secret is None:
                return "otp-required", page, enrolled
            # Keycloak refuse un code déjà utilisé : on attend la fenêtre suivante celle de l'enrôlement.
            time.sleep(31 - time.time() % 30)
            fields.update(otp=totp(otp_secret))
        else:
            return "refused", page, enrolled
        kind, url, page = browser.post(action, fields)
    return "refused", "trop d'étapes", enrolled


def main(argv):
    if len(argv) < 3:
        print(__doc__, file=sys.stderr)
        return 2
    kc, scenario = argv[1], argv[2]
    options = dict(arg.split("=", 1) for arg in argv[3:])
    result, detail, enrolled = login(kc, options["realm"], options.get("client", "mon-app"), options["secret"],
                                     options["user"], options["password"], options.get("idp"),
                                     options.get("otp_secret"), options.get("new_password"),
                                     options.get("redirect", APP + "/login/oauth2/code/oidc"),
                                     options.get("link_password"), options.get("link_user"))
    print(json.dumps({"scenario": scenario, "result": result,
                      "claims": detail if isinstance(detail, dict) else None,
                      "page": None if isinstance(detail, dict) else _summary(detail),
                      "otp_secret": enrolled}, ensure_ascii=False))
    return 0


def _summary(page):
    title = re.search(r"<title>(.*?)</title>", page or "", re.S)
    error = re.search(r'class="[^"]*(?:alert|kc-feedback-text|instruction)[^"]*"[^>]*>(.*?)<', page or "", re.S)
    return " | ".join(html.unescape(x.group(1)).strip() for x in (title, error) if x)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
