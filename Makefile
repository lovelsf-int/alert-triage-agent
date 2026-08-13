.PHONY: infra-up infra-down run test package demo search

infra-up:
	docker compose up -d postgres

infra-down:
	docker compose down

run:
	mvn spring-boot:run

test:
	mvn test

package:
	mvn clean package

demo:
	bash scripts/demo.sh

search:
	bash scripts/search-knowledge.sh
