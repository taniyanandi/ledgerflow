FROM python:3.11-slim

WORKDIR /service
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app

# Train the model at build time so the image ships with a ready model artifact.
# (In production you'd pull a versioned model from a registry instead.)
RUN python -m app.train

EXPOSE 8000
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
