.PHONY: infra-up infra-down run test package

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
