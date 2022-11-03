FROM python:3.9.13-bullseye

WORKDIR /app
COPY requirements.txt /app
RUN pip install --no-cache-dir --upgrade -r /app/requirements.txt
COPY app /app/app

RUN addgroup --group python-demo && adduser --system --group python-demo
USER python-demo
EXPOSE 8000

ENTRYPOINT ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
