"""
OpenTelemetry 설정 — Tempo OTLP exporter.

backend (Spring) 가 보낸 traceparent 헤더를 받아 같은 trace_id 로 span 추가.
"""
import os
from opentelemetry import trace
from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
from opentelemetry.instrumentation.httpx import HTTPXClientInstrumentor
from opentelemetry.sdk.resources import Resource
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

OTLP_ENDPOINT = os.environ.get("OTLP_ENDPOINT", "http://tempo.monitoring.svc:4318/v1/traces")
SERVICE_NAME = os.environ.get("OTEL_SERVICE_NAME", "lemuel-xr-ai")
TRACING_ENABLED = os.environ.get("TRACING_ENABLED", "true").lower() == "true"


def setup(app):
    """FastAPI app 에 OTel 설정 + auto-instrumentation."""
    if not TRACING_ENABLED:
        return

    resource = Resource.create({
        "service.name": SERVICE_NAME,
        "service.version": os.environ.get("APP_VERSION", "0.1.0"),
    })
    provider = TracerProvider(resource=resource)
    exporter = OTLPSpanExporter(endpoint=OTLP_ENDPOINT, timeout=10)
    provider.add_span_processor(BatchSpanProcessor(exporter))
    trace.set_tracer_provider(provider)

    FastAPIInstrumentor.instrument_app(app)
    HTTPXClientInstrumentor().instrument()
