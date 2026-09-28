# OpenTreeMap modern-v4.1

This archive is the Ubuntu/Docker smoke-test bridge for the OpenTreeMap modernization.

Start here: [`MODERN_V4_1_NOTES.md`](MODERN_V4_1_NOTES.md).

```bash
chmod +x modern-v4.1.sh
./modern-v4.1.sh doctor
./modern-v4.1.sh build
```

If the image builds successfully:

```bash
./modern-v4.1.sh up
./modern-v4.1.sh logs
```

This is a migration/development bridge, not a public production deployment.
