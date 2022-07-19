.PHONY: all clean build start test stop

all: clean build start-silent test stop

clean:
	./gradlew clean

build:
	./gradlew build

start:
	docker-compose up --build

start-silent:
	docker-compose up --build -d

start-idea:
	docker-compose up -d echo-server

stop:
	docker-compose down --remove-orphans

test:
	node test/test.js