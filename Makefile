.PHONY: all clean build start test stop

all: clean build start-silent test stop

clean:
	./gradlew clean

build:
	./gradlew build

start:
	docker compose up --build echo-server redis database cdnstub oidc-stub frontend-with-redis-persistence

start-silent:
	docker compose up --build -d echo-server redis database cdnstub oidc-stub frontend-with-redis-persistence

start-idea:
	docker compose up -d echo-server redis database cdnstub

stop:
	docker compose down --remove-orphans

test:
	node test/test.js

integrationtest: integrationtest-setup integrationtest-test-redis integrationtest-test-postgresql integrationtest-teardown

integrationtest-setup:
	# Start supporting services
	docker compose up --build -d echo-server redis database cdnstub oidc-stub

integrationtest-test-redis:
	# Start and test frontend app with redis persistence layer
	docker compose up --build -d frontend-with-redis-persistence
	node test/test.js
	# Stop redis service to verify the app works if redis is down
	docker compose stop redis
	node test/test.js
	# Test complete for frontend app with redis persistence layer, printing logs and stopping the service
	docker compose logs frontend-with-redis-persistence
	docker compose stop frontend-with-redis-persistence

integrationtest-test-postgresql:
	# Start and test frontend app with postgresql persistence layer
	docker compose up --build -d frontend-with-postgresql-persistence
	node test/test.js
	# Stop database to verify the app works if it is down
	docker compose stop database
	node test/test.js
	# Test complete for frontend app with redis persistence layer, printing logs and stopping the service
	docker compose logs frontend-with-postgresql-persistence
	docker compose stop frontend-with-postgresql-persistence

integrationtest-teardown:
	# All tests complete, stopping all services
	docker compose down --remove-orphans