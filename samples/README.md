# Samples

No assignment or third-party signed PDF is committed because redistribution permission is unknown. Keep sensitive comparison documents outside Git and upload them only to the local application.

To compare an authorized sample locally:

1. Place it temporarily in this directory.
2. Confirm `VALIDATION_MODE=dss`.
3. Upload it through the UI or use `curl -F "document=@samples/your-file.pdf" http://localhost:8080/api/v1/validations`.
4. Remove it after testing.

Backend tests generate small unsigned and signed PAdES PDFs at runtime and do not commit private keys or binary documents.

Fixture mode remains available for UI development. Its deterministic filename behavior applies only when `VALIDATION_MODE=fixture`.
