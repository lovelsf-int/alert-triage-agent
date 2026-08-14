.PHONY: install lint test run infra-up infra-down demo

install:
	python -m pip install -e ".[dev]"

lint:
	ruff check .

test:
	pytest -q

run:
	uvicorn alert_triage_langgraph.main:app --reload --port 8081

infra-up:
	docker compose up -d postgres

infra-down:
	docker compose down

demo:
	bash scripts/demo.sh
