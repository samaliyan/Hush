# APK signing key

`hush.p12` is the app's signing key. The first GitHub build creates it from the repository secret
`SIGNING_PASSWORD` and commits it here. It is encrypted (PKCS#12, AES-256) and useless without that
password, which is **not** in this repository.

* Every published APK must be signed with this key, otherwise Android refuses to install it as an
  update. The release workflow fails if the secret is missing and checks the signature of the built
  APK against `cert-sha256.txt`.
* Never replace or delete this file: installed copies of the app could no longer be updated.
* Local builds without the secret are signed with the Android debug key (fine for testing only).
