"""Verify signed author assets in a built official APK. Requires cryptography."""
import base64
import hashlib
import json
import sys
import zipfile

from cryptography import x509
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding

OFFICIAL_CERT = "f7c17a0bcbcdfa96b610c13fc2e5d4ece82cb71d5ff454e80834c21ca1b0aa92"


def verify(path):
    with zipfile.ZipFile(path) as apk:
        rows = apk.read("assets/verified-author/identity.txt").decode("utf-8").splitlines()
        assert len(rows) == 3
        payload, signature, cert_bytes = [base64.b64decode(row, validate=True) for row in rows]
        avatar = apk.read("assets/verified-author/avatar.jpg")
        assert hashlib.sha256(cert_bytes).hexdigest() == OFFICIAL_CERT
        key = x509.load_der_x509_certificate(cert_bytes).public_key()
        key.verify(signature, payload, padding.PKCS1v15(), hashes.SHA256())
        fields = payload.decode("utf-8").split("\n")
        assert len(fields) == 4 and fields[0] == "author-v1" and fields[-1] == ""
        assert 1 <= len(fields[1]) <= 80
        assert hashlib.sha256(avatar).hexdigest() == fields[2]
        assert not any(name.lower().endswith((".p12", ".jks", ".keystore", "author-name.txt")) for name in apk.namelist())
    return {"authorSignature": "verified", "avatarDigest": "verified", "officialCertificate": OFFICIAL_CERT, "privateSigningFilesPackaged": False}


if __name__ == "__main__":
    print(json.dumps(verify(sys.argv[1]), ensure_ascii=False, indent=2))
